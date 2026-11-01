package com.buggygamesyt.shortsfinder.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Follows a path of object keys (String) and array indexes (Int).
 * Returns null as soon as a step is missing.
 */
internal fun navigate(root: Any?, vararg path: Any): Any? {
    var cur: Any? = root
    for (step in path) {
        cur = when {
            cur is JSONObject && step is String -> cur.opt(step)
            cur is JSONArray && step is Int -> cur.opt(step)
            else -> return null
        }
        if (cur == null || cur == JSONObject.NULL) return null
    }
    return cur
}

internal fun navString(root: Any?, vararg path: Any): String? {
    val v = navigate(root, *path) ?: return null
    return if (v is String) v else v.toString()
}

/** Reads YouTube text objects: {simpleText}, {runs:[{text}]} or {content}. */
internal fun textOf(o: Any?): String? {
    if (o is String) return o
    if (o !is JSONObject) return null
    val simple = o.optString("simpleText")
    if (simple.isNotEmpty()) return simple
    val runs = o.optJSONArray("runs")
    if (runs != null) {
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        if (sb.isNotEmpty()) return sb.toString()
    }
    val content = o.optString("content")
    if (content.isNotEmpty()) return content
    return null
}

/**
 * Walks the whole tree and collects every object stored under one of [keys].
 * Matches are not descended into unless [descendIntoMatches] is true.
 * JSONObject on Android keeps insertion order, so results follow document order.
 */
internal fun collectByKey(
    node: Any?,
    keys: Set<String>,
    out: MutableList<Pair<String, JSONObject>>,
    descendIntoMatches: Boolean = false,
    depth: Int = 0
) {
    if (depth > 250) return
    when (node) {
        is JSONObject -> {
            val it = node.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = node.opt(k)
                if (k in keys && v is JSONObject) {
                    out.add(k to v)
                    if (descendIntoMatches) collectByKey(v, keys, out, true, depth + 1)
                } else {
                    collectByKey(v, keys, out, descendIntoMatches, depth + 1)
                }
            }
        }
        is JSONArray -> {
            for (i in 0 until node.length()) {
                collectByKey(node.opt(i), keys, out, descendIntoMatches, depth + 1)
            }
        }
    }
}

internal fun collectByKey(node: Any?, key: String): List<JSONObject> {
    val out = mutableListOf<Pair<String, JSONObject>>()
    collectByKey(node, setOf(key), out)
    return out.map { it.second }
}

/**
 * Finds a JSON object literal assigned right after [marker] in an HTML page,
 * e.g. `var ytInitialData = {...};`. Uses brace matching that respects strings.
 */
internal fun extractJsonObject(html: String, marker: String): JSONObject? {
    var idx = html.indexOf(marker)
    while (idx >= 0) {
        val afterMarker = idx + marker.length
        val start = html.indexOf('{', afterMarker)
        if (start < 0) return null
        val between = html.substring(afterMarker, start)
        if (between.length < 12 && between.all { it.isWhitespace() || it == '=' || it == ']' || it == '"' || it == '\'' }) {
            val end = matchingBrace(html, start)
            if (end > start) {
                try {
                    return JSONObject(html.substring(start, end + 1))
                } catch (_: Exception) {
                    // keep looking
                }
            }
        }
        idx = html.indexOf(marker, afterMarker)
    }
    return null
}

private fun matchingBrace(s: String, start: Int): Int {
    var depth = 0
    var inString = false
    var quote = '"'
    var escaped = false
    var i = start
    while (i < s.length) {
        val c = s[i]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == quote) {
                inString = false
            }
        } else {
            when (c) {
                '"', '\'' -> {
                    inString = true
                    quote = c
                }
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        i++
    }
    return -1
}

/** "1:23" -> 83, "1:02:03" -> 3723. Null when it isn't a clock string. */
fun parseClock(s: String?): Int? {
    if (s.isNullOrBlank()) return null
    val parts = s.trim().split(":")
    if (parts.size !in 2..3) return null
    var total = 0
    for (p in parts) {
        val n = p.trim().toIntOrNull() ?: return null
        total = total * 60 + n
    }
    return total
}

/** "1.2K" -> 1200, "3M" -> 3000000, "45" -> 45, anything else -> 0. */
fun parseCount(s: String?): Int {
    if (s.isNullOrBlank()) return 0
    val cleaned = s.trim().replace(Regex("(?<=\\d),(?=\\d{3}(\\D|$))"), "")
    val m = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*([KkMmBb]?)").find(cleaned) ?: return 0
    val num = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0
    val mult = when (m.groupValues[2].lowercase()) {
        "k" -> 1_000.0
        "m" -> 1_000_000.0
        "b" -> 1_000_000_000.0
        else -> 1.0
    }
    return (num * mult).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
}
