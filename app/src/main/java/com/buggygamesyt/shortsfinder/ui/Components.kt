package com.buggygamesyt.shortsfinder.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.buggygamesyt.shortsfinder.data.ShortSpec

/** Soft moving highlight for loading placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val shine = MaterialTheme.colorScheme.surfaceContainerLow
    background(
        Brush.linearGradient(
            colors = listOf(base, shine, base),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 200f),
        )
    )
}

@Composable
fun ShimmerBlock(modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).shimmer())
}

/** Placeholder shaped like a video row. */
@Composable
fun VideoRowPlaceholder() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBlock(
                Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f),
                corner = 16.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBlock(Modifier.fillMaxWidth().height(14.dp), corner = 7.dp)
                Spacer(Modifier.height(8.dp))
                ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(14.dp), corner = 7.dp)
                Spacer(Modifier.height(10.dp))
                ShimmerBlock(Modifier.fillMaxWidth(0.4f).height(10.dp), corner = 5.dp)
            }
        }
    }
}

/** Placeholder shaped like a moment card. */
@Composable
fun MomentPlaceholder() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(Modifier.size(52.dp), corner = 26.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBlock(Modifier.fillMaxWidth(0.5f).height(16.dp), corner = 8.dp)
                    Spacer(Modifier.height(8.dp))
                    ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(10.dp), corner = 5.dp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBlock(Modifier.width(84.dp).height(28.dp), corner = 14.dp)
                ShimmerBlock(Modifier.width(84.dp).height(28.dp), corner = 14.dp)
            }
        }
    }
}

/** Score 0..100 as a ring with the number in the middle, colored by strength. */
@Composable
fun ScoreBadge(score: Int, size: Dp = 52.dp) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        score >= 70 -> scheme.primary
        score >= 40 -> scheme.tertiary
        else -> scheme.secondary
    }
    val animated by animateFloatAsState(score.coerceIn(0, 100) / 100f, label = "score")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
        )
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = 4.dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round,
        )
        Text(
            "$score",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)?, onDismiss: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Dismiss") }
                if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

/** Progress and results of an export, as a dialog that can't be dismissed while working. */
@Composable
fun ExportDialog(state: ExportState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    if (!state.active) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !state.running, dismissOnClickOutside = !state.running),
        icon = {
            Icon(
                if (state.running) Icons.Filled.PlayArrow else if (state.error != null) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
            )
        },
        title = {
            Text(
                when {
                    state.running && state.total > 1 -> "Making Short ${state.index + 1} of ${state.total}"
                    state.running -> "Making your Short"
                    state.error != null && state.saved.isEmpty() -> "No Short this time"
                    state.saved.size > 1 -> "${state.saved.size} Shorts ready"
                    else -> "Short ready"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.running) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        "${(state.progress * 100).toInt()}% · 1080×1920 MP4. Keep the app open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val err = state.error
                if (err != null) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.saved.isNotEmpty()) {
                    Text(
                        "Saved to Movies/${ShortSpec.GALLERY_FOLDER}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (s in state.saved) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(s.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { shareVideo(context, s.uri) }, shape = CircleShape) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                                FilledTonalButton(onClick = { openVideo(context, s.uri) }, shape = CircleShape) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.running) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
    )
}

fun shareVideo(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Share Short").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can share videos.", Toast.LENGTH_SHORT).show()
    }
}

fun openVideo(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "video/mp4")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No video player app found.", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Couldn't open the video.", Toast.LENGTH_SHORT).show()
    }
}

/** A file name for a finished Short. */
fun shortFileName(title: String, startSec: Int): String {
    val clean = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(Regex("\\s+"), "_").take(40)
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
    val at = formatClock(startSec).replace(":", "m")
    return "Short_${clean.ifEmpty { "clip" }}_${at}_$stamp.mp4"
}
