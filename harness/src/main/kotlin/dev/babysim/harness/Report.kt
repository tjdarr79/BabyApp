package dev.babysim.harness

import dev.babysim.engine.model.AgeBandId
import dev.babysim.engine.model.TemperamentId
import dev.babysim.engine.params.Evidence
import dev.babysim.engine.params.ParamSet
import dev.babysim.engine.params.Provenance
import dev.babysim.engine.params.ReviewStatus
import dev.babysim.harness.Simulation.Companion.MILLIS_PER_DAY
import java.util.Locale

/** One metric for one band × temperament, across seeds. */
data class Row(
    val metric: Metric,
    val day1Median: Double,
    val median: Double,
    val p10: Double,
    val p90: Double,
    val target: Target?,
) {
    val checked: Double get() = if (target?.stat == Stat.P90) p90 else median
    val outOfRange: Boolean get() = target != null && !target.accepts(checked)
}

data class ComboReport(
    val band: AgeBandId,
    val temperament: TemperamentId,
    val rows: List<Row>,
) {
    val outOfRange: List<Row> get() = rows.filter { it.outOfRange }
}

object Report {
    private const val P10 = 0.1
    private const val P90 = 0.9

    fun evaluate(
        params: ParamSet,
        band: AgeBandId,
        temperament: TemperamentId,
        runs: List<RunResult>,
    ): ComboReport {
        val full = runs.map { Metrics.compute(it) }
        val day1 = runs.map { Metrics.compute(it, it.start.epochMillis, it.start.epochMillis + MILLIS_PER_DAY) }
        val targets = Targets.forBand(band, params.age(band)).associateBy { it.metric }
        val rows =
            Metric.entries.map { metric ->
                val values = full.map { it.getValue(metric) }
                Row(
                    metric = metric,
                    day1Median = Metrics.median(day1.map { it.getValue(metric) }),
                    median = Metrics.median(values),
                    p10 = Metrics.quantile(values, P10),
                    p90 = Metrics.quantile(values, P90),
                    target = targets[metric],
                )
            }
        return ComboReport(band, temperament, rows)
    }

    fun markdown(
        title: String,
        settings: String,
        combos: List<ComboReport>,
        provenance: List<Provenance>,
    ): String =
        buildString {
            appendLine("# $title")
            appendLine()
            appendLine(settings)
            appendLine()
            appendLine(
                "All parameter values are `UNREVIEWED` until the childcare reviewer signs off. " +
                    "Targets come from docs/06.",
            )
            appendLine("A metric marked **OUT** is outside its target; *Levers* names the parameters that move it.")
            appendLine()
            appendLine("## Summary")
            appendLine()
            appendLine("| Band | Temperament | Out of range | Metrics out of range |")
            appendLine("|---|---|---|---|")
            for (c in combos) {
                val names = c.outOfRange.joinToString(", ") { it.metric.label }.ifEmpty { "—" }
                appendLine(
                    "| ${c.band} | ${c.temperament} | ${c.outOfRange.size} of ${c.rows.count {
                        it.target != null
                    }} | $names |",
                )
            }
            appendLine()
            appendProvenance(provenance)
            for (c in combos) {
                appendLine("## ${c.band} / ${c.temperament}")
                appendLine()
                appendLine("| Metric | Day 1 | Median | p10 | p90 | Target | Status | Levers |")
                appendLine("|---|---|---|---|---|---|---|---|")
                for (r in c.rows) {
                    val unit = r.metric.unit.let { if (it.isEmpty()) "" else " $it" }
                    val status =
                        when {
                            r.target == null -> "—"
                            r.outOfRange -> "**OUT** (${r.target.stat.name.lowercase()} ${fmt(r.checked)})"
                            else -> "pass"
                        }
                    appendLine(
                        "| ${r.metric.label}$unit | ${fmt(
                            r.day1Median,
                        )} | ${fmt(r.median)} | ${fmt(r.p10)} | ${fmt(r.p90)} | " +
                            "${targetText(r.target)} | $status | ${r.target?.levers?.joinToString(", ") ?: ""} |",
                    )
                }
                appendLine()
            }
        }

    fun csv(
        policy: String,
        combos: List<ComboReport>,
    ): String =
        buildString {
            appendLine(
                "policy,band,temperament,metric,day1_median,median,p10,p90," +
                    "target_min,target_max,target_stat,out_of_range",
            )
            for (c in combos) {
                for (r in c.rows) {
                    appendLine(
                        listOf(
                            policy,
                            c.band.name,
                            c.temperament.name,
                            r.metric.name,
                            fmt(r.day1Median),
                            fmt(r.median),
                            fmt(r.p10),
                            fmt(r.p90),
                            r.target?.min?.let(::fmt) ?: "",
                            r.target?.max?.let(::fmt) ?: "",
                            r.target?.stat?.name ?: "",
                            r.outOfRange.toString(),
                        ).joinToString(","),
                    )
                }
            }
        }

    private fun StringBuilder.appendProvenance(provenance: List<Provenance>) {
        appendLine("## Parameters in use")
        appendLine()
        val byEvidence = provenance.groupingBy { it.evidence }.eachCount()
        val unreviewed = provenance.count { it.reviewStatus == ReviewStatus.UNREVIEWED }
        appendLine(
            "${provenance.size} values, $unreviewed `UNREVIEWED`. By evidence: " +
                Evidence.entries.joinToString(", ") { "${it.name} ${byEvidence[it] ?: 0}" } + ".",
        )
        appendLine()
        appendLine("`DESIGN_ASSUMPTION` values by file (no adequate source; the reviewer decides these):")
        appendLine()
        provenance.filter { it.evidence == Evidence.DESIGN_ASSUMPTION }.groupBy { it.file }.forEach { (file, entries) ->
            appendLine("- `$file`: ${entries.joinToString(", ") { it.field }}")
        }
        appendLine()
    }

    private fun targetText(target: Target?): String =
        when {
            target == null -> "—"
            target.min == 0.0 && target.max == 0.0 -> "0"
            target.min == 0.0 -> "≤ ${fmt(target.max)}"
            else -> "${fmt(target.min)}–${fmt(target.max)}"
        }

    fun fmt(value: Double): String = if (value.isNaN()) "n/a" else String.format(Locale.ROOT, "%.2f", value)
}
