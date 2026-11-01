package com.buggygamesyt.shortsfinder.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.buggygamesyt.shortsfinder.ui.theme.Amber
import com.buggygamesyt.shortsfinder.ui.theme.BugGreen
import com.buggygamesyt.shortsfinder.ui.theme.Sky

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    video: Video,
    state: MomentsState,
    settings: AppSettings,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        video.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(step, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }
                is MomentsState.Ready -> ResultsList(video, state.signals, settings)
            }
        }
    }
}

@Composable
private fun ResultsList(video: Video, signals: VideoSignals, settings: AppSettings) {
    val context = LocalContext.current
    val moments = remember(signals, settings) { MomentFinder.find(signals, settings.finderConfig()) }
    val reactionLines = remember(signals) { signals.captions.count { MomentFinder.cueScore(it.text) > 0.0 } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(signals.title ?: video.title, style = MaterialTheme.typography.titleMedium)
                val meta = listOfNotNull(
                    if (signals.durationSec > 0) formatClock(signals.durationSec) else null,
                    video.views,
                    video.published,
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { openUrl(context, youtubeLinkAt(video.id, 0)) }) {
                    Text("Open video in YouTube")
                }
            }
        }

        item(key = "signals") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Signals", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SignalLine("Most replayed", signals.heat.isNotEmpty(), signals.heatNote, BugGreen)
                    val captionText = if (signals.captions.isNotEmpty()) {
                        "${signals.captionNote} $reactionLines reaction cues."
                    } else {
                        signals.captionNote
                    }
                    SignalLine("Captions", signals.captions.isNotEmpty(), captionText, Amber)
                    SignalLine("Comments", signals.stamps.isNotEmpty(), signals.commentNote, Sky)
                }
            }
        }

        if (moments.isEmpty()) {
            item(key = "none") {
                Text(
                    if (signals.heat.isEmpty() && signals.captions.isEmpty() && signals.stamps.isEmpty()) {
                        "YouTube has no replay graph, captions or timestamped comments for this video yet, " +
                            "so there's nothing to rank. Try again once it has more views, or pick an older video."
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
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(moments, key = { "m${it.peakSec}" }) { m ->
                MomentCard(videoId = video.id, moment = m)
            }
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
                .clip(RoundedCornerShape(50))
                .background(if (ok) color else MaterialTheme.colorScheme.outline)
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
private fun MomentCard(videoId: String, moment: Moment) {
    val context = LocalContext.current
    val clipText = formatRange(moment.clipStart, moment.clipEnd)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${moment.rank}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "at ${formatClock(moment.peakSec)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Clip $clipText (${moment.clipEnd - moment.clipStart}s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreBadge(moment.score)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (r in moment.reasons) ReasonChip(r)
            }
            for (d in moment.details) {
                Text(
                    d,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(onClick = { openUrl(context, youtubeLinkAt(videoId, moment.clipStart)) }) {
                    Text("Open at ${formatClock(moment.clipStart)}")
                }
                TextButton(onClick = { copyText(context, "Timestamp", formatClock(moment.peakSec)) }) {
                    Text("Copy time")
                }
                TextButton(onClick = { copyText(context, "Clip range", clipText) }) {
                    Text("Copy clip")
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("score", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReasonChip(reason: Reason) {
    val color = when (reason) {
        Reason.REPLAYED -> BugGreen
        Reason.REACTION -> Amber
        Reason.COMMENTS -> Sky
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f),
        contentColor = color,
    ) {
        Text(
            reason.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
