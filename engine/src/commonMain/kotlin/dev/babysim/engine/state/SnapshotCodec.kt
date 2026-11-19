package dev.babysim.engine.state

import dev.babysim.engine.EngineInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Thrown when a snapshot cannot be decoded. Callers recover per docs/05 §4 (never crash on load). */
class SnapshotDecodeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Encodes and decodes [BabyState] snapshots as JSON (docs/05 `baby_snapshots.payload`).
 * Stateless: the [Json] instance is immutable configuration.
 */
object SnapshotCodec {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    fun encode(state: BabyState): String = json.encodeToString(BabyState.serializer(), state)

    fun decode(text: String): BabyState {
        val version =
            decoding("Snapshot has no readable meta.engineSchemaVersion") {
                json
                    .parseToJsonElement(text)
                    .jsonObject
                    .getValue("meta")
                    .jsonObject
                    .getValue("engineSchemaVersion")
                    .jsonPrimitive.int
            }
        if (version > EngineInfo.ENGINE_SCHEMA_VERSION) {
            throw SnapshotDecodeException(
                "Snapshot schema v$version is newer than this engine (v${EngineInfo.ENGINE_SCHEMA_VERSION})",
            )
        }
        // Upcasters for older versions are inserted here when ENGINE_SCHEMA_VERSION is first bumped.
        return decoding("Snapshot payload is invalid") { json.decodeFromString(BabyState.serializer(), text) }
    }

    /**
     * Converts decoding failures into [SnapshotDecodeException]. kotlinx's SerializationException and `require`
     * failures are both IllegalArgumentExceptions; a missing JSON key is a NoSuchElementException.
     */
    private inline fun <T> decoding(
        message: String,
        block: () -> T,
    ): T =
        runCatching(block).getOrElse { e ->
            if (e is IllegalArgumentException || e is NoSuchElementException) {
                throw SnapshotDecodeException("$message: ${e.message}", e)
            }
            throw e
        }
}
