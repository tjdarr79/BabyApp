package dev.babysim.harness

import dev.babysim.engine.BehaviorEngine
import dev.babysim.engine.EngineInfo
import dev.babysim.engine.caregiver.CaregiverPolicy
import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.BabyConfig
import dev.babysim.engine.model.BabyId
import dev.babysim.engine.model.Slot
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.params.ParamLoader
import dev.babysim.engine.params.ParamSet
import java.io.File
import kotlin.system.exitProcess

/**
 * Simulation harness CLI (docs/04 §10). Writes `<out>/report-<policy>-<days>d.md` and `.csv`.
 *
 * `--band ALL|NEWBORN|M2_3|…  --temperament ALL|AVERAGE|…  --days 7  --seeds 20
 *  --policy attentive|delayed|absent|random  --step-minutes 2  --out reports`
 */
fun main(args: Array<String>) {
    val options = parseOptions(args) ?: exitProcess(2)
    val params = ParamLoader.bundled()
    val started = System.nanoTime()
    val (markdown, csv) = generate(params, options)
    val out = File(options.out).apply { mkdirs() }
    val base = "report-${options.policy.name}-${options.days}d"
    File(out, "$base.md").writeText(markdown)
    File(out, "$base.csv").writeText(csv)
    val seconds = (System.nanoTime() - started) / NANOS_PER_SECOND
    println("${banner()}: wrote ${File(out, "$base.md").path} and .csv in ${"%.1f".format(seconds)} s")
}

internal fun banner(): String = "baby-sim harness (engine schema v${EngineInfo.ENGINE_SCHEMA_VERSION})"

data class Options(
    val bands: List<AgeBandId>,
    val temperaments: List<TemperamentId>,
    val days: Int,
    val seeds: Int,
    val policy: CaregiverPolicy,
    val stepMinutes: Int,
    val out: String,
)

/** Runs every requested band × temperament over [Options.seeds] seeds and renders the report. Deterministic. */
fun generate(
    params: ParamSet,
    options: Options,
): Pair<String, String> {
    val simulation = Simulation(BehaviorEngine(params), params)
    val combos =
        options.bands.flatMap { band ->
            options.temperaments.map { temperament ->
                val runs =
                    (1..options.seeds).map { seed ->
                        val config =
                            BabyConfig(
                                BabyId("harness-$seed"),
                                Slot(0),
                                band,
                                temperament,
                                seed =
                                    seed * SEED_STRIDE + band.ordinal,
                            )
                        simulation.run(config, options.policy, options.days, options.stepMinutes)
                    }
                Report.evaluate(params, band, temperament, runs)
            }
        }
    val title = "Behavior report: ${options.policy.name} caregiver, ${options.days} days"
    val settings =
        "Engine schema v${EngineInfo.ENGINE_SCHEMA_VERSION}, parameter set v${params.version}. " +
            "${options.seeds} seeds per combination; the caregiver checks every ${options.stepMinutes} min. " +
            "Day = 07:00–19:00, night = 19:00–07:00. Day 1 = the first 24 h; " +
            "median, p10 and p90 are across seeds over all ${options.days} days."
    return Report.markdown(title, settings, combos, ParamLoader.bundledProvenance()) to
        Report.csv(options.policy.name, combos)
}

private const val SEED_STRIDE = 7919L

internal fun parseOptions(args: Array<String>): Options? {
    val map =
        args
            .toList()
            .chunked(2)
            .filter { it.size == 2 }
            .associate { (k, v) -> k.removePrefix("--") to v }
    return try {
        Options(
            bands =
                map["band"].let {
                    if (it == null ||
                        it == "ALL"
                    ) {
                        AgeBandId.entries
                    } else {
                        listOf(AgeBandId.valueOf(it))
                    }
                },
            temperaments =
                map["temperament"].let {
                    if (it == null ||
                        it == "ALL"
                    ) {
                        TemperamentId.entries
                    } else {
                        listOf(TemperamentId.valueOf(it))
                    }
                },
            days = map["days"]?.toInt() ?: DEFAULT_DAYS,
            seeds = map["seeds"]?.toInt() ?: DEFAULT_SEEDS,
            policy = CaregiverPolicy.named(map["policy"] ?: "attentive"),
            stepMinutes = map["step-minutes"]?.toInt() ?: 2,
            out = map["out"] ?: "reports",
        )
    } catch (e: IllegalArgumentException) {
        System.err.println("${banner()}: ${e.message}")
        null
    }
}

private const val DEFAULT_DAYS = 7
private const val NANOS_PER_SECOND = 1e9
private const val DEFAULT_SEEDS = 20
