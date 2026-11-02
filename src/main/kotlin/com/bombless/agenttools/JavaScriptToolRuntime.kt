package com.bombless.agenttools

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.concurrent.atomic.AtomicInteger

/** Small, host-neutral Rhino sandbox shared by the Gemma and Whisper apps. */
class JavaScriptToolRuntime(
    private val timeoutMs: Long = 5_000,
    private val maxCodeChars: Int = 32_000,
    private val maxOutputChars: Int = 20_000
) {
    data class Result(val output: String, val success: Boolean)

    fun execute(source: String): Result {
        if (source.isBlank()) return Result("JavaScript 代码为空。", false)
        if (source.length > maxCodeChars) return Result("代码超过 ${maxCodeChars} 字符限制。", false)
        val output = StringBuilder()
        val failed = AtomicInteger()
        val started = System.nanoTime()
        fun log(value: String) {
            if (output.length < maxOutputChars) output.append(value.take(maxOutputChars - output.length)).append('\n')
        }
        val factory = object : ContextFactory() {
            override fun makeContext(): Context = super.makeContext().apply {
                optimizationLevel = -1
                languageVersion = Context.VERSION_ES6
                instructionObserverThreshold = 10_000
                setClassShutter { false }
            }
            override fun observeInstructionCount(cx: Context, instructionCount: Int) {
                if ((System.nanoTime() - started) / 1_000_000 > timeoutMs) {
                    throw IllegalStateException("JavaScript 执行超时（${timeoutMs}ms）")
                }
            }
        }
        return try {
            factory.call { cx ->
                val scope = cx.initSafeStandardObjects(null, true)
                val console = cx.newObject(scope)
                val logFn = object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        log(args.joinToString(" ") { Context.toString(it) })
                        return Undefined.instance
                    }
                }
                ScriptableObject.putProperty(console, "log", logFn)
                ScriptableObject.putProperty(console, "info", logFn)
                ScriptableObject.putProperty(console, "warn", logFn)
                ScriptableObject.putProperty(console, "error", logFn)
                ScriptableObject.putProperty(scope, "console", console)
                val assertFn = object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        if (args.isEmpty() || !Context.toBoolean(args[0])) {
                            throw IllegalStateException(if (args.size > 1) Context.toString(args[1]) else null ?: "Assertion failed")
                        }
                        return Undefined.instance
                    }
                }
                ScriptableObject.putProperty(scope, "assert", assertFn)
                val tools = cx.newObject(scope)
                ScriptableObject.putProperty(tools, "assert", assertFn)
                ScriptableObject.putProperty(tools, "now", object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any =
                        System.currentTimeMillis().toDouble()
                })
                ScriptableObject.putProperty(scope, "tools", tools)
                val result = cx.evaluateString(scope, source, "<android-agent-tool>", 1, null)
                if (result !== Undefined.instance) log("返回值: ${Context.toString(result)}")
            }
            Result(output.toString().ifBlank { "JavaScript 执行成功（无输出）。" }, failed.get() == 0)
        } catch (e: Exception) {
            log("执行错误: ${e.message ?: e.javaClass.simpleName}")
            Result(output.toString().ifBlank { "执行错误: ${e.message ?: e.javaClass.simpleName}" }, false)
        }
    }
}
