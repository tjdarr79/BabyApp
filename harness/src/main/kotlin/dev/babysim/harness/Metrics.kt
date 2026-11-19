package dev.babysim.harness

import dev.babysim.engine.BabyEvent
import dev.babysim.engine.EventFamily
import dev.babysim.engine.EventType
import dev.babysim.engine.model.ActionOutcome
import dev.babysim.engine.model.Activity
import dev.babysim.harness.Simulation.Companion.MILLIS_PER_DAY
import dev.babysim.harness.Simulation.Companion.MILLIS_PER_MINUTE

/** The docs/04 §10 report metrics. Day = 07:00–19:00 local, night = 19:00–07:00 (docs/01 B7 defaults). */
enum class Metric(
    val label: String,
    val unit: String,
) {
    CARE_REQUESTS_PER_DAY("Care requests per day", ""),
    NIGHT_CARE_REQUESTS("Night care requests per night", ""),
    LONGEST_DAY_CARE_GAP_H("Longest daytime gap with no care request", "h"),
    LONGEST_AWAKE_QUIET_GAP_MIN("Longest awake gap with no audible activity", "min"),
    AUDIBLE_EVENTS_PER_HOUR("Audible events per hour", ""),
    TOTAL_SLEEP_H("Total sleep per 24 h", "h"),
    NAPS_PER_DAY("Naps per day", ""),
    DAY_NAP_MIN("Median daytime nap length", "min"),
    NIGHT_WAKINGS("Night wakings per night", ""),
    FUSS_CRY_MIN("Fussing and crying per 24 h", "min"),
    FEEDS_PER_DAY("Feeds per day", ""),
    FEED_INTERVAL_MIN("Median time between feeds", "min"),
    WET_DIAPERS_PER_DAY("Wet diapers per day", ""),
    DIAPER_CHANGES_PER_DAY("Diaper changes per day", ""),
    DAY_TO_DAY_SIMILARITY("Day-to-day schedule similarity", ""),
    AUTO_PAUSES("Auto-pauses", ""),
}

@Suppress("TooManyFunctions") // One small function per metric keeps each definition readable.
object Metrics {
    private const val MILLIS_PER_HOUR = 3_600_000.0
    private const val HOURS_PER_DAY = 24.0
    private const val DAY_START_MIN = 7 * 60
    private const val NIGHT_START_MIN = 19 * 60
    private const val BIN_MINUTES = 15
    private const val MEDIAN = 0.5
    private val ASLEEP = setOf(Activity.ASLEEP_ACTIVE, Activity.ASLEEP_QUIET, Activity.EXHAUSTED_SLEEP)
    private val UPSET = setOf(Activity.FUSSING, Activity.CRYING, Activity.CRYING_INTENSE)

    private data class Segment(
        val from: Long,
        val to: Long,
        val activity: Activity,
    )

    /** Metrics over [from, to) of one run. Similarity needs at least two whole days and is NaN otherwise. */
    fun compute(
        run: RunResult,
        from: Long = run.start.epochMillis,
        to: Long = run.end.epochMillis,
    ): Map<Metric, Double> {
        val days = (to - from).toDouble() / MILLIS_PER_DAY
        val events = run.events.filter { it.at.epochMillis in from until to }
        val segments = timeline(run).mapNotNull { clip(it, from, to) }
        val opened = events.filter { it.type == EventType.CARE_REQUEST_OPENED }.map { it.at.epochMillis }
        val feeds = run.events.filter { it.outcome == ActionOutcome.FEEDING }.map { it.at.epochMillis }
        return mapOf(
            Metric.CARE_REQUESTS_PER_DAY to opened.size / days,
            Metric.NIGHT_CARE_REQUESTS to opened.count { isNight(it) } / days,
            Metric.LONGEST_DAY_CARE_GAP_H to longestDaytimeGap(opened, from, to) / MILLIS_PER_HOUR,
            Metric.LONGEST_AWAKE_QUIET_GAP_MIN to longestAwakeQuietGap(segments, events) / MILLIS_PER_MINUTE.toDouble(),
            Metric.AUDIBLE_EVENTS_PER_HOUR to
                events.count { it.type.family == EventFamily.AUDIBLE } / (days * HOURS_PER_DAY),
            Metric.TOTAL_SLEEP_H to
                segments.filter { it.activity in ASLEEP }.sumOf { it.to - it.from } / MILLIS_PER_HOUR / days,
            Metric.NAPS_PER_DAY to
                events.count { it.type == EventType.FELL_ASLEEP && !isNight(it.at.epochMillis) } / days,
            Metric.DAY_NAP_MIN to median(dayNapMinutes(events)),
            Metric.NIGHT_WAKINGS to events.count { it.type == EventType.WOKE && isNight(it.at.epochMillis) } / days,
            Metric.FUSS_CRY_MIN to
                segments
                    .filter {
                        it.activity in UPSET
                    }.sumOf { it.to - it.from } / MILLIS_PER_MINUTE.toDouble() / days,
            Metric.FEEDS_PER_DAY to feeds.count { it in from until to } / days,
            Metric.FEED_INTERVAL_MIN to median(feeds.zipWithNext { a, b -> (b - a) / MILLIS_PER_MINUTE.toDouble() }),
            Metric.WET_DIAPERS_PER_DAY to events.count { it.type == EventType.DIAPER_WET } / days,
            Metric.DIAPER_CHANGES_PER_DAY to
                events.count { it.outcome == ActionOutcome.CHANGED_WET || it.outcome == ActionOutcome.CHANGED_SOILED } /
                days,
            Metric.DAY_TO_DAY_SIMILARITY to similarity(opened, from, to),
            Metric.AUTO_PAUSES to events.count { it.type == EventType.AUTO_PAUSED }.toDouble(),
        )
    }

    /** What the baby was doing over time, rebuilt from its events. Ends at a pause. */
    private fun timeline(run: RunResult): List<Segment> {
        val segments = mutableListOf<Segment>()
        var since = run.start.epochMillis
        var activity = Activity.AWAKE_CALM
        val pause = run.events.firstOrNull { it.type == EventType.AUTO_PAUSED || it.type == EventType.PAUSED }
        val end = pause?.at?.epochMillis ?: run.end.epochMillis
        for (event in run.events.filter { it.at.epochMillis < end }) {
            val next = activityAfter(event)
            if (next != null && next != activity) {
                segments += Segment(since, event.at.epochMillis, activity)
                since = event.at.epochMillis
                activity = next
            }
        }
        segments += Segment(since, end, activity)
        return segments
    }

    private fun activityAfter(event: BabyEvent): Activity? =
        when (event.type) {
            EventType.ACTIVITY_CHANGED, EventType.FELL_ASLEEP -> event.activity
            EventType.WOKE -> Activity.AWAKE_CALM
            else -> null
        }

    private fun clip(
        segment: Segment,
        from: Long,
        to: Long,
    ): Segment? {
        val start = maxOf(segment.from, from)
        val end = minOf(segment.to, to)
        return if (end > start) segment.copy(from = start, to = end) else null
    }

    /** Length of each nap that starts in the daytime, from falling asleep to the next waking. */
    private fun dayNapMinutes(events: List<BabyEvent>): List<Double> {
        val starts = events.filter { it.type == EventType.FELL_ASLEEP && !isNight(it.at.epochMillis) }
        return starts.mapNotNull { start ->
            events
                .firstOrNull { it.type == EventType.WOKE && it.at.epochMillis > start.at.epochMillis }
                ?.let { (it.at.epochMillis - start.at.epochMillis) / MILLIS_PER_MINUTE.toDouble() }
        }
    }

    private fun minuteOfDay(t: Long): Int = (t.mod(MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt()

    private fun isNight(t: Long): Boolean = minuteOfDay(t) !in DAY_START_MIN until NIGHT_START_MIN

    /** Longest stretch inside any 07:00–19:00 window with no care request opening. */
    private fun longestDaytimeGap(
        opened: List<Long>,
        from: Long,
        to: Long,
    ): Long {
        var longest = 0L
        var dayStart = from - from.mod(MILLIS_PER_DAY) + DAY_START_MIN * MILLIS_PER_MINUTE
        while (dayStart < to) {
            val start = maxOf(dayStart, from)
            val end = minOf(dayStart + (NIGHT_START_MIN - DAY_START_MIN) * MILLIS_PER_MINUTE, to)
            if (end > start) {
                val points = listOf(start) + opened.filter { it in start until end } + end
                longest = maxOf(longest, points.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0L)
            }
            dayStart += MILLIS_PER_DAY
        }
        return longest
    }

    /** Longest stretch while awake and not fussing or crying with no audible event (coo, babble, burp, …). */
    private fun longestAwakeQuietGap(
        segments: List<Segment>,
        events: List<BabyEvent>,
    ): Long {
        val audible = events.filter { it.type.family == EventFamily.AUDIBLE }.map { it.at.epochMillis }
        val quiet = merge(segments.filter { it.activity !in ASLEEP && it.activity !in UPSET })
        return quiet.maxOfOrNull { (start, end) ->
            val points = listOf(start) + audible.filter { it in start until end } + end
            points.zipWithNext { a, b -> b - a }.max()
        } ?: 0L
    }

    private fun merge(segments: List<Segment>): List<Pair<Long, Long>> {
        val out = mutableListOf<Pair<Long, Long>>()
        for (s in segments.sortedBy { it.from }) {
            val last = out.lastOrNull()
            if (last != null &&
                s.from <= last.second
            ) {
                out[out.lastIndex] = last.first to maxOf(last.second, s.to)
            } else {
                out +=
                    s.from to s.to
            }
        }
        return out
    }

    /** Mean Jaccard similarity of consecutive days' 15-minute bins that hold a care request (docs/06). */
    private fun similarity(
        opened: List<Long>,
        from: Long,
        to: Long,
    ): Double {
        val days = ((to - from) / MILLIS_PER_DAY).toInt()
        if (days < 2) return Double.NaN
        val bins =
            List(days) { d ->
                val dayFrom = from + d * MILLIS_PER_DAY
                opened
                    .filter { it in dayFrom until dayFrom + MILLIS_PER_DAY }
                    .map { ((it - dayFrom) / (BIN_MINUTES * MILLIS_PER_MINUTE)).toInt() }
                    .toSet()
            }
        val scores =
            bins.zipWithNext { a, b ->
                val union = (a + b).size
                if (union == 0) 0.0 else (a intersect b).size.toDouble() / union
            }
        return scores.average()
    }

    fun median(values: List<Double>): Double = quantile(values, MEDIAN)

    fun quantile(
        values: List<Double>,
        q: Double,
    ): Double {
        val sorted = values.filter { !it.isNaN() }.sorted()
        if (sorted.isEmpty()) return Double.NaN
        val position = q * (sorted.size - 1)
        val lower = sorted[position.toInt()]
        val upper = sorted[minOf(position.toInt() + 1, sorted.size - 1)]
        return lower + (upper - lower) * (position - position.toInt())
    }
}
