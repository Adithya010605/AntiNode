package dev.circuiteq.core.formats

import dev.circuiteq.core.model.Chain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Circuit EQ's own preset file: the whole chain, versioned. */
object NativeFormat {
    const val VERSION = 1

    @Serializable
    data class File(val circuitEq: Int = VERSION, val name: String, val chain: Chain)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun write(name: String, chain: Chain): String = json.encodeToString(File.serializer(), File(name = name, chain = chain))

    fun read(text: String): Imported {
        val file = runCatching { json.decodeFromString(File.serializer(), text) }
            .getOrElse { throw ImportException("Damaged Circuit EQ preset: ${it.message}") }
        if (file.circuitEq > VERSION) throw ImportException("Preset is from a newer app version")
        return Imported(file.chain, file.name, "Circuit EQ")
    }

    /** Compact form used for storing chains in the database. */
    private val storage = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    fun encodeChain(chain: Chain): String = storage.encodeToString(Chain.serializer(), chain)
    fun decodeChain(text: String): Chain = runCatching { storage.decodeFromString(Chain.serializer(), text) }.getOrDefault(Chain())
}
