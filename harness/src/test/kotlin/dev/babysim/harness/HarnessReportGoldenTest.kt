package dev.babysim.harness

import dev.babysim.engine.caregiver.AttentiveCaregiver
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.params.ParamLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The harness report is deterministic: the same parameters and engine give the same report, byte for byte.
 * Any behavior change shows up here as a diff to review. After a deliberate change, regenerate with
 * `./gradlew :harness:test -PupdateGolden=true` and commit the new golden file with the change.
 */
class HarnessReportGoldenTest {
    private val options =
        Options(
            bands = listOf(AgeBandId.M2_3),
            temperaments = listOf(TemperamentId.AVERAGE),
            days = 2,
            seeds = 3,
            policy = AttentiveCaregiver,
            stepMinutes = 2,
            out = "unused",
        )

    @Test
    fun reportMatchesGoldenFile() {
        val (markdown, csv) = generate(ParamLoader.bundled(), options)
        val dir = File(System.getProperty("harness.goldenDir") ?: fail("harness.goldenDir not set"))
        val mdFile = File(dir, "m2_3-average-attentive-2d.md")
        val csvFile = File(dir, "m2_3-average-attentive-2d.csv")
        if (System.getProperty("harness.updateGolden") == "true") {
            dir.mkdirs()
            mdFile.writeText(markdown)
            csvFile.writeText(csv)
        }
        assertTrue(mdFile.exists(), "missing golden file; run ./gradlew :harness:test -PupdateGolden=true")
        assertEquals(mdFile.readText(), markdown, "report changed; review the diff, then regenerate the golden file")
        assertEquals(csvFile.readText(), csv)
    }

    @Test
    fun generationIsDeterministic() {
        assertEquals(generate(ParamLoader.bundled(), options), generate(ParamLoader.bundled(), options))
    }

    @Test
    fun optionsParseAndRejectUnknownValues() {
        val parsed = parseOptions(arrayOf("--band", "NEWBORN", "--days", "3", "--seeds", "4", "--policy", "random"))
        assertEquals(listOf(AgeBandId.NEWBORN), parsed?.bands)
        assertEquals(TemperamentId.entries, parsed?.temperaments)
        assertEquals("random", parsed?.policy?.name)
        assertEquals(null, parseOptions(arrayOf("--policy", "nobody")))
        assertEquals(null, parseOptions(arrayOf("--band", "TODDLER")))
    }
}
