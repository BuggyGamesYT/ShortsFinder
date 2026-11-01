package com.buggygamesyt.shortsfinder.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

enum class Reason(val label: String) {
    REPLAYED("Replayed"),
    REACTION("Reaction"),
    COMMENTS("Comments"),
}

data class Moment(
    val rank: Int,
    val peakSec: Int,
    val score: Int,
    val reasons: List<Reason>,
    val clipStart: Int,
    val clipEnd: Int,
    val details: List<String>,
)

/**
 * Turns the raw signals into a ranked list of clip-worthy moments.
 *
 * Each signal becomes a per-second curve scaled to 0..1:
 *  - replay: "Most replayed" intensity above the video's median
 *  - reaction: caption cues ([Laughter], "no way", "bro", lots of "!" or CAPS)
 *  - comments: how often (and how liked) comments point at that time
 * The curves are mixed with the user's weights. Signals that are missing for a
 * video are left out of the mix instead of counting as zero.
 */
object MomentFinder {

    data class Config(
        val weightReplay: Float = 1.0f,
        val weightReaction: Float = 0.7f,
        val weightComments: Float = 0.9f,
        val clipLength: Int = 30,
        val leadSeconds: Int = 3,
        val minClip: Int = 15,
        val maxClip: Int = 60,
        val dedupeSeconds: Int = 10,
        val maxMoments: Int = 10,
    )

    private const val REASON_THRESHOLD = 0.3

    fun find(signals: VideoSignals, cfg: Config): List<Moment> =
        find(signals.durationSec, signals.heat, signals.captions, signals.stamps, cfg)

    fun find(
        durationSec: Int,
        heat: List<HeatMarker>,
        captions: List<CaptionLine>,
        stamps: List<CommentStamp>,
        cfg: Config,
    ): List<Moment> {
        val inferred = maxOf(
            heat.maxOfOrNull { ceil(it.startSec + it.durationSec).toInt() } ?: 0,
            captions.maxOfOrNull { ceil(it.startSec + it.durationSec).toInt() } ?: 0,
            (stamps.maxOfOrNull { it.seconds } ?: 0) + 1,
        )
        val n = max(1, if (durationSec > 0) durationSec else inferred)

        val replay = replayCurve(heat, n)
        val reaction = reactionCurve(captions, n)
        val comments = commentCurve(stamps, n)

        val active = ArrayList<Pair<DoubleArray, Double>>()
        if (replay != null && cfg.weightReplay > 0f) active.add(replay to cfg.weightReplay.toDouble())
        if (reaction != null && cfg.weightReaction > 0f) active.add(reaction to cfg.weightReaction.toDouble())
        if (comments != null && cfg.weightComments > 0f) active.add(comments to cfg.weightComments.toDouble())
        if (active.isEmpty()) return emptyList()

        val weightSum = active.sumOf { it.second }
        val combined = DoubleArray(n) { s -> active.sumOf { (curve, w) -> curve[s] * w } / weightSum }
        val smooth = movingAverage(combined, 2)

        val candidates = (0 until n)
            .filter { s ->
                smooth[s] > 0.0 &&
                    (s == 0 || smooth[s] >= smooth[s - 1]) &&
                    (s == n - 1 || smooth[s] >= smooth[s + 1])
            }
            .sortedByDescending { smooth[it] }

        val picked = ArrayList<Int>()
        for (c in candidates) {
            if (picked.size >= cfg.maxMoments) break
            val peak = argMaxNear(combined, c, 3)
            if (picked.none { abs(it - peak) < cfg.dedupeSeconds }) picked.add(peak)
        }

        val moments = picked.map { peak ->
            val reasons = ArrayList<Reason>()
            if (replay != null && windowMax(replay, peak, 3) >= REASON_THRESHOLD) reasons.add(Reason.REPLAYED)
            if (reaction != null && windowMax(reaction, peak, 4) >= REASON_THRESHOLD) reasons.add(Reason.REACTION)
            if (comments != null && windowMax(comments, peak, 4) >= REASON_THRESHOLD) reasons.add(Reason.COMMENTS)
            if (reasons.isEmpty()) {
                val best = listOfNotNull(
                    replay?.let { Reason.REPLAYED to windowMax(it, peak, 3) },
                    reaction?.let { Reason.REACTION to windowMax(it, peak, 4) },
                    comments?.let { Reason.COMMENTS to windowMax(it, peak, 4) },
                ).maxByOrNull { it.second }
                if (best != null) reasons.add(best.first)
            }
            val (start, end) = clipWindow(peak, n, cfg)
            Moment(
                rank = 0,
                peakSec = peak,
                score = (windowMax(combined, peak, 2) * 100).toInt().coerceIn(0, 100),
                reasons = reasons,
                clipStart = start,
                clipEnd = end,
                details = details(peak, heat, captions, stamps),
            )
        }
        return moments
            .sortedByDescending { it.score }
            .mapIndexed { i, m -> m.copy(rank = i + 1) }
    }

    /** Clip window that starts a little before the peak and never runs past the video. */
    fun clipWindow(peak: Int, durationSec: Int, cfg: Config): Pair<Int, Int> {
        val len = cfg.clipLength.coerceIn(cfg.minClip, cfg.maxClip)
        if (durationSec <= len) return 0 to durationSec
        var start = max(0, peak - cfg.leadSeconds)
        var end = start + len
        if (end > durationSec) {
            end = durationSec
            start = max(0, durationSec - len)
        }
        return start to end
    }

    // ------------------------------------------------------------ curves

    private fun replayCurve(heat: List<HeatMarker>, n: Int): DoubleArray? {
        if (heat.isEmpty()) return null
        val raw = DoubleArray(n)
        heat.forEachIndexed { index, m ->
            // The very first segment is inflated by everyone who starts the video.
            val intensity = if (index == 0) m.intensity * 0.7 else m.intensity
            val from = m.startSec.toInt().coerceIn(0, n - 1)
            val to = ceil(m.startSec + max(m.durationSec, 1.0)).toInt().coerceIn(from + 1, n)
            for (s in from until to) raw[s] = max(raw[s], intensity)
        }
        val sorted = raw.sorted()
        val median = sorted[sorted.size / 2]
        val top = sorted.last()
        val span = top - median
        if (top <= 0.0) return null
        if (span <= 1e-6) return DoubleArray(n) // flat graph: nothing stands out
        return DoubleArray(n) { s -> max(0.0, raw[s] - median) / span }
    }

    private fun reactionCurve(captions: List<CaptionLine>, n: Int): DoubleArray? {
        if (captions.isEmpty()) return null
        val raw = DoubleArray(n)
        for (line in captions) {
            val score = cueScore(line.text)
            if (score <= 0.0) continue
            val s = line.startSec.toInt().coerceIn(0, n - 1)
            raw[s] += score
        }
        return normalize(windowSum(raw, 3))
    }

    private fun commentCurve(stamps: List<CommentStamp>, n: Int): DoubleArray? {
        if (stamps.isEmpty()) return null
        val raw = DoubleArray(n)
        for (st in stamps) {
            val s = st.seconds.coerceIn(0, n - 1)
            raw[s] += st.weight
        }
        return normalize(windowSum(raw, 3))
    }

    // ------------------------------------------------------------ caption cues

    private val TAG_SCORES = listOf(
        "laugh" to 1.0, "scream" to 0.9, "gasp" to 0.9, "applause" to 0.8,
        "cheer" to 0.8, "music" to 0.4,
    )

    private val PHRASES = listOf(
        "no way" to 1.0, "oh my god" to 1.0, "oh my gosh" to 0.9, "omg" to 1.0,
        "what the" to 0.9, "wtf" to 0.9, "lmao" to 0.9, "lol" to 0.8, "haha" to 0.8,
        "holy" to 0.8, "are you kidding" to 0.8, "insane" to 0.7, "crazy" to 0.6,
        "bruh" to 0.6, "bro" to 0.6, "dude" to 0.5, "wait" to 0.6, "what" to 0.6,
        "how" to 0.3, "yo" to 0.4, "glitch" to 0.4,
    )

    private val PHRASE_REGEX: List<Pair<Regex, Double>> = PHRASES.map { (p, w) ->
        Regex("\\b" + Regex.escape(p) + "\\b") to w
    }
    private val TAG_REGEX = Regex("\\[([^\\]]+)\\]")
    private val STRETCHED = Regex("([a-z])\\1{2,}")
    private val WORD = Regex("[A-Za-z]{3,}")

    /** How much a caption line sounds like a reaction. 0 = nothing. Capped at 3. */
    fun cueScore(text: String): Double {
        var score = 0.0
        val lower = text.lowercase()
        for (m in TAG_REGEX.findAll(lower)) {
            val tag = m.groupValues[1]
            val hit = TAG_SCORES.firstOrNull { tag.contains(it.first) }
            score += hit?.second ?: 0.3
        }
        val plain = TAG_REGEX.replace(lower, " ")
        var phraseScore = 0.0
        for ((re, w) in PHRASE_REGEX) {
            if (re.containsMatchIn(plain)) phraseScore += w
        }
        score += min(phraseScore, 1.8)
        if (STRETCHED.containsMatchIn(plain)) score += 0.5 // "noooo", "whaaat"

        // Loudness proxies from the text itself: exclamation marks and shouted words.
        val bangs = text.count { it == '!' }
        score += min(1.0, bangs * 0.3)
        val words = WORD.findAll(TAG_REGEX.replace(text, " ")).map { it.value }.toList()
        if (words.isNotEmpty()) {
            val caps = words.count { w -> w.all { it.isUpperCase() } }
            if (caps >= 2 || caps.toDouble() / words.size >= 0.5) score += 0.5
        }
        return min(score, 3.0)
    }

    // ------------------------------------------------------------ comments

    private val STAMP = Regex("(?<![\\d:])(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?![\\d:])")

    /** Finds timestamps like 1:23, 0:05 or 1:02:03 in comments. One count per time per comment. */
    fun extractStamps(comments: List<RawComment>, durationSec: Int): List<CommentStamp> {
        val out = ArrayList<CommentStamp>()
        for (c in comments) {
            val seen = HashSet<Int>()
            for (m in STAMP.findAll(c.text)) {
                val h = m.groupValues[1].toIntOrNull() ?: 0
                val mm = m.groupValues[2].toIntOrNull() ?: continue
                val ss = m.groupValues[3].toIntOrNull() ?: continue
                if (ss >= 60) continue
                if (h > 0 && mm >= 60) continue
                val total = h * 3600 + mm * 60 + ss
                if (durationSec > 0 && total > durationSec) continue
                if (!seen.add(total)) continue
                val weight = 1.0 + ln(1.0 + c.likes.toDouble()) / ln(10.0)
                out.add(CommentStamp(total, weight, c.text.take(140)))
            }
        }
        return out
    }

    // ------------------------------------------------------------ details

    private fun details(
        peak: Int,
        heat: List<HeatMarker>,
        captions: List<CaptionLine>,
        stamps: List<CommentStamp>,
    ): List<String> {
        val out = ArrayList<String>()
        val marker = heat.firstOrNull { peak >= it.startSec && peak < it.startSec + max(it.durationSec, 1.0) }
        if (marker != null) out.add("Replay intensity ${(marker.intensity * 100).toInt()}%")

        val near = stamps.filter { abs(it.seconds - peak) <= 5 }
        if (near.isNotEmpty()) {
            val word = if (near.size == 1) "comment points" else "comments point"
            out.add("${near.size} $word here")
            val quote = near.maxByOrNull { it.weight }?.text
            if (!quote.isNullOrBlank()) out.add("\u201C${quote.replace('\n', ' ').take(90)}\u201D")
        }

        val cue = captions
            .filter { it.startSec >= peak - 5 && it.startSec <= peak + 5 }
            .maxByOrNull { cueScore(it.text) }
        if (cue != null && cueScore(cue.text) > 0.0) out.add("Captions: \u201C${cue.text.take(80)}\u201D")
        return out
    }

    // ------------------------------------------------------------ math

    private fun normalize(a: DoubleArray): DoubleArray {
        val top = a.maxOrNull() ?: 0.0
        if (top <= 0.0) return DoubleArray(a.size)
        return DoubleArray(a.size) { a[it] / top }
    }

    private fun windowSum(a: DoubleArray, radius: Int): DoubleArray =
        DoubleArray(a.size) { i ->
            var sum = 0.0
            for (j in max(0, i - radius)..min(a.size - 1, i + radius)) sum += a[j]
            sum
        }

    private fun movingAverage(a: DoubleArray, radius: Int): DoubleArray =
        DoubleArray(a.size) { i ->
            val from = max(0, i - radius)
            val to = min(a.size - 1, i + radius)
            var sum = 0.0
            for (j in from..to) sum += a[j]
            sum / (to - from + 1)
        }

    private fun windowMax(a: DoubleArray, center: Int, radius: Int): Double {
        var best = 0.0
        for (j in max(0, center - radius)..min(a.size - 1, center + radius)) best = max(best, a[j])
        return best
    }

    private fun argMaxNear(a: DoubleArray, center: Int, radius: Int): Int {
        var bestIdx = center
        var best = a[center]
        for (j in max(0, center - radius)..min(a.size - 1, center + radius)) {
            if (a[j] > best) {
                best = a[j]
                bestIdx = j
            }
        }
        return bestIdx
    }
}
