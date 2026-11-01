package com.buggygamesyt.shortsfinder.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppSettings(
    val handle: String = DEFAULT_HANDLE,
    val includeShorts: Boolean = true,
    val weightReplay: Float = 1.0f,
    val weightReaction: Float = 0.7f,
    val weightComments: Float = 0.9f,
    val clipLength: Int = 30,
    val leadSeconds: Int = 3,
    val commentPages: Int = 5,
) {
    fun finderConfig(): MomentFinder.Config = MomentFinder.Config(
        weightReplay = weightReplay,
        weightReaction = weightReaction,
        weightComments = weightComments,
        clipLength = clipLength,
        leadSeconds = leadSeconds,
    )

    companion object {
        const val DEFAULT_HANDLE = "@BuggyGamesYT"
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            handle = prefs.getString("handle", d.handle)?.takeIf { it.isNotBlank() } ?: d.handle,
            includeShorts = prefs.getBoolean("includeShorts", d.includeShorts),
            weightReplay = prefs.getFloat("weightReplay", d.weightReplay),
            weightReaction = prefs.getFloat("weightReaction", d.weightReaction),
            weightComments = prefs.getFloat("weightComments", d.weightComments),
            clipLength = prefs.getInt("clipLength", d.clipLength),
            leadSeconds = prefs.getInt("leadSeconds", d.leadSeconds),
            commentPages = prefs.getInt("commentPages", d.commentPages),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("handle", s.handle)
            .putBoolean("includeShorts", s.includeShorts)
            .putFloat("weightReplay", s.weightReplay)
            .putFloat("weightReaction", s.weightReaction)
            .putFloat("weightComments", s.weightComments)
            .putInt("clipLength", s.clipLength)
            .putInt("leadSeconds", s.leadSeconds)
            .putInt("commentPages", s.commentPages)
            .apply()
    }
}

data class CachedList(val savedAt: Long, val includesShorts: Boolean, val videos: List<Video>)

/** Keeps the last full video list on the device so the app opens instantly. */
class VideoCache(context: Context) {
    private val dir: File = context.applicationContext.filesDir

    private fun file(handle: String): File {
        val safeName = handle.lowercase().replace(Regex("[^a-z0-9_@.-]"), "_")
        return File(dir, "videos_$safeName.json")
    }

    fun load(handle: String): CachedList? = try {
        val f = file(handle)
        if (!f.exists()) {
            null
        } else {
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("videos") ?: JSONArray()
            val videos = ArrayList<Video>(arr.length())
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i)?.let { Video.fromJson(it) }
                if (v != null) videos.add(v)
            }
            CachedList(o.optLong("savedAt"), o.optBoolean("includesShorts"), videos)
        }
    } catch (e: Exception) {
        null
    }

    fun save(handle: String, includesShorts: Boolean, videos: List<Video>) {
        try {
            val arr = JSONArray()
            for (v in videos) arr.put(v.toJson())
            val o = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("includesShorts", includesShorts)
                .put("videos", arr)
            val target = file(handle)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(target)) {
                target.writeText(o.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
            // Cache is a convenience; ignore write failures.
        }
    }

    fun clear(handle: String) {
        file(handle).delete()
    }
}
