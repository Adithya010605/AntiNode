package dev.antinode.core.formats

import dev.antinode.core.model.Chain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.Json

/** Antinode's own preset file: the whole chain, versioned. */
object NativeFormat {
    const val VERSION = 1

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class File(
        /** Format version; presets exported before the rename used the key "circuitEq". */
        @JsonNames("circuitEq") val antinode: Int = VERSION,
        val name: String,
        val chain: Chain,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun write(name: String, chain: Chain): String = json.encodeToString(File.serializer(), File(name = name, chain = chain))

    fun read(text: String): Imported {
        val file = runCatching { json.decodeFromString(File.serializer(), text) }
            .getOrElse { throw ImportException("Damaged Antinode preset: ${it.message}") }
        if (file.antinode > VERSION) throw ImportException("Preset is from a newer app version")
        return Imported(file.chain, file.name, "Antinode")
    }

    /** Compact form used for storing chains in the database. */
    private val storage = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    fun encodeChain(chain: Chain): String = storage.encodeToString(Chain.serializer(), chain)
    fun decodeChain(text: String): Chain = runCatching { storage.decodeFromString(Chain.serializer(), text) }.getOrDefault(Chain())
}
