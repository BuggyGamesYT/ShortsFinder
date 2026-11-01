package com.buggygamesyt.shortsfinder.ui

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.buggygamesyt.shortsfinder.R
import com.buggygamesyt.shortsfinder.data.ExportJob
import com.buggygamesyt.shortsfinder.data.Framing
import com.buggygamesyt.shortsfinder.data.MediaFiles
import com.buggygamesyt.shortsfinder.data.ShortSpec
import com.buggygamesyt.shortsfinder.data.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortEditorScreen(
    request: EditorRequest,
    sourceUri: Uri?,
    exportRunning: Boolean,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onExport: (ExportJob) -> Unit,
) {
    val context = LocalContext.current
    var info by remember(sourceUri) { mutableStateOf<SourceInfo?>(null) }
    var loadError by remember(sourceUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        try {
            info = withContext(Dispatchers.IO) { MediaFiles.probe(context, uri) }
        } catch (e: Exception) {
            loadError = e.message ?: "Couldn't open that video."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Make a Short", maxLines = 1)
                        Text(
                            request.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val loaded = info
            when {
                sourceUri == null -> PickFileEmptyState(onPickFile)
                loadError != null -> ProblemState(loadError ?: "", onPickFile)
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> EditorBody(
                    request = request,
                    info = loaded,
                    exportRunning = exportRunning,
                    onPickFile = onPickFile,
                    onExport = onExport,
                )
            }
        }
    }
}

@Composable
private fun PickFileEmptyState(onPickFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Pick the original recording or the downloaded video from your phone",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Shorts are cut from a video file you already have. The app doesn't download from YouTube. " +
                "Use the same video you uploaded so the moment times line up. The app remembers the file for next time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPickFile, shape = CircleShape) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pick video file")
        }
    }
}

@Composable
private fun ProblemState(message: String, onPickFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPickFile, shape = CircleShape) { Text("Pick another file") }
    }
}

private fun initialRange(request: EditorRequest, durationSec: Float): ClosedFloatingPointRange<Float> {
    val maxLen = ShortSpec.MAX_SECONDS.toFloat()
    val s = request.suggestedStartSec?.toFloat()
    val e = request.suggestedEndSec?.toFloat()
    if (s != null && e != null && s < durationSec - 1f) {
        val start = s.coerceIn(0f, durationSec)
        val end = e.coerceIn(start + 1f, minOf(durationSec, start + maxLen))
        return start..end
    }
    return 0f..minOf(durationSec, 30f)
}

/** Keeps the range between 1 s and 60 s long and inside the file, moving the other thumb if needed. */
private fun clampRange(
    new: ClosedFloatingPointRange<Float>,
    old: ClosedFloatingPointRange<Float>,
    durationSec: Float,
): ClosedFloatingPointRange<Float> {
    val maxLen = minOf(ShortSpec.MAX_SECONDS.toFloat(), durationSec)
    val minLen = minOf(ShortSpec.MIN_SECONDS.toFloat(), durationSec)
    var start = new.start.coerceIn(0f, durationSec)
    var end = new.endInclusive.coerceIn(0f, durationSec)
    val startMoved = start != old.start
    if (end - start > maxLen) {
        if (startMoved) end = start + maxLen else start = end - maxLen
    }
    if (end - start < minLen) {
        if (startMoved) start = end - minLen else end = start + minLen
    }
    if (start < 0f) { end -= start; start = 0f }
    if (end > durationSec) { start -= end - durationSec; end = durationSec }
    return start.coerceAtLeast(0f)..end
}

private fun clockTenths(sec: Float): String {
    val tenths = (sec * 10f).roundToInt().coerceAtLeast(0)
    return "${formatClock(tenths / 10)}.${tenths % 10}"
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorBody(
    request: EditorRequest,
    info: SourceInfo,
    exportRunning: Boolean,
    onPickFile: () -> Unit,
    onExport: (ExportJob) -> Unit,
) {
    val context = LocalContext.current
    val durationSec = info.durationMs / 1000f
    var range by remember(info.uri) { mutableStateOf(initialRange(request, durationSec)) }
    var framing by rememberSaveable { mutableStateOf(Framing.CROP) }
    var playing by remember { mutableStateOf(false) }
    val currentRange by rememberUpdatedState(range)

    val player = remember(info.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(info.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
            seekTo((range.start * 1000).toLong())
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Loop the preview inside the chosen range.
    LaunchedEffect(player) {
        while (true) {
            val r = currentRange
            val startMs = (r.start * 1000).toLong()
            val endMs = (r.endInclusive * 1000).toLong()
            val pos = player.currentPosition
            if (player.isPlaying && (pos >= endMs || pos < startMs - 400)) player.seekTo(startMs)
            delay(80)
        }
    }
    // Pause the preview while exporting, so the decoder is free for the export.
    LaunchedEffect(exportRunning) { if (exportRunning) player.pause() }

    val suggestedMismatch = request.suggestedEndSec != null && request.suggestedEndSec > durationSec + 1f

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 9:16 preview of what the Short will look like.
        Box(
            Modifier
                .height(400.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    when (framing) {
                        Framing.BARS -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
                        Framing.BLUR -> Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                            )
                        )
                        Framing.CROP -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
                    }
                ),
        ) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.preview_player, null, false) as PlayerView).also {
                        it.player = player
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = if (framing == Framing.CROP) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            FilledTonalIconButton(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        val pos = player.currentPosition
                        if (pos < (range.start * 1000).toLong() || pos >= (range.endInclusive * 1000).toLong()) {
                            player.seekTo((range.start * 1000).toLong())
                        }
                        player.play()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .size(56.dp),
            ) {
                if (playing) {
                    // Simple pause glyph (two bars); the core icon set has no Pause icon.
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.width(6.dp).height(20.dp).background(MaterialTheme.colorScheme.onSecondaryContainer, CircleShape))
                        Box(Modifier.width(6.dp).height(20.dp).background(MaterialTheme.colorScheme.onSecondaryContainer, CircleShape))
                    }
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play preview", modifier = Modifier.size(30.dp))
                }
            }
            if (framing == Framing.BLUR) {
                Text(
                    "Background is blurred in the export",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            info.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onPickFile) { Text("Use a different file") }

        AnimatedVisibility(visible = suggestedMismatch) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "This file is shorter than the YouTube video, so the suggested moment doesn't fit. " +
                            "Check it's the same video, or set the range by hand.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Trim
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .animateContentSize(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Trim", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val len = range.endInclusive - range.start
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "%.1fs / ${ShortSpec.MAX_SECONDS}s".format(len),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                var lastMovedStart by remember { mutableStateOf(true) }
                RangeSlider(
                    value = range,
                    onValueChange = { new ->
                        lastMovedStart = new.start != range.start
                        range = clampRange(new, range, durationSec)
                    },
                    valueRange = 0f..durationSec,
                    onValueChangeFinished = {
                        val target = if (lastMovedStart) range.start else (range.endInclusive - 2f).coerceAtLeast(range.start)
                        player.seekTo((target * 1000).toLong())
                    },
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("0:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatClock(durationSec.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                NudgeRow(
                    label = "Start",
                    value = range.start,
                    onNudge = { d ->
                        val n = (range.start + d)..range.endInclusive
                        range = clampRange(n, range, durationSec)
                        player.seekTo((range.start * 1000).toLong())
                    },
                    onSetHere = {
                        val here = player.currentPosition / 1000f
                        range = clampRange(here..range.endInclusive.coerceAtLeast(here + 1f), range, durationSec)
                        player.seekTo((range.start * 1000).toLong())
                    },
                )
                NudgeRow(
                    label = "End",
                    value = range.endInclusive,
                    onNudge = { d ->
                        val n = range.start..(range.endInclusive + d)
                        range = clampRange(n, range, durationSec)
                        player.seekTo(((range.endInclusive - 2f).coerceAtLeast(range.start) * 1000).toLong())
                    },
                    onSetHere = {
                        val here = player.currentPosition / 1000f
                        range = clampRange(range.start.coerceAtMost(here - 1f)..here, range, durationSec)
                    },
                )
            }
        }

        // Framing
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Framing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Framing.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = framing == f,
                            onClick = { framing = f },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = Framing.entries.size),
                            icon = {
                                if (framing == f) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        ) { Text(f.label, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    framing.blurb + " · 1080×1920 MP4 (H.264/AAC)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                player.pause()
                onExport(
                    ExportJob(
                        source = info.uri,
                        startMs = (range.start * 1000).toLong(),
                        endMs = (range.endInclusive * 1000).toLong(),
                        framing = framing,
                        displayName = shortFileName(request.title, range.start.toInt()),
                    )
                )
            },
            enabled = !exportRunning,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Export Short", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Saved to Movies/${ShortSpec.GALLERY_FOLDER}. You can share it right after.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NudgeRow(label: String, value: Float, onNudge: (Float) -> Unit, onSetHere: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(clockTenths(value), style = MaterialTheme.typography.titleSmall)
        }
        OutlinedButton(onClick = { onNudge(-1f) }, shape = CircleShape, contentPadding = SmallPadding) { Text("−1s") }
        OutlinedButton(onClick = { onNudge(1f) }, shape = CircleShape, contentPadding = SmallPadding) { Text("+1s") }
        FilledTonalButton(onClick = onSetHere, shape = CircleShape, contentPadding = SmallPadding) { Text("Here") }
    }
}

private val SmallPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
