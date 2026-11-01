package com.buggygamesyt.shortsfinder.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How a wide video is fitted into the 9:16 Short. */
enum class Framing(val label: String, val blurb: String) {
    CROP("Crop", "Fill the screen, cut the sides"),
    BLUR("Blur bars", "Whole frame, blurred background"),
    BARS("Black bars", "Whole frame, black background"),
}

/** One Short to export. Times are in the source file's timeline. */
data class ExportJob(
    val source: Uri,
    val startMs: Long,
    val endMs: Long,
    val framing: Framing,
    val displayName: String,
)

/** What we know about a picked file. Width/height are as displayed (rotation applied). */
data class SourceInfo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
)

class ExportFailed(message: String, cause: Throwable? = null) : Exception(message, cause)

object ShortSpec {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val MAX_SECONDS = 60
    const val MIN_SECONDS = 1
    const val GALLERY_FOLDER = "YT Shorts Maker"
}

/** Remembers which file on the phone belongs to which YouTube video. */
class SourceStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("sources", Context.MODE_PRIVATE)

    fun get(videoId: String): Uri? {
        val s = prefs.getString(videoId, null) ?: return null
        val uri = Uri.parse(s)
        // Only hand back files we can still read.
        val stillAllowed = app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return if (stillAllowed) uri else null
    }

    fun put(videoId: String, uri: Uri) {
        prefs.edit().putString(videoId, uri.toString()).apply()
    }

    fun forget(videoId: String) {
        prefs.edit().remove(videoId).apply()
    }
}

object MediaFiles {

    /** Keeps read access to a picked document across restarts. Safe to call on any Uri. */
    fun keepAccess(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some pickers don't offer persistable access; it still works for this session.
        }
    }

    fun displayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "video"
    }

    /** Reads length and size of a video. Call off the main thread. */
    fun probe(context: Context, uri: Uri): SourceInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w; w = h; h = t
            }
            if (duration <= 0L) throw ExportFailed("That file doesn't look like a video the phone can read.")
            return SourceInfo(uri, displayName(context, uri), duration, w, h)
        } catch (e: ExportFailed) {
            throw e
        } catch (e: SecurityException) {
            throw ExportFailed("The app lost access to that file. Pick it again.", e)
        } catch (e: Exception) {
            throw ExportFailed("Couldn't open that video. It may have been moved or deleted.", e)
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Copies a finished Short into Movies/YT Shorts Maker and returns its content Uri.
     * Call off the main thread.
     */
    fun saveToGallery(context: Context, file: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val name = if (displayName.endsWith(".mp4", ignoreCase = true)) displayName else "$displayName.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/${ShortSpec.GALLERY_FOLDER}")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw ExportFailed("Couldn't create the file in Movies.")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { it.copyTo(out) }
                } ?: throw ExportFailed("Couldn't write the file in Movies.")
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                return uri
            } catch (e: Exception) {
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                throw if (e is ExportFailed) e else ExportFailed("Couldn't save the Short to Movies.", e)
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ShortSpec.GALLERY_FOLDER)
            if (!dir.exists() && !dir.mkdirs()) throw ExportFailed("Couldn't create Movies/${ShortSpec.GALLERY_FOLDER}. Allow storage access and try again.")
            var target = File(dir, name)
            var n = 2
            while (target.exists()) {
                target = File(dir, name.removeSuffix(".mp4") + " ($n).mp4")
                n++
            }
            file.copyTo(target)
            val values = ContentValues().apply {
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, target.absolutePath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            }
            val uri = try {
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            } catch (_: Exception) {
                null
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("video/mp4"), null)
            return uri ?: Uri.fromFile(target)
        }
    }
}

/**
 * Trims and reframes one clip into a 1080x1920 H.264/AAC MP4 with Media3 Transformer.
 * Must be called from the main thread: the Transformer lives on the main Looper.
 */
@OptIn(UnstableApi::class)
class ShortExporter(context: Context) {
    private val app = context.applicationContext
    private var current: Transformer? = null

    suspend fun export(job: ExportJob, info: SourceInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.Main) {
            val dir = File(app.cacheDir, "exports").apply { mkdirs() }
            val out = File(dir, "short_${System.currentTimeMillis()}.mp4")
            if (out.exists()) out.delete()

            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(job.startMs)
                .setEndPositionMs(job.endMs)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(job.source)
                .setClippingConfiguration(clipping)
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(listOf(), videoEffects(job.framing, info)))
                .build()

            coroutineScope {
                var transformer: Transformer? = null
                val poller = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        val t = transformer
                        if (t != null && t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress / 100f)
                        }
                        delay(200)
                    }
                }
                try {
                    suspendCancellableCoroutine<File> { cont ->
                        val t = Transformer.Builder(app)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (cont.isActive) cont.resume(out)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    out.delete()
                                    if (cont.isActive) {
                                        cont.resumeWithException(ExportFailed(friendly(exportException), exportException))
                                    }
                                }
                            })
                            .build()
                        transformer = t
                        current = t
                        cont.invokeOnCancellation {
                            // May run on any thread; the Transformer must be touched on main.
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    t.cancel()
                                } catch (_: Exception) {
                                }
                                out.delete()
                            }
                        }
                        try {
                            t.start(edited, out.absolutePath)
                        } catch (e: Exception) {
                            if (cont.isActive) {
                                cont.resumeWithException(ExportFailed("Couldn't start the export: ${e.message}", e))
                            }
                        }
                    }
                } finally {
                    poller.cancel()
                    current = null
                }
            }
        }

    fun cancel() {
        try {
            current?.cancel()
        } catch (_: Exception) {
        }
        current = null
    }

    private fun videoEffects(framing: Framing, info: SourceInfo): List<Effect> {
        val w = ShortSpec.WIDTH
        val h = ShortSpec.HEIGHT
        return when (framing) {
            Framing.CROP -> listOf(
                Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            )
            Framing.BARS -> listOf(
                Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)
            )
            Framing.BLUR -> {
                val srcAspect = if (info.width > 0 && info.height > 0) info.width.toFloat() / info.height else 16f / 9f
                val outAspect = w.toFloat() / h
                if (srcAspect <= outAspect) {
                    // Already as tall as a Short: cropping loses nothing worth blurring.
                    listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                } else {
                    // Stretch to 9:16, blur it as the background, and overlay the frame
                    // scaled back to its real shape in the middle.
                    val scaleY = outAspect / srcAspect
                    listOf(
                        Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT),
                        GaussianBlurWithFrameOverlaid(30f, 1f, scaleY),
                    )
                }
            }
        }
    }

    private fun friendly(e: ExportException): String = when (e.errorCode) {
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
        ExportException.ERROR_CODE_IO_NO_PERMISSION -> "The app can't read that video anymore. Pick the file again."
        ExportException.ERROR_CODE_DECODER_INIT_FAILED,
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This phone can't decode that video format."
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> "This phone's encoder couldn't make a 1080x1920 H.264 video."
        else -> "Export failed: ${e.errorCodeName}"
    }
}
