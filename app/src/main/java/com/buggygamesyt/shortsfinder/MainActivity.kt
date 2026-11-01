package com.buggygamesyt.shortsfinder

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buggygamesyt.shortsfinder.data.ExportJob
import com.buggygamesyt.shortsfinder.data.Framing
import com.buggygamesyt.shortsfinder.data.MediaFiles
import com.buggygamesyt.shortsfinder.data.Moment
import com.buggygamesyt.shortsfinder.ui.AppViewModel
import com.buggygamesyt.shortsfinder.ui.EditorRequest
import com.buggygamesyt.shortsfinder.ui.ExportDialog
import com.buggygamesyt.shortsfinder.ui.MomentsScreen
import com.buggygamesyt.shortsfinder.ui.SettingsScreen
import com.buggygamesyt.shortsfinder.ui.ShortEditorScreen
import com.buggygamesyt.shortsfinder.ui.VideoListScreen
import com.buggygamesyt.shortsfinder.ui.shortFileName
import com.buggygamesyt.shortsfinder.ui.theme.YTShortsMakerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent system bars; icon colors follow the system light/dark setting.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YTShortsMakerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    AppRoot()
                }
            }
        }
    }
}

enum class Screen { LIST, MOMENTS, EDITOR, SETTINGS }

/** What to do once the file picker returns. */
private enum class PickPurpose { EDITOR, FREE_FILE, MOMENT_SOURCE, TOP_BATCH }

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val list by vm.list.collectAsState()
    val settings by vm.settings.collectAsState()
    val selected by vm.selected.collectAsState()
    val moments by vm.moments.collectAsState()
    val editor by vm.editor.collectAsState()
    val export by vm.export.collectAsState()
    val sourcesVersion by vm.sourcesVersion.collectAsState()

    var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.LIST) }
    var editorReturn by rememberSaveable { mutableStateOf(Screen.LIST) }

    // Pending work that needs a picked file or a permission first.
    var pickPurpose by remember { mutableStateOf(PickPurpose.EDITOR) }
    var pendingBatch by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var pendingExport by remember { mutableStateOf<List<ExportJob>>(emptyList()) }

    val video = selected
    val currentSource: Uri? = remember(video?.id, sourcesVersion) { vm.sourceFor(video?.id) }

    fun batchJobs(uri: Uri, picks: List<Moment>): List<ExportJob> {
        val title = video?.title ?: "clip"
        return picks.map { m ->
            ExportJob(
                source = uri,
                startMs = m.clipStart * 1000L,
                endMs = m.clipEnd * 1000L,
                framing = Framing.CROP,
                displayName = shortFileName("$title #${m.rank}", m.clipStart),
            )
        }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val jobs = pendingExport
        pendingExport = emptyList()
        if (granted) {
            vm.exportShorts(jobs)
        } else {
            Toast.makeText(context, "Storage access is needed to save into Movies.", Toast.LENGTH_LONG).show()
        }
    }

    fun startExport(jobs: List<ExportJob>) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingExport = jobs
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.exportShorts(jobs)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (pickPurpose) {
            PickPurpose.FREE_FILE -> {
                MediaFiles.keepAccess(context, uri)
                vm.openEditor(
                    EditorRequest(
                        videoId = null,
                        title = MediaFiles.displayName(context, uri).substringBeforeLast('.'),
                        suggestedStartSec = null,
                        suggestedEndSec = null,
                        pickedUri = uri,
                    )
                )
                editorReturn = Screen.LIST
                screen = Screen.EDITOR
            }
            PickPurpose.EDITOR -> {
                val e = editor
                if (e != null && e.videoId == null) {
                    MediaFiles.keepAccess(context, uri)
                    vm.openEditor(e.copy(pickedUri = uri))
                } else {
                    vm.rememberSource(e?.videoId, uri)
                }
            }
            PickPurpose.MOMENT_SOURCE -> vm.rememberSource(video?.id, uri)
            PickPurpose.TOP_BATCH -> {
                vm.rememberSource(video?.id, uri)
                val picks = pendingBatch
                pendingBatch = emptyList()
                if (picks.isNotEmpty()) startExport(batchJobs(uri, picks))
            }
        }
    }

    fun pick(purpose: PickPurpose) {
        pickPurpose = purpose
        try {
            picker.launch(arrayOf("video/*"))
        } catch (e: Exception) {
            Toast.makeText(context, "No file picker found on this phone.", Toast.LENGTH_LONG).show()
        }
    }

    // After process death the selected video or editor request is gone; fall back to the list.
    val current = when {
        screen == Screen.MOMENTS && video == null -> Screen.LIST
        screen == Screen.EDITOR && editor == null -> if (video != null) Screen.MOMENTS else Screen.LIST
        else -> screen
    }

    BackHandler(enabled = current != Screen.LIST) {
        screen = when (current) {
            Screen.SETTINGS -> settingsReturn
            Screen.EDITOR -> {
                vm.closeEditor()
                editorReturn
            }
            else -> Screen.LIST
        }
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.LIST -> VideoListScreen(
                state = list,
                settings = settings,
                onRefresh = vm::refresh,
                onDismissError = vm::dismissError,
                onOpen = { v ->
                    vm.openVideo(v)
                    screen = Screen.MOMENTS
                },
                onSettings = {
                    settingsReturn = Screen.LIST
                    screen = Screen.SETTINGS
                },
                onMakeFromFile = { pick(PickPurpose.FREE_FILE) },
            )

            Screen.MOMENTS -> if (video != null) {
                MomentsScreen(
                    video = video,
                    state = moments,
                    settings = settings,
                    sourceUri = currentSource,
                    onBack = { screen = Screen.LIST },
                    onRetry = vm::analyze,
                    onSettings = {
                        settingsReturn = Screen.MOMENTS
                        screen = Screen.SETTINGS
                    },
                    onMakeShort = { m ->
                        vm.openEditor(
                            EditorRequest(
                                videoId = video.id,
                                title = "${video.title} #${m.rank}",
                                suggestedStartSec = m.clipStart,
                                suggestedEndSec = m.clipEnd,
                            )
                        )
                        editorReturn = Screen.MOMENTS
                        screen = Screen.EDITOR
                    },
                    onMakeTop = { picks ->
                        val src = currentSource
                        if (src != null) {
                            startExport(batchJobs(src, picks))
                        } else {
                            pendingBatch = picks
                            pick(PickPurpose.TOP_BATCH)
                        }
                    },
                    onPickSource = { pick(PickPurpose.MOMENT_SOURCE) },
                )
            }

            Screen.EDITOR -> {
                val req = editor
                if (req != null) {
                    val src = remember(req.videoId, req.pickedUri, sourcesVersion) {
                        req.pickedUri ?: vm.sourceFor(req.videoId)
                    }
                    ShortEditorScreen(
                        request = req,
                        sourceUri = src,
                        exportRunning = export.running,
                        onBack = {
                            vm.closeEditor()
                            screen = editorReturn
                        },
                        onPickFile = { pick(PickPurpose.EDITOR) },
                        onExport = { job -> startExport(listOf(job)) },
                    )
                }
            }

            Screen.SETTINGS -> SettingsScreen(
                initial = settings,
                onSave = { s ->
                    vm.updateSettings(s)
                    screen = settingsReturn
                },
                onBack = { screen = settingsReturn },
            )
        }
    }

    ExportDialog(state = export, onCancel = vm::cancelExport, onDismiss = vm::dismissExport)
}
