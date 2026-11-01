package com.bombless.agenttools

/** A model-independent JavaScript tool call extracted from model output. */
data class ToolCall(
    val name: String,
    val code: String,
    val rawText: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Shared extraction helpers. Host apps retain their model-specific protocol parsers and
 * normalize their results to [ToolCall], so adding a model format does not fork execution.
 */
object ToolCallParser {
    private val toolBlock = Regex(
        """<tool_call(?::([A-Za-z0-9_-]+))?>([\\s\\S]*?)</tool_call(?::\\1)?>""",
        RegexOption.IGNORE_CASE
    )
    private val runBlock = Regex("""<run_javascript>([\\s\\S]*?)</run_javascript>""", RegexOption.IGNORE_CASE)
    private val fence = Regex("""^\\s*```(?:javascript|js)?\\s*([\\s\\S]*?)\\s*```\\s*$""", RegexOption.IGNORE_CASE)

    fun findNext(text: String, fromIndex: Int = 0): ToolCall? {
        if (fromIndex < 0 || fromIndex >= text.length) return null
        val tail = text.substring(fromIndex)
        val match = sequenceOf(toolBlock.find(tail), runBlock.find(tail))
            .filterNotNull().minByOrNull { it.range.first } ?: return null
        val isTool = match.groups.size > 2
        val body = if (isTool) match.groupValues[2] else match.groupValues[1]
        val name = if (isTool) match.groupValues[1].ifBlank { "run_javascript" } else "run_javascript"
        val code = extractCode(body)
        return ToolCall(name, code, match.value, fromIndex + match.range.first, fromIndex + match.range.last + 1)
    }

    fun extractCode(content: String): String {
        var value = content.trim()
        val json = runCatching { org.json.JSONObject(value) }.getOrNull()
        if (json != null) {
            val args = json.optJSONObject("arguments") ?: json.optJSONObject("parameters") ?: json
            value = args.optString("code", args.optString("script", args.optString("prompt", value)))
        }
        val parameter = Regex(
            """<(?:parameter|param)(?:\\s*=\\s*([\\w-]+)|\\s+name=["']?([\\w-]+)["']?)[^>]*>([\\s\\S]*?)</(?:parameter|param)>""",
            RegexOption.IGNORE_CASE
        ).find(value)
        if (parameter != null) value = parameter.groupValues[3]
        val function = Regex("""<function(?:\\s*=[^>]+|\\s+[^>]+)?>([\\s\\S]*?)</function>""", RegexOption.IGNORE_CASE).find(value)
        if (function != null) value = function.groupValues[1]
        val codeTag = Regex("""<(?:code|script|javascript)>([\\s\\S]*?)</(?:code|script|javascript)>""", RegexOption.IGNORE_CASE).find(value)
        if (codeTag != null) value = codeTag.groupValues[1]
        val codeFence = fence.find(value)
        if (codeFence != null) value = codeFence.groupValues[1]
        return value.trim()
            .replace(Regex("""^<!\\[CDATA\\["""), "")
            .replace(Regex("""\\]\\]>$"""), "")
            .trim()
    }
}
