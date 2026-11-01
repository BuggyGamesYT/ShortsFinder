package com.buggygamesyt.shortsfinder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buggygamesyt.shortsfinder.data.AppSettings
import com.buggygamesyt.shortsfinder.data.ChannelTab
import com.buggygamesyt.shortsfinder.data.ExportFailed
import com.buggygamesyt.shortsfinder.data.ExportJob
import com.buggygamesyt.shortsfinder.data.MediaFiles
import com.buggygamesyt.shortsfinder.data.ShortExporter
import com.buggygamesyt.shortsfinder.data.SourceStore
import com.buggygamesyt.shortsfinder.data.HttpException
import com.buggygamesyt.shortsfinder.data.SettingsStore
import com.buggygamesyt.shortsfinder.data.Video
import com.buggygamesyt.shortsfinder.data.VideoCache
import com.buggygamesyt.shortsfinder.data.VideoSignals
import com.buggygamesyt.shortsfinder.data.YouTubeClient
import com.buggygamesyt.shortsfinder.data.YouTubeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class ListState(
    val videos: List<Video> = emptyList(),
    val loading: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val lastUpdated: Long = 0L,
)

/** What the editor opens with: a YouTube moment (videoId set) or a plain file. */
data class EditorRequest(
    val videoId: String?,
    val title: String,
    val suggestedStartSec: Int?,
    val suggestedEndSec: Int?,
    val pickedUri: Uri? = null,
)

data class SavedShort(val uri: Uri, val name: String)

data class ExportState(
    val active: Boolean = false,
    val running: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
    val progress: Float = 0f,
    val saved: List<SavedShort> = emptyList(),
    val error: String? = null,
)

sealed interface MomentsState {
    data object Idle : MomentsState
    data class Loading(val step: String) : MomentsState
    data class Ready(val signals: VideoSignals) : MomentsState
    data class Failed(val message: String) : MomentsState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val yt = YouTubeClient()
    private val store = SettingsStore(app)
    private val cache = VideoCache(app)

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _list = MutableStateFlow(ListState())
    val list: StateFlow<ListState> = _list.asStateFlow()

    private val _selected = MutableStateFlow<Video?>(null)
    val selected: StateFlow<Video?> = _selected.asStateFlow()

    private val _moments = MutableStateFlow<MomentsState>(MomentsState.Idle)
    val moments: StateFlow<MomentsState> = _moments.asStateFlow()

    private val sources = SourceStore(app)
    private val exporter = ShortExporter(app)

    private val _editor = MutableStateFlow<EditorRequest?>(null)
    val editor: StateFlow<EditorRequest?> = _editor.asStateFlow()

    private val _export = MutableStateFlow(ExportState())
    val export: StateFlow<ExportState> = _export.asStateFlow()

    /** Bumped when a source file is picked or forgotten, so screens re-read [sourceFor]. */
    private val _sourcesVersion = MutableStateFlow(0)
    val sourcesVersion: StateFlow<Int> = _sourcesVersion.asStateFlow()

    private var loadJob: Job? = null
    private var analyzeJob: Job? = null
    private var exportJob: Job? = null

    init {
        loadCacheThenMaybeRefresh()
    }

    private fun loadCacheThenMaybeRefresh() {
        viewModelScope.launch {
            val s = _settings.value
            val cached = withContext(Dispatchers.IO) { cache.load(s.handle) }
            if (cached != null && cached.videos.isNotEmpty()) {
                _list.update { it.copy(videos = cached.videos, lastUpdated = cached.savedAt) }
            }
            val stale = cached == null ||
                cached.videos.isEmpty() ||
                cached.includesShorts != s.includeShorts ||
                System.currentTimeMillis() - cached.savedAt > STALE_AFTER_MS
            if (stale) refresh()
        }
    }

    /** Re-reads every upload on the channel, following continuation pages until the end. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val s = _settings.value
            val hadList = _list.value.videos.isNotEmpty()
            _list.update { it.copy(loading = true, error = null, progress = "Finding channel…") }
            val found = LinkedHashMap<String, Video>()
            try {
                val channelId = yt.resolveChannelId(s.handle)
                val tabs = if (s.includeShorts) listOf(ChannelTab.VIDEOS, ChannelTab.SHORTS) else listOf(ChannelTab.VIDEOS)
                for (tab in tabs) {
                    try {
                        loadTab(channelId, tab, found, hadList)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The Videos tab must work; a missing Shorts tab is fine.
                        if (tab == ChannelTab.VIDEOS) throw e
                    }
                }
                val all = found.values.toList()
                if (all.isEmpty()) throw YouTubeException("No videos found on ${s.handle}.")
                withContext(Dispatchers.IO) { cache.save(s.handle, s.includeShorts, all) }
                _list.update {
                    it.copy(
                        videos = all,
                        loading = false,
                        progress = null,
                        error = null,
                        lastUpdated = System.currentTimeMillis(),
                    )
                }
            } catch (e: CancellationException) {
                // Whoever cancelled (a settings change) resets the list state itself.
                throw e
            } catch (e: Exception) {
                val partial = found.values.toList()
                _list.update {
                    it.copy(
                        videos = if (it.videos.isEmpty() && partial.isNotEmpty()) partial else it.videos,
                        loading = false,
                        progress = null,
                        error = friendlyError(e),
                    )
                }
            }
        }
    }

    private suspend fun loadTab(
        channelId: String,
        tab: ChannelTab,
        found: LinkedHashMap<String, Video>,
        hadList: Boolean,
    ) {
        var token: String? = null
        var pages = 0
        do {
            val page = yt.fetchChannelTab(channelId, tab, token)
            pages++
            for (v in page.videos) if (!found.containsKey(v.id)) found[v.id] = v
            token = page.nextToken
            val count = found.size
            _list.update { st ->
                st.copy(
                    progress = "Loading ${tab.label.lowercase()}… $count found",
                    // First run: show videos as they arrive. Refresh: keep the old list until done.
                    videos = if (hadList) st.videos else found.values.toList(),
                )
            }
        } while (token != null && pages < MAX_PAGES_PER_TAB)
    }

    fun dismissError() {
        _list.update { it.copy(error = null) }
    }

    fun updateSettings(new: AppSettings) {
        val old = _settings.value
        val cleaned = new.copy(handle = new.handle.trim().ifEmpty { AppSettings.DEFAULT_HANDLE })
        _settings.value = cleaned
        store.save(cleaned)
        val handleChanged = !cleaned.handle.equals(old.handle, ignoreCase = true)
        if (handleChanged) {
            loadJob?.cancel()
            loadJob = null
            _list.value = ListState()
            loadCacheThenMaybeRefresh()
        } else if (cleaned.includeShorts != old.includeShorts) {
            loadJob?.cancel()
            loadJob = null
            refresh()
        }
    }

    fun openVideo(video: Video) {
        _selected.value = video
        analyze()
    }

    fun analyze() {
        val video = _selected.value ?: return
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _moments.value = MomentsState.Loading("Starting…")
            try {
                val signals = yt.gatherSignals(
                    videoId = video.id,
                    knownLengthSeconds = video.lengthSeconds,
                    commentPages = _settings.value.commentPages,
                    onStep = { step -> _moments.value = MomentsState.Loading(step) },
                )
                _moments.value = MomentsState.Ready(signals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _moments.value = MomentsState.Failed(friendlyError(e))
            }
        }
    }

    // ---- Shorts maker ----

    fun sourceFor(videoId: String?): Uri? = videoId?.let { sources.get(it) }

    /** Remembers a picked file for a video (keeps read access across restarts). */
    fun rememberSource(videoId: String?, uri: Uri) {
        MediaFiles.keepAccess(getApplication<Application>(), uri)
        if (videoId != null) sources.put(videoId, uri)
        _editor.update { e -> if (e != null && e.videoId == videoId) e.copy(pickedUri = uri) else e }
        _sourcesVersion.update { it + 1 }
    }

    fun forgetSource(videoId: String?) {
        if (videoId != null) sources.forget(videoId)
        _editor.update { e -> if (e != null && e.videoId == videoId) e.copy(pickedUri = null) else e }
        _sourcesVersion.update { it + 1 }
    }

    fun openEditor(request: EditorRequest) {
        _editor.value = request
    }

    fun closeEditor() {
        _editor.value = null
    }

    /** Exports one or more Shorts in a row, saving each to Movies/YT Shorts Maker. */
    fun exportShorts(jobs: List<ExportJob>) {
        if (jobs.isEmpty() || exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            _export.value = ExportState(active = true, running = true, index = 0, total = jobs.size)
            val ctx = getApplication<Application>()
            val saved = ArrayList<SavedShort>()
            try {
                for ((i, job) in jobs.withIndex()) {
                    _export.update { it.copy(index = i, progress = 0f) }
                    val info = withContext(Dispatchers.IO) { MediaFiles.probe(ctx, job.source) }
                    val end = job.endMs.coerceAtMost(info.durationMs)
                    val start = job.startMs.coerceIn(0L, (end - 500L).coerceAtLeast(0L))
                    if (end - start < 500L) throw ExportFailed("That clip is outside the picked file. Check the file and the range.")
                    val file = exporter.export(job.copy(startMs = start, endMs = end), info) { p ->
                        _export.update { it.copy(progress = p) }
                    }
                    try {
                        val uri = withContext(Dispatchers.IO) { MediaFiles.saveToGallery(ctx, file, job.displayName) }
                        saved.add(SavedShort(uri, job.displayName))
                        _export.update { it.copy(saved = saved.toList(), progress = 1f) }
                    } finally {
                        withContext(Dispatchers.IO) { file.delete() }
                    }
                }
                _export.update { it.copy(running = false, index = jobs.size) }
            } catch (e: CancellationException) {
                _export.update { it.copy(running = false, error = if (saved.isEmpty()) "Export cancelled." else "Export stopped.") }
                throw e
            } catch (e: Exception) {
                _export.update { it.copy(running = false, error = e.message ?: "Export failed.") }
            }
        }
    }

    fun cancelExport() {
        exporter.cancel()
        exportJob?.cancel()
        exportJob = null
        _export.update { it.copy(running = false, error = it.error ?: "Export cancelled.") }
    }

    fun dismissExport() {
        if (_export.value.running) return
        _export.value = ExportState()
    }

    override fun onCleared() {
        exporter.cancel()
        super.onCleared()
    }

    companion object {
        private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
        private const val MAX_PAGES_PER_TAB = 400

        fun friendlyError(e: Throwable): String = when (e) {
            is UnknownHostException, is ConnectException ->
                "Can't reach YouTube. Check your connection. On GrapheneOS, make sure the " +
                    "Network permission is allowed for YT Shorts Maker."
            is SocketTimeoutException -> "YouTube took too long to answer."
            is SSLException -> "Secure connection to YouTube failed."
            is HttpException -> when (e.code) {
                429 -> "YouTube is rate-limiting requests right now. Wait a minute and retry."
                403 -> "YouTube refused the request (403)."
                else -> "YouTube answered with an error (${e.code})."
            }
            is YouTubeException -> e.message ?: "YouTube sent something unexpected."
            else -> e.message ?: "Something went wrong."
        }
    }
}
