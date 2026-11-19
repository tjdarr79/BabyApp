package dev.babysim.engine.params

import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.TemperamentId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Where one parameter value comes from and whether the reviewer has signed it off. */
data class Provenance(
    val file: String,
    val field: String,
    val evidence: Evidence,
    val reviewStatus: ReviewStatus,
)

/** Thrown when parameter files are incomplete or invalid. Lists every problem found, not just the first. */
class ParamValidationException(
    val problems: List<String>,
) : Exception("Invalid parameter set:\n" + problems.joinToString("\n") { "- $it" })

/**
 * Loads and validates parameter files (docs/04 §6). Files are keyed by path relative to `engine/params/`,
 * e.g. `age/M2_3.json`, `temperament/AVERAGE.json`, `model.json`.
 */
object ParamLoader {
    private const val AGE_DIR = "age/"
    private const val TEMPERAMENT_DIR = "temperament/"
    private const val JSON_EXT = ".json"
    private const val MODEL_FILE = "model.json"

    private val requiredLeafKeys = listOf("value", "source", "evidence", "reviewStatus")
    private val probabilityFields = setOf("feedAcceptMin", "circadianStrength", "selfSettleProbNight", "catnapProb")

    private val json = Json { ignoreUnknownKeys = false }

    /** The parameter files compiled into the engine from `engine/params/`. */
    fun bundled(): ParamSet = load(BundledParamFiles.files)

    /** Provenance of every bundled value, for reports and the release check (docs/07 M9). */
    fun bundledProvenance(): List<Provenance> =
        BundledParamFiles.files.toSortedMap().flatMap { (path, text) ->
            val values =
                json
                    .parseToJsonElement(text)
                    .jsonObject
                    .getValue("values")
                    .jsonObject
            values.map { (field, leaf) ->
                val obj = leaf.jsonObject
                Provenance(
                    file = path,
                    field = field,
                    evidence = Evidence.valueOf((obj.getValue("evidence") as JsonPrimitive).content),
                    reviewStatus = ReviewStatus.valueOf((obj.getValue("reviewStatus") as JsonPrimitive).content),
                )
            }
        }

    fun load(files: Map<String, String>): ParamSet {
        val found = Found()
        for ((path, text) in files.toSortedMap()) {
            val element = parseOrNull(path, text, found.problems) ?: continue
            checkProvenance(path, element, found.problems)
            collect(path, element, found)
        }
        found.checkComplete()
        if (found.problems.isNotEmpty()) throw ParamValidationException(found.problems)
        return ParamSet(
            version = found.versions.single(),
            ages = found.ages,
            temperaments = found.temperaments,
            model = checkNotNull(found.model),
        )
    }

    /** What the files provided so far, plus every problem found. */
    private class Found {
        val problems = mutableListOf<String>()
        val ages = mutableMapOf<AgeBandId, AgeProfile>()
        val temperaments = mutableMapOf<TemperamentId, TemperamentProfile>()
        var model: ModelProfile? = null
        val versions = mutableSetOf<Int>()

        fun checkComplete() {
            AgeBandId.entries.filterNot { it in ages }.forEach {
                problems += "missing age profile: $AGE_DIR${it.name}$JSON_EXT"
            }
            TemperamentId.entries.filterNot { it in temperaments }.forEach {
                problems += "missing temperament profile: $TEMPERAMENT_DIR${it.name}$JSON_EXT"
            }
            if (model == null) problems += "missing model profile: $MODEL_FILE"
            if (versions.size > 1) problems += "files disagree on paramSetVersion: ${versions.sorted()}"
        }
    }

    private fun collect(
        path: String,
        element: JsonElement,
        found: Found,
    ) {
        val problems = found.problems
        when {
            path.startsWith(AGE_DIR) ->
                decode(path, problems) { json.decodeFromJsonElement(AgeProfileFile.serializer(), element) }
                    ?.let { file ->
                        expectFileName(path, AGE_DIR, file.ageBand.name, problems)
                        checkProbabilities(path, element, problems)
                        found.ages[file.ageBand] = file.values
                        found.versions += file.paramSetVersion
                    }

            path.startsWith(TEMPERAMENT_DIR) ->
                decode(path, problems) { json.decodeFromJsonElement(TemperamentProfileFile.serializer(), element) }
                    ?.let { file ->
                        expectFileName(path, TEMPERAMENT_DIR, file.temperament.name, problems)
                        found.temperaments[file.temperament] = file.values
                        found.versions += file.paramSetVersion
                    }

            path == MODEL_FILE ->
                decode(path, problems) { json.decodeFromJsonElement(ModelProfileFile.serializer(), element) }
                    ?.let { file ->
                        found.model = file.values
                        found.versions += file.paramSetVersion
                    }

            else -> problems += "$path: not $MODEL_FILE and not under $AGE_DIR or $TEMPERAMENT_DIR"
        }
    }

    private fun parseOrNull(
        path: String,
        text: String,
        problems: MutableList<String>,
    ): JsonElement? =
        try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            problems += "$path: not valid JSON (${e.message})"
            null
        }

    private fun <T> decode(
        path: String,
        problems: MutableList<String>,
        block: () -> T,
    ): T? =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            // Also covers SerializationException (a subclass) and `require` failures in DoubleRange.
            problems += "$path: ${e.message}"
            null
        }

    /**
     * Every entry under `values` must carry provenance: [requiredLeafKeys] present, non-blank `source`,
     * and at least one http(s) URL unless the evidence is DESIGN_ASSUMPTION. Checked on the raw JSON so the
     * rule holds for any field added to the profile classes later.
     */
    private fun checkProvenance(
        path: String,
        root: JsonElement,
        problems: MutableList<String>,
    ) {
        val values = (root as? JsonObject)?.get("values") as? JsonObject
        if (values == null) {
            problems += "$path: missing \"values\" object"
            return
        }
        for ((field, leaf) in values) {
            val obj = leaf as? JsonObject
            if (obj == null) {
                problems += "$path: $field must be an object with provenance"
            } else {
                checkLeafProvenance("$path: $field", obj, problems)
            }
        }
    }

    private fun checkLeafProvenance(
        where: String,
        leaf: JsonObject,
        problems: MutableList<String>,
    ) {
        requiredLeafKeys.filterNot { it in leaf }.forEach { problems += "$where is missing \"$it\"" }
        val source = (leaf["source"] as? JsonPrimitive)?.content
        if (source != null && source.isBlank()) problems += "$where has a blank source"
        val urlsElement = leaf["sourceUrls"]
        if (urlsElement != null && urlsElement !is JsonArray) problems += "$where sourceUrls must be a list"
        val urls = (urlsElement as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        val evidence = (leaf["evidence"] as? JsonPrimitive)?.content
        if (evidence != null && evidence != Evidence.DESIGN_ASSUMPTION.name && urls.isEmpty()) {
            problems += "$where has evidence $evidence but no sourceUrls"
        }
        urls.filterNot { it.startsWith("https://") || it.startsWith("http://") }.forEach {
            problems += "$where has a non-http source URL: $it"
        }
    }

    private fun checkProbabilities(
        path: String,
        root: JsonElement,
        problems: MutableList<String>,
    ) {
        val values = root.jsonObject["values"]?.jsonObject ?: return
        for (field in probabilityFields) {
            val value = values[field]?.jsonObject?.get("value") ?: continue
            val numbers =
                when (value) {
                    is JsonPrimitive -> listOfNotNull(value.content.toDoubleOrNull())
                    is JsonObject -> value.values.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
                    else -> emptyList()
                }
            if (numbers.any { it < 0.0 || it > 1.0 }) problems += "$path: $field must be within 0..1"
        }
    }

    private fun expectFileName(
        path: String,
        dir: String,
        declared: String,
        problems: MutableList<String>,
    ) {
        val expected = "$dir$declared$JSON_EXT"
        if (path != expected) problems += "$path: declares $declared, so it must be named $expected"
    }
}
