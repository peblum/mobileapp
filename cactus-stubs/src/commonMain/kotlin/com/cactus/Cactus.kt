package com.cactus

fun interface CactusTokenCallback {
    fun onToken(token: String, tokenId: Int)
}

fun interface CactusLogCallback {
    fun onLog(level: Int, component: String, message: String)
}

fun isCactusSupported(): Boolean = false

fun cactusSetBackend(backend: String): Int = -1
fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long =
    error("Cactus is disabled in the F-Droid build")

fun cactusDestroy(handle: Long) = Unit
fun cactusReset(handle: Long) = Unit
fun cactusStop(handle: Long) = Unit
fun cactusComplete(handle: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray? = null): String =
    error(cactusGetLastError())
fun cactusPrefill(handle: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, pcmData: ByteArray? = null): String =
    error(cactusGetLastError())
fun cactusTokenize(handle: Long, text: String): IntArray = error(cactusGetLastError())
fun cactusScoreWindow(handle: Long, tokens: IntArray, start: Long, end: Long, context: Long): String =
    error(cactusGetLastError())
fun cactusTranscribe(handle: Long, audioPath: String?, prompt: String, optionsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String =
    error(cactusGetLastError())
fun cactusStreamTranscribeStart(handle: Long, optionsJson: String?): Long = error(cactusGetLastError())
fun cactusStreamTranscribeProcess(stream: Long, pcmData: ByteArray?): String = error(cactusGetLastError())
fun cactusStreamTranscribeStop(stream: Long): String = error(cactusGetLastError())
fun cactusEmbed(handle: Long, text: String, normalize: Boolean): FloatArray = error(cactusGetLastError())
fun cactusImageEmbed(handle: Long, imagePath: String): FloatArray = error(cactusGetLastError())
fun cactusAudioEmbed(handle: Long, audioPath: String): FloatArray = error(cactusGetLastError())
fun cactusRagQuery(handle: Long, query: String, topK: Long): String = error(cactusGetLastError())
fun cactusIndexInit(indexDir: String, embeddingDim: Long): Long = error(cactusGetLastError())
fun cactusIndexAdd(handle: Long, ids: IntArray, documents: Array<String>, metadatas: Array<String>?, embeddings: Array<FloatArray>, embeddingDim: Long): Int =
    error(cactusGetLastError())
fun cactusIndexDelete(handle: Long, ids: IntArray): Int = error(cactusGetLastError())
fun cactusIndexGet(handle: Long, ids: IntArray): String = error(cactusGetLastError())
fun cactusIndexQuery(handle: Long, embedding: FloatArray, optionsJson: String?): String = error(cactusGetLastError())
fun cactusIndexCompact(handle: Long): Int = error(cactusGetLastError())
fun cactusIndexDestroy(handle: Long) = Unit
fun cactusGetLastError(): String = "Cactus is disabled in the F-Droid build"
fun cactusLogSetLevel(level: Int) = Unit
fun cactusLogSetCallback(callback: CactusLogCallback?) = Unit
fun cactusSetTelemetryEnvironment(
    framework: String?,
    cacheLocation: String?,
    version: String?,
) = Unit
fun cactusSetAppId(appId: String) = Unit
fun cactusTelemetryFlush() = Unit
fun cactusTelemetryShutdown() = Unit
