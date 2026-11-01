package com.buggygamesyt.shortsfinder.data

import org.json.JSONObject

data class Video(
    val id: String,
    val title: String,
    val views: String?,
    val length: String?,
    val lengthSeconds: Int?,
    val published: String?,
    val isShort: Boolean,
) {
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    val watchUrl: String get() = if (isShort) "https://www.youtube.com/shorts/$id" else "https://youtu.be/$id"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("views", views ?: JSONObject.NULL)
        .put("length", length ?: JSONObject.NULL)
        .put("lengthSeconds", lengthSeconds ?: JSONObject.NULL)
        .put("published", published ?: JSONObject.NULL)
        .put("isShort", isShort)

    companion object {
        fun fromJson(o: JSONObject): Video? {
            val id = o.optString("id")
            if (id.isEmpty()) return null
            return Video(
                id = id,
                title = o.optString("title"),
                views = o.optNullableString("views"),
                length = o.optNullableString("length"),
                lengthSeconds = if (o.isNull("lengthSeconds")) null else o.optInt("lengthSeconds"),
                published = o.optNullableString("published"),
                isShort = o.optBoolean("isShort"),
            )
        }
    }
}

internal fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

/** One "most replayed" segment. Intensity is 0..1 as YouTube reports it. */
data class HeatMarker(val startSec: Double, val durationSec: Double, val intensity: Double)

data class CaptionLine(val startSec: Double, val durationSec: Double, val text: String)

data class CaptionTrack(
    val baseUrl: String,
    val languageCode: String,
    val name: String,
    val isAuto: Boolean,
)

data class RawComment(val text: String, val likes: Int)

/** A timestamp found in a comment. Weight grows slowly with the comment's likes. */
data class CommentStamp(val seconds: Int, val weight: Double, val text: String)

/** Everything gathered for one video, plus a short status line per signal. */
data class VideoSignals(
    val videoId: String,
    val title: String?,
    val durationSec: Int,
    val heat: List<HeatMarker>,
    val heatNote: String,
    val captions: List<CaptionLine>,
    val captionNote: String,
    val commentsRead: Int,
    val stamps: List<CommentStamp>,
    val commentNote: String,
)
