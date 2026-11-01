# Android Agent Tools

Shared, Android-independent tool-calling primitives for bombless Android applications.

This repository is consumed as a Git submodule. Keep model-specific token/protocol handling in each host app; put protocol-neutral parsing utilities and sandboxed JavaScript execution here.

## Module

`shared-tools` is a Kotlin/JVM Gradle project. Host applications include it with `includeBuild` or as a Gradle project and provide Android-facing tool implementations through explicit bridges.
