package com.buggygamesyt.shortsfinder.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

enum class ChannelTab(val label: String, val defaultParams: String, val urlSuffix: String) {
    VIDEOS("Videos", "EgZ2aWRlb3PyBgQKAjoA", "/videos"),
    SHORTS("Shorts", "EgZzaG9ydHPyBgUKA5oBAA%3D%3D", "/shorts"),
}

data class ChannelPage(val videos: List<Video>, val nextToken: String?)

data class PlayerInfo(
    val title: String?,
    val lengthSeconds: Int,
    val captionTracks: List<CaptionTrack>,
    val status: String?,
)

/**
 * Talks to YouTube's public web endpoints (the same ones youtube.com uses).
 * No API key or account is needed.
 */
class YouTubeClient(private val http: Http = Http()) {

    @Volatile private var clientVersion: String = DEFAULT_CLIENT_VERSION
    @Volatile private var visitorData: String? = null
    private val tabParams = HashMap<String, String>()

    // ---------------------------------------------------------------- session

    private fun context(): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", clientVersion)
            .put("hl", "en")
            .put("gl", "US")
        visitorData?.let { client.put("visitorData", it) }
        return JSONObject().put("client", client)
    }

    private fun innertubeHeaders(): Map<String, String> {
        val h = HashMap<String, String>()
        h["X-YouTube-Client-Name"] = "1"
        h["X-YouTube-Client-Version"] = clientVersion
        h["Origin"] = BASE
        h["Referer"] = "$BASE/"
        visitorData?.let { h["X-Goog-Visitor-Id"] = it }
        return h
    }

    private suspend fun innertube(endpoint: String, body: JSONObject): JSONObject {
        body.put("context", context())
        return http.postJson("$BASE/youtubei/v1/$endpoint?prettyPrint=false", body, innertubeHeaders())
    }

    /** Picks up the current web client version and visitor id from any youtube.com page. */
    private fun absorbSession(html: String) {
        Regex("\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"").find(html)?.let {
            clientVersion = it.groupValues[1]
        }
        Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(html)?.let {
            visitorData = it.groupValues[1]
        }
    }

    // ---------------------------------------------------------------- channel

    /** Accepts "@handle", "handle", a channel URL, or a UC… channel id. Returns the UC… id. */
    suspend fun resolveChannelId(input: String): String {
        val path = channelPath(input)
        val url = "$BASE/$path"
        var html: String? = null
        try {
            html = http.get(url)
            absorbSession(html)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code != 404) throw e
        }
        if (path.startsWith("channel/")) return path.removePrefix("channel/")
        if (html != null) {
            val fromHtml = extractChannelId(html)
            if (fromHtml != null) return fromHtml
        }
        val resp = innertube("navigation/resolve_url", JSONObject().put("url", url))
        val browseId = navString(resp, "endpoint", "browseEndpoint", "browseId")
        if (browseId != null && browseId.startsWith("UC")) return browseId
        throw YouTubeException("Couldn't find the channel \"${input.trim()}\". Check the handle in Settings.")
    }

    private fun extractChannelId(html: String): String? {
        val patterns = listOf(
            Regex("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/channel/(UC[\\w-]{22})\""),
            Regex("\"externalId\":\"(UC[\\w-]{22})\""),
            Regex("<meta itemprop=\"identifier\" content=\"(UC[\\w-]{22})\""),
            Regex("\"channelId\":\"(UC[\\w-]{22})\""),
        )
        for (p in patterns) {
            val m = p.find(html)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    /**
     * Loads one page of a channel tab. Pass [continuation] = null for the first page,
     * then the returned [ChannelPage.nextToken] until it is null.
     */
    suspend fun fetchChannelTab(channelId: String, tab: ChannelTab, continuation: String?): ChannelPage {
        if (continuation != null) {
            val resp = innertube("browse", JSONObject().put("continuation", continuation))
            val root: Any = resp.optJSONArray("onResponseReceivedActions")
                ?: resp.optJSONArray("onResponseReceivedEndpoints")
                ?: resp
            return parseItems(root, tab)
        }
        val key = channelId + tab.name
        val params = synchronized(tabParams) { tabParams[key] } ?: tab.defaultParams
        val first = parseSelectedTab(
            innertube("browse", JSONObject().put("browseId", channelId).put("params", params)), tab
        )
        if (first.videos.isNotEmpty() || first.nextToken != null) return first

        // The built-in tab params may have changed: read them from the channel's own tab list.
        val home = innertube("browse", JSONObject().put("browseId", channelId))
        val discovered = findTabParams(home, tab) ?: return first
        if (discovered == params) return first
        synchronized(tabParams) { tabParams[key] = discovered }
        return parseSelectedTab(
            innertube("browse", JSONObject().put("browseId", channelId).put("params", discovered)), tab
        )
    }

    private fun tabMatches(tabRenderer: JSONObject, tab: ChannelTab): Boolean {
        val url = navString(tabRenderer, "endpoint", "commandMetadata", "webCommandMetadata", "url").orEmpty()
        val title = tabRenderer.optString("title")
        return url.endsWith(tab.urlSuffix) || title.equals(tab.label, ignoreCase = true)
    }

    private fun parseSelectedTab(resp: JSONObject, tab: ChannelTab): ChannelPage {
        val tabs = navigate(resp, "contents", "twoColumnBrowseResultsRenderer", "tabs") as? JSONArray
            ?: return ChannelPage(emptyList(), null)
        for (i in 0 until tabs.length()) {
            val tr = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
            if (!tr.optBoolean("selected")) continue
            // If YouTube fell back to a different tab (e.g. Home), don't list its featured videos.
            if (!tabMatches(tr, tab)) return ChannelPage(emptyList(), null)
            val content = tr.opt("content") ?: return ChannelPage(emptyList(), null)
            return parseItems(content, tab)
        }
        return ChannelPage(emptyList(), null)
    }

    private fun findTabParams(resp: JSONObject, tab: ChannelTab): String? {
        val tabs = navigate(resp, "contents", "twoColumnBrowseResultsRenderer", "tabs") as? JSONArray
            ?: return null
        for (i in 0 until tabs.length()) {
            val tr = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
            if (tabMatches(tr, tab)) return navString(tr, "endpoint", "browseEndpoint", "params")
        }
        return null
    }

    private fun parseItems(root: Any, tab: ChannelTab): ChannelPage {
        val found = mutableListOf<Pair<String, JSONObject>>()
        collectByKey(root, ITEM_KEYS, found)
        val videos = ArrayList<Video>()
        var next: String? = null
        for ((key, o) in found) {
            val v: Video? = when (key) {
                "videoRenderer", "gridVideoRenderer" -> parseVideoRenderer(o, tab)
                "reelItemRenderer" -> parseReelItem(o)
                "shortsLockupViewModel" -> parseShortsLockup(o)
                "lockupViewModel" -> parseLockup(o, tab)
                else -> null
            }
            if (v != null) videos.add(v)
            if (key == "continuationItemRenderer") {
                val t = continuationToken(o)
                if (t != null) next = t
            }
        }
        return ChannelPage(videos.distinctBy { it.id }, next)
    }

    private fun continuationToken(o: JSONObject): String? =
        navString(o, "continuationEndpoint", "continuationCommand", "token")
            ?: navString(o, "button", "buttonRenderer", "command", "continuationCommand", "token")

    private fun parseVideoRenderer(o: JSONObject, tab: ChannelTab): Video? {
        val id = o.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        var length = textOf(o.opt("lengthText"))
        if (length == null) {
            for (ov in collectByKey(o.opt("thumbnailOverlays"), "thumbnailOverlayTimeStatusRenderer")) {
                val t = textOf(ov.opt("text"))
                if (parseClock(t) != null) {
                    length = t
                    break
                }
            }
        }
        return Video(
            id = id,
            title = textOf(o.opt("title")) ?: "(untitled)",
            views = textOf(o.opt("viewCountText")) ?: textOf(o.opt("shortViewCountText")),
            length = length,
            lengthSeconds = parseClock(length),
            published = textOf(o.opt("publishedTimeText")),
            isShort = tab == ChannelTab.SHORTS,
        )
    }

    private fun parseReelItem(o: JSONObject): Video? {
        val id = o.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        return Video(
            id = id,
            title = textOf(o.opt("headline")) ?: "(untitled)",
            views = textOf(o.opt("viewCountText")),
            length = null,
            lengthSeconds = null,
            published = null,
            isShort = true,
        )
    }

    private fun parseShortsLockup(o: JSONObject): Video? {
        val id = navString(o, "onTap", "innertubeCommand", "reelWatchEndpoint", "videoId")
            ?: o.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.isNotEmpty() }
            ?: return null
        return Video(
            id = id,
            title = navString(o, "overlayMetadata", "primaryText", "content")
                ?: o.optString("accessibilityText").substringBefore(",").ifEmpty { "(untitled)" },
            views = navString(o, "overlayMetadata", "secondaryText", "content"),
            length = null,
            lengthSeconds = null,
            published = null,
            isShort = true,
        )
    }

    private fun parseLockup(o: JSONObject, tab: ChannelTab): Video? {
        val type = o.optString("contentType")
        if (type.isNotEmpty() && type != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = o.optString("contentId").takeIf { it.isNotEmpty() } ?: return null
        val meta = navigate(o, "metadata", "lockupMetadataViewModel") as? JSONObject
        val title = navString(meta, "title", "content") ?: "(untitled)"

        val parts = ArrayList<String>()
        val rows = navigate(meta, "metadata", "contentMetadataViewModel", "metadataRows") as? JSONArray
        if (rows != null) {
            for (r in 0 until rows.length()) {
                val ps = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until ps.length()) {
                    val t = navString(ps.optJSONObject(p), "text", "content")
                    if (!t.isNullOrBlank()) parts.add(t)
                }
            }
        }
        val views = parts.firstOrNull { it.contains("view", ignoreCase = true) }
        val published = parts.firstOrNull { it.contains("ago", ignoreCase = true) || it.contains("Streamed") }

        var length: String? = null
        for (b in collectByKey(o.opt("contentImage"), "thumbnailBadgeViewModel")) {
            val t = b.optString("text")
            if (parseClock(t) != null) {
                length = t
                break
            }
        }
        return Video(
            id = id,
            title = title,
            views = views,
            length = length,
            lengthSeconds = parseClock(length),
            published = published,
            isShort = tab == ChannelTab.SHORTS,
        )
    }

    // ---------------------------------------------------------------- video

    private suspend fun fetchPlayer(videoId: String): PlayerInfo {
        val resp = innertube(
            "player",
            JSONObject()
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true)
        )
        return parsePlayer(resp)
    }

    private fun parsePlayer(resp: JSONObject): PlayerInfo {
        val details = resp.optJSONObject("videoDetails")
        val tracks = ArrayList<CaptionTrack>()
        val arr = navigate(resp, "captions", "playerCaptionsTracklistRenderer", "captionTracks") as? JSONArray
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val url = t.optString("baseUrl")
                if (url.isEmpty()) continue
                tracks.add(
                    CaptionTrack(
                        baseUrl = url,
                        languageCode = t.optString("languageCode"),
                        name = textOf(t.opt("name")) ?: t.optString("languageCode"),
                        isAuto = t.optString("kind") == "asr",
                    )
                )
            }
        }
        return PlayerInfo(
            title = details?.optString("title")?.takeIf { it.isNotEmpty() },
            lengthSeconds = details?.optString("lengthSeconds")?.toIntOrNull() ?: 0,
            captionTracks = tracks,
            status = navString(resp, "playabilityStatus", "status"),
        )
    }

    private suspend fun fetchWatchHtml(videoId: String): String {
        val html = http.get("$BASE/watch?v=$videoId&hl=en&gl=US")
        absorbSession(html)
        return html
    }

    /** Parses "Most replayed" data from a /next response or ytInitialData. */
    private fun parseHeat(root: Any): List<HeatMarker> {
        // Newer layout: frameworkUpdates -> macroMarkersListEntity -> markersList
        for (entity in collectByKey(root, "macroMarkersListEntity")) {
            val list = entity.optJSONObject("markersList") ?: continue
            if (!list.optString("markerType").contains("HEATMAP")) continue
            val markers = list.optJSONArray("markers") ?: continue
            val out = ArrayList<HeatMarker>()
            for (i in 0 until markers.length()) {
                val m = markers.optJSONObject(i) ?: continue
                out.add(
                    HeatMarker(
                        startSec = m.optDouble("startMillis", 0.0) / 1000.0,
                        durationSec = m.optDouble("durationMillis", 0.0) / 1000.0,
                        intensity = m.optDouble("intensityScoreNormalized", 0.0),
                    )
                )
            }
            if (out.isNotEmpty()) return out.sortedBy { it.startSec }
        }
        // Older layout: heatMarkerRenderer entries in the player bar
        val old = collectByKey(root, "heatMarkerRenderer")
        if (old.isNotEmpty()) {
            return old.map {
                HeatMarker(
                    startSec = it.optDouble("timeRangeStartMillis", 0.0) / 1000.0,
                    durationSec = it.optDouble("markerDurationMillis", 0.0) / 1000.0,
                    intensity = it.optDouble("heatMarkerIntensityScoreNormalized", 0.0),
                )
            }.sortedBy { it.startSec }
        }
        return emptyList()
    }

    private fun findCommentsToken(next: JSONObject): String? {
        for (section in collectByKey(next, "itemSectionRenderer")) {
            if (section.optString("sectionIdentifier") != "comment-item-section") continue
            for (c in collectByKey(section, "continuationItemRenderer")) {
                continuationToken(c)?.let { return it }
            }
        }
        val panels = next.optJSONArray("engagementPanels") ?: return null
        for (i in 0 until panels.length()) {
            val panel = navigate(panels, i, "engagementPanelSectionListRenderer") as? JSONObject ?: continue
            if (!panel.optString("panelIdentifier").contains("comment")) continue
            for (c in collectByKey(panel.opt("content"), "continuationItemRenderer")) {
                continuationToken(c)?.let { return it }
            }
        }
        return null
    }

    private suspend fun fetchComments(firstToken: String, maxPages: Int): List<RawComment> {
        val out = ArrayList<RawComment>()
        var token: String? = firstToken
        var pages = 0
        while (token != null && pages < maxPages) {
            val resp = innertube("next", JSONObject().put("continuation", token))
            pages++
            val (comments, next) = parseCommentPage(resp)
            out.addAll(comments)
            token = if (next == token) null else next
        }
        return out
    }

    private fun parseCommentPage(resp: JSONObject): Pair<List<RawComment>, String?> {
        val items = ArrayList<JSONObject>()
        val endpoints = resp.optJSONArray("onResponseReceivedEndpoints")
            ?: resp.optJSONArray("onResponseReceivedActions")
        if (endpoints != null) {
            for (i in 0 until endpoints.length()) {
                val e = endpoints.optJSONObject(i) ?: continue
                val arr = (navigate(e, "reloadContinuationItemsCommand", "continuationItems")
                    ?: navigate(e, "appendContinuationItemsAction", "continuationItems")) as? JSONArray
                    ?: continue
                for (j in 0 until arr.length()) {
                    val item = arr.optJSONObject(j)
                    if (item != null) items.add(item)
                }
            }
        }
        var next: String? = null
        val comments = ArrayList<RawComment>()
        for (item in items) {
            val cont = item.optJSONObject("continuationItemRenderer")
            if (cont != null) {
                val t = continuationToken(cont)
                if (t != null) next = t
            }
            val thread = item.optJSONObject("commentThreadRenderer") ?: continue
            val renderer = navigate(thread, "comment", "commentRenderer") as? JSONObject ?: continue
            val text = textOf(renderer.opt("contentText"))
            if (!text.isNullOrBlank()) {
                comments.add(RawComment(text, parseCount(textOf(renderer.opt("voteCount")))))
            }
        }
        if (comments.isEmpty()) {
            // Newer layout: the comment bodies live in frameworkUpdates entity payloads.
            val source: Any = resp.optJSONObject("frameworkUpdates") ?: resp
            for (p in collectByKey(source, "commentEntityPayload")) {
                val text = navString(p, "properties", "content", "content")
                if (!text.isNullOrBlank()) {
                    comments.add(RawComment(text, parseCount(navString(p, "toolbar", "likeCountNotliked"))))
                }
            }
        }
        return comments to next
    }

    private suspend fun fetchCaptions(track: CaptionTrack): List<CaptionLine> {
        val absolute = if (track.baseUrl.startsWith("http")) track.baseUrl else BASE + track.baseUrl
        val base = absolute.replace(Regex("&fmt=[^&]*"), "")
        val json3 = safe { parseJson3(http.get("$base&fmt=json3")) }.orEmpty()
        if (json3.isNotEmpty()) return json3
        return safe { parseXmlCaptions(http.get(base)) }.orEmpty()
    }

    private fun parseJson3(body: String): List<CaptionLine> {
        if (body.isBlank()) return emptyList()
        val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
        val out = ArrayList<CaptionLine>()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val segs = e.optJSONArray("segs") ?: continue
            val sb = StringBuilder()
            for (j in 0 until segs.length()) sb.append(segs.optJSONObject(j)?.optString("utf8").orEmpty())
            val text = sb.toString().replace('\n', ' ').trim()
            if (text.isEmpty()) continue
            out.add(
                CaptionLine(
                    startSec = e.optDouble("tStartMs", 0.0) / 1000.0,
                    durationSec = e.optDouble("dDurationMs", 0.0) / 1000.0,
                    text = text,
                )
            )
        }
        return out
    }

    private fun parseXmlCaptions(body: String): List<CaptionLine> {
        val out = ArrayList<CaptionLine>()
        val srv1 = Regex("<text start=\"([\\d.]+)\"(?: dur=\"([\\d.]+)\")?[^>]*>(.*?)</text>", RegexOption.DOT_MATCHES_ALL)
        for (m in srv1.findAll(body)) {
            val text = unescapeXml(m.groupValues[3]).replace('\n', ' ').trim()
            if (text.isEmpty()) continue
            out.add(CaptionLine(m.groupValues[1].toDoubleOrNull() ?: 0.0, m.groupValues[2].toDoubleOrNull() ?: 0.0, text))
        }
        if (out.isNotEmpty()) return out
        val srv3 = Regex("<p t=\"(\\d+)\"(?: d=\"(\\d+)\")?[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        for (m in srv3.findAll(body)) {
            val text = unescapeXml(m.groupValues[3].replace(Regex("<[^>]+>"), "")).replace('\n', ' ').trim()
            if (text.isEmpty()) continue
            out.add(
                CaptionLine(
                    (m.groupValues[1].toDoubleOrNull() ?: 0.0) / 1000.0,
                    (m.groupValues[2].toDoubleOrNull() ?: 0.0) / 1000.0,
                    text
                )
            )
        }
        return out
    }

    private fun unescapeXml(s: String): String {
        var r = s.replace("&amp;", "&")
        r = r.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        r = Regex("&#(\\d+);").replace(r) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else ""
        }
        return r
    }

    private fun pickTrack(tracks: List<CaptionTrack>): CaptionTrack? =
        tracks.firstOrNull { !it.isAuto && it.languageCode.startsWith("en") }
            ?: tracks.firstOrNull { it.isAuto && it.languageCode.startsWith("en") }
            ?: tracks.firstOrNull { !it.isAuto }
            ?: tracks.firstOrNull()

    /**
     * Gathers every signal for one video. Each signal fails on its own: a missing
     * heatmap or disabled comments never stops the others.
     */
    suspend fun gatherSignals(
        videoId: String,
        knownLengthSeconds: Int?,
        commentPages: Int,
        onStep: (String) -> Unit,
    ): VideoSignals = coroutineScope {
        onStep("Reading video info…")
        val playerJob = async { safe { fetchPlayer(videoId) } }
        val nextJob = async { safe { innertube("next", JSONObject().put("videoId", videoId)) } }
        var player: PlayerInfo? = playerJob.await()
        var next: JSONObject? = nextJob.await()
        var heat: List<HeatMarker> = if (next != null) parseHeat(next) else emptyList()

        val playerMissing = player == null || player.captionTracks.isEmpty()
        if (heat.isEmpty() || playerMissing || next == null) {
            onStep("Checking the watch page…")
            val html = safe { fetchWatchHtml(videoId) }
            if (html != null) {
                val initialData = extractJsonObject(html, "ytInitialData")
                if (initialData != null) {
                    if (heat.isEmpty()) heat = parseHeat(initialData)
                    if (next == null) next = initialData
                }
                val pr = extractJsonObject(html, "ytInitialPlayerResponse")
                if (pr != null) {
                    val fromPage = parsePlayer(pr)
                    val current = player
                    if (current == null || (current.captionTracks.isEmpty() && fromPage.captionTracks.isNotEmpty())) {
                        player = fromPage
                    }
                }
            }
        }

        if (player == null && next == null) {
            throw YouTubeException("Couldn't load this video from YouTube.")
        }

        val finalPlayer = player
        val duration = finalPlayer?.lengthSeconds?.takeIf { it > 0 }
            ?: knownLengthSeconds
            ?: heat.lastOrNull()?.let { (it.startSec + it.durationSec).toInt() }
            ?: 0

        val heatNote = if (heat.isNotEmpty()) {
            "Found ${heat.size} replay segments."
        } else {
            "Not available. Short or new videos often don't have a \"Most replayed\" graph yet."
        }

        // Captions
        var captions: List<CaptionLine> = emptyList()
        val captionNote: String
        val track = finalPlayer?.let { pickTrack(it.captionTracks) }
        if (track == null) {
            captionNote = if (finalPlayer == null) "Couldn't read caption info." else "No captions on this video."
        } else {
            onStep("Reading captions…")
            captions = fetchCaptions(track)
            val kind = if (track.isAuto) "auto" else "uploaded"
            captionNote = if (captions.isNotEmpty()) {
                "${track.name} ($kind), ${captions.size} lines."
            } else {
                "A ${track.name} track exists but YouTube returned no text for it."
            }
        }

        // Comments
        var stamps: List<CommentStamp> = emptyList()
        var read = 0
        val commentNote: String
        val token = next?.let { findCommentsToken(it) }
        if (commentPages <= 0) {
            commentNote = "Comment scanning is turned off in Settings."
        } else if (token == null) {
            commentNote = "Comments are off or couldn't be loaded."
        } else {
            onStep("Reading comments…")
            val comments = safe { fetchComments(token, commentPages) }
            if (comments == null) {
                commentNote = "Couldn't load comments right now."
            } else {
                read = comments.size
                stamps = MomentFinder.extractStamps(comments, duration)
                commentNote = when {
                    comments.isEmpty() -> "No comments yet."
                    stamps.isEmpty() -> "Read $read comments; none mention a timestamp."
                    else -> "Read $read comments; ${stamps.size} timestamps."
                }
            }
        }

        VideoSignals(
            videoId = videoId,
            title = finalPlayer?.title,
            durationSec = duration,
            heat = heat,
            heatNote = heatNote,
            captions = captions,
            captionNote = captionNote,
            commentsRead = read,
            stamps = stamps,
            commentNote = commentNote,
        )
    }

    companion object {
        const val BASE = "https://www.youtube.com"
        const val DEFAULT_CLIENT_VERSION = "2.20261029.11.00"
        private val ITEM_KEYS = setOf(
            "videoRenderer",
            "gridVideoRenderer",
            "reelItemRenderer",
            "shortsLockupViewModel",
            "lockupViewModel",
            "continuationItemRenderer",
        )

        /** Turns user input into a youtube.com path: "@handle" or "channel/UC…". */
        fun channelPath(input: String): String {
            var s = input.trim()
            s = s.removePrefix("https://").removePrefix("http://")
            s = s.removePrefix("www.").removePrefix("m.").removePrefix("youtube.com/")
            s = s.trim('/')
            if (s.startsWith("channel/")) {
                return "channel/" + s.removePrefix("channel/").substringBefore('/')
            }
            s = s.substringBefore('/').substringBefore('?')
            if (s.startsWith("UC") && s.length == 24) return "channel/$s"
            if (!s.startsWith("@")) s = "@$s"
            return s
        }
    }
}

/** Runs [block] and returns null on any failure except coroutine cancellation. */
internal inline fun <T> safe(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
