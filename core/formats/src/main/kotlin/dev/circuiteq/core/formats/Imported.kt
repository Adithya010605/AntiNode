package dev.circuiteq.core.formats

import dev.circuiteq.core.model.Chain

/** Result of reading a preset file: the chain it describes plus anything that couldn't be carried over. */
data class Imported(
    val chain: Chain,
    val name: String?,
    val format: String,
    val warnings: List<String> = emptyList(),
)

class ImportException(message: String) : Exception(message)

object Importer {
    /** Detects the format from the content and parses it. */
    fun read(text: String, fileName: String?): Imported {
        val trimmed = text.trimStart()
        val base = fileName?.substringBeforeLast('.')
        return when {
            trimmed.startsWith("{") && trimmed.contains("\"circuitEq\"") -> NativeFormat.read(text)
            trimmed.startsWith("{") -> EasyEffectsFormat.read(text, base)
            ApoFormat.looksLikeApo(text) -> ApoFormat.read(text, base)
            else -> throw ImportException(
                "Not a supported preset. Use an AutoEQ / Equalizer APO .txt, an Easy Effects .json or a Circuit EQ preset.",
            )
        }
    }
}
