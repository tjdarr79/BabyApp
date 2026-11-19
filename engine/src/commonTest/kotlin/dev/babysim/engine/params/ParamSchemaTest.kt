package dev.babysim.engine.params

import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.TemperamentId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParamSchemaTest {
    private val bundled = BundledParamFiles.files

    @Test
    fun bundledParametersLoad() {
        val params = ParamLoader.bundled()
        assertEquals(AgeBandId.entries.toSet(), params.ages.keys)
        assertEquals(TemperamentId.entries.toSet(), params.temperaments.keys)
        assertEquals(1, params.version)
    }

    @Test
    fun everyBundledLeafHasSourceEvidenceAndReviewStatus() {
        for ((path, text) in bundled) {
            val values =
                Json
                    .parseToJsonElement(text)
                    .jsonObject
                    .getValue("values")
                    .jsonObject
            assertTrue(values.isNotEmpty(), "$path has no values")
            for ((field, leaf) in values) {
                val obj = leaf.jsonObject
                for (key in listOf("value", "source", "evidence", "reviewStatus")) {
                    assertTrue(key in obj, "$path: $field missing $key")
                }
                assertTrue((obj.getValue("source") as JsonPrimitive).content.isNotBlank(), "$path: $field blank source")
            }
        }
    }

    @Test
    fun designAssumptionsAreLabelledInTheSourceText() {
        for ((path, text) in bundled) {
            val values =
                Json
                    .parseToJsonElement(text)
                    .jsonObject
                    .getValue("values")
                    .jsonObject
            for ((field, leaf) in values) {
                val obj = leaf.jsonObject
                val evidence = (obj.getValue("evidence") as JsonPrimitive).content
                val source = (obj.getValue("source") as JsonPrimitive).content
                if (evidence == Evidence.DESIGN_ASSUMPTION.name) {
                    assertTrue(source.startsWith("DESIGN_ASSUMPTION"), "$path: $field must say DESIGN_ASSUMPTION")
                }
            }
        }
    }

    @Test
    fun missingReviewStatusFailsLoading() {
        assertProblem("is missing \"reviewStatus\"") { removeLeafKey(it, "feedsPer24h", "reviewStatus") }
    }

    @Test
    fun missingSourceFailsLoading() {
        assertProblem("is missing \"source\"") { removeLeafKey(it, "feedsPer24h", "source") }
    }

    @Test
    fun blankSourceFailsLoading() {
        assertProblem("blank source") { setLeafKey(it, "feedsPer24h", "source", JsonPrimitive("  ")) }
    }

    @Test
    fun researchedValueWithoutUrlFailsLoading() {
        assertProblem("no sourceUrls") { removeLeafKey(it, "totalSleepHours24", "sourceUrls") }
    }

    @Test
    fun invalidRangeFailsLoading() {
        assertProblem("min <= typical <= max") {
            setLeafKey(
                it,
                "feedIntervalMinutes",
                "value",
                Json.parseToJsonElement("""{"min": 240.0, "typical": 210.0, "max": 180.0}"""),
            )
        }
    }

    @Test
    fun probabilityOutsideUnitIntervalFailsLoading() {
        assertProblem(
            "feedAcceptMin must be within 0..1",
        ) { setLeafKey(it, "feedAcceptMin", "value", JsonPrimitive(1.5)) }
    }

    @Test
    fun unknownFieldFailsLoading() {
        assertProblem("feedIntervalMinutez") {
            val values = it.getValue("values").jsonObject
            JsonObject(
                it + ("values" to JsonObject(values + ("feedIntervalMinutez" to values.getValue("feedsPer24h")))),
            )
        }
    }

    @Test
    fun missingAgeBandFailsLoading() {
        val e = assertFailsWith<ParamValidationException> { ParamLoader.load(bundled - "age/M7_9.json") }
        assertTrue(e.problems.any { "missing age profile: age/M7_9.json" in it }, e.message)
    }

    @Test
    fun misnamedFileFailsLoading() {
        val files =
            bundled - "temperament/AVERAGE.json" +
                ("temperament/average.json" to bundled.getValue("temperament/AVERAGE.json"))
        val e = assertFailsWith<ParamValidationException> { ParamLoader.load(files) }
        assertTrue(e.problems.any { "must be named temperament/AVERAGE.json" in it }, e.message)
    }

    @Test
    fun mixedParamSetVersionsFailLoading() {
        val bumped =
            Json.parseToJsonElement(bundled.getValue("age/M4_6.json")).jsonObject.let {
                JsonObject(it + ("paramSetVersion" to JsonPrimitive(2)))
            }
        val e =
            assertFailsWith<ParamValidationException> {
                ParamLoader.load(
                    bundled + ("age/M4_6.json" to bumped.toString()),
                )
            }
        assertTrue(e.problems.any { "disagree on paramSetVersion" in it }, e.message)
    }

    /** Mutates `age/M2_3.json` and asserts loading fails with a problem containing [expected]. */
    private fun assertProblem(
        expected: String,
        mutate: (JsonObject) -> JsonObject,
    ) {
        val path = "age/M2_3.json"
        val mutated = mutate(Json.parseToJsonElement(bundled.getValue(path)).jsonObject)
        val e = assertFailsWith<ParamValidationException> { ParamLoader.load(bundled + (path to mutated.toString())) }
        assertTrue(e.problems.any { expected in it }, "expected a problem containing '$expected', got: ${e.message}")
    }

    private fun setLeafKey(
        root: JsonObject,
        field: String,
        key: String,
        value: JsonElement,
    ): JsonObject = updateLeaf(root, field) { JsonObject(it + (key to value)) }

    private fun removeLeafKey(
        root: JsonObject,
        field: String,
        key: String,
    ): JsonObject = updateLeaf(root, field) { JsonObject(it - key) }

    private fun updateLeaf(
        root: JsonObject,
        field: String,
        change: (JsonObject) -> JsonObject,
    ): JsonObject {
        val values = root.getValue("values").jsonObject
        val leaf = values.getValue(field).jsonObject
        return JsonObject(root + ("values" to JsonObject(values + (field to change(leaf)))))
    }
}
