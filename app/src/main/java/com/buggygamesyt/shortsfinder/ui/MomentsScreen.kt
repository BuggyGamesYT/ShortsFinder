package com.buggygamesyt.shortsfinder.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buggygamesyt.shortsfinder.data.AppSettings
import com.buggygamesyt.shortsfinder.data.Moment
import com.buggygamesyt.shortsfinder.data.MomentFinder
import com.buggygamesyt.shortsfinder.data.Reason
import com.buggygamesyt.shortsfinder.data.Video
import com.buggygamesyt.shortsfinder.data.VideoSignals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    video: Video,
    state: MomentsState,
    settings: AppSettings,
    sourceUri: Uri?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onMakeShort: (Moment) -> Unit,
    onMakeTop: (List<Moment>) -> Unit,
    onPickSource: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        video.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRetry, enabled = state !is MomentsState.Loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Analyze again")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (state) {
                is MomentsState.Idle, is MomentsState.Loading -> {
                    val step = (state as? MomentsState.Loading)?.step ?: "Starting…"
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "thumb") { VideoHeaderImage(video) }
                        item(key = "step") {
                            Text(step, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        items(4, key = { "ph$it" }) { MomentPlaceholder() }
                    }
                }
                is MomentsState.Failed -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRetry, shape = CircleShape) { Text("Retry") }
                    }
                }
                is MomentsState.Ready -> ResultsList(
                    video = video,
                    signals = state.signals,
                    settings = settings,
                    sourceUri = sourceUri,
                    onMakeShort = onMakeShort,
                    onMakeTop = onMakeTop,
                    onPickSource = onPickSource,
                )
            }
        }
    }
}

@Composable
private fun VideoHeaderImage(video: Video) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsList(
    video: Video,
    signals: VideoSignals,
    settings: AppSettings,
    sourceUri: Uri?,
    onMakeShort: (Moment) -> Unit,
    onMakeTop: (List<Moment>) -> Unit,
    onPickSource: () -> Unit,
) {
    val context = LocalContext.current
    val moments = remember(signals, settings) { MomentFinder.find(signals, settings.finderConfig()) }
    val reactionLines = remember(signals) { signals.captions.count { MomentFinder.cueScore(it.text) > 0.0 } }
    val sourceName = remember(sourceUri) {
        sourceUri?.let { com.buggygamesyt.shortsfinder.data.MediaFiles.displayName(context, it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column {
                VideoHeaderImage(video)
                Spacer(Modifier.height(10.dp))
                Text(signals.title ?: video.title, style = MaterialTheme.typography.titleMedium)
                val meta = listOfNotNull(
                    if (signals.durationSec > 0) formatClock(signals.durationSec) else null,
                    video.views,
                    video.published,
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { openUrl(context, youtubeLinkAt(video.id, 0)) }, shape = CircleShape) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in YouTube")
                }
            }
        }

        item(key = "source") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (sourceUri != null) Icons.Filled.CheckCircle else Icons.Filled.Edit,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (sourceUri != null) "Video file ready" else "Pick the video file",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                sourceName
                                    ?: "Shorts are cut from a file on your phone: the original recording or the video you downloaded.",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onPickSource, shape = CircleShape) {
                            Text(if (sourceUri != null) "Change file" else "Pick file")
                        }
                        if (moments.size > 1) {
                            Button(onClick = { onMakeTop(moments.take(3)) }, shape = CircleShape) {
                                Text("Make top ${minOf(3, moments.size)}")
                            }
                        }
                    }
                }
            }
        }

        item(key = "signals") {
            SignalsCard(signals, reactionLines)
        }

        if (moments.isEmpty()) {
            item(key = "none") {
                Text(
                    if (signals.heat.isEmpty() && signals.captions.isEmpty() && signals.stamps.isEmpty()) {
                        "YouTube has no replay graph, captions or timestamped comments for this video yet, " +
                            "so there's nothing to rank. You can still make a Short from the file on your phone " +
                            "with the button on the home screen."
                    } else {
                        "No moment stands out yet. Try raising a weight in Settings."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            item(key = "title") {
                Text(
                    "Best moments for Shorts",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(moments, key = { "m${it.peakSec}" }) { m ->
                MomentCard(
                    videoId = video.id,
                    moment = m,
                    onMakeShort = { onMakeShort(m) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SignalsCard(signals: VideoSignals, reactionLines: Int) {
    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Signals", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val scheme = MaterialTheme.colorScheme
            SignalLine("Most replayed", signals.heat.isNotEmpty(), signals.heatNote, scheme.primary)
            val captionText = if (signals.captions.isNotEmpty()) {
                "${signals.captionNote} $reactionLines reaction cues."
            } else {
                signals.captionNote
            }
            SignalLine("Captions", signals.captions.isNotEmpty(), captionText, scheme.tertiary)
            SignalLine("Comments", signals.stamps.isNotEmpty(), signals.commentNote, scheme.secondary)
        }
    }
}

@Composable
private fun SignalLine(label: String, ok: Boolean, text: String, color: Color) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ok) color else MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MomentCard(videoId: String, moment: Moment, onMakeShort: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipText = formatRange(moment.clipStart, moment.clipEnd)
    var expanded by rememberSaveable(moment.peakSec) { mutableStateOf(false) }
    ElevatedCard(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreBadge(moment.score)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "#${moment.rank} · at ${formatClock(moment.peakSec)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Clip $clipText (${moment.clipEnd - moment.clipStart}s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (moment.details.isNotEmpty()) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Hide details" else "Show details",
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (r in moment.reasons) ReasonChip(r, onClick = { expanded = !expanded })
            }
            AnimatedVisibility(visible = expanded && moment.details.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp)) {
                    for (d in moment.details) {
                        Text(
                            d,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = { copyText(context, "Timestamp", formatClock(moment.peakSec)) }) {
                            Text("Copy time")
                        }
                        TextButton(onClick = { copyText(context, "Clip range", clipText) }) {
                            Text("Copy clip")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = onMakeShort, shape = CircleShape) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Make Short")
                }
                FilledTonalButton(
                    onClick = { openUrl(context, youtubeLinkAt(videoId, moment.clipStart)) },
                    shape = CircleShape,
                ) {
                    Text("Watch at ${formatClock(moment.clipStart)}")
                }
            }
        }
    }
}

@Composable
private fun ReasonChip(reason: Reason, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content, icon) = when (reason) {
        Reason.REPLAYED -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Filled.Refresh)
        Reason.REACTION -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Filled.Face)
        Reason.COMMENTS -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Filled.Favorite)
    }
    AssistChip(
        onClick = onClick,
        label = { Text(reason.label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = content,
            leadingIconContentColor = content,
        ),
        border = null,
    )
}
