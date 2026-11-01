package com.buggygamesyt.shortsfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buggygamesyt.shortsfinder.BuildConfig
import com.buggygamesyt.shortsfinder.data.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var handle by rememberSaveable { mutableStateOf(initial.handle) }
    var includeShorts by rememberSaveable { mutableStateOf(initial.includeShorts) }
    var wReplay by rememberSaveable { mutableStateOf(initial.weightReplay) }
    var wReaction by rememberSaveable { mutableStateOf(initial.weightReaction) }
    var wComments by rememberSaveable { mutableStateOf(initial.weightComments) }
    var clipLength by rememberSaveable { mutableStateOf(initial.clipLength.toFloat()) }
    var lead by rememberSaveable { mutableStateOf(initial.leadSeconds.toFloat()) }
    var commentPages by rememberSaveable { mutableStateOf(initial.commentPages.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Channel", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it },
                label = { Text("Channel handle or URL") },
                placeholder = { Text(AppSettings.DEFAULT_HANDLE) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Include the Shorts tab", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Also list videos from the channel's Shorts tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = includeShorts, onCheckedChange = { includeShorts = it })
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Signal weights", style = MaterialTheme.typography.titleSmall)
            Text(
                "How much each signal counts toward a moment's score. 0 turns a signal off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SliderRow("Most replayed", wReplay, 0f..2f, 19, "%.1f".format(wReplay)) { wReplay = round1(it) }
            SliderRow("Caption reactions", wReaction, 0f..2f, 19, "%.1f".format(wReaction)) { wReaction = round1(it) }
            SliderRow("Comment timestamps", wComments, 0f..2f, 19, "%.1f".format(wComments)) { wComments = round1(it) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Clips", style = MaterialTheme.typography.titleSmall)
            SliderRow("Clip length", clipLength, 15f..60f, 8, "${clipLength.roundToInt()}s") { clipLength = it }
            SliderRow("Start before the peak", lead, 0f..10f, 9, "${lead.roundToInt()}s") { lead = it }
            SliderRow(
                "Comment pages to read",
                commentPages,
                0f..10f,
                9,
                "${commentPages.roundToInt()} (about ${commentPages.roundToInt() * 20} comments)",
            ) { commentPages = it }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    onSave(
                        AppSettings(
                            handle = handle.trim(),
                            includeShorts = includeShorts,
                            weightReplay = wReplay,
                            weightReaction = wReaction,
                            weightComments = wComments,
                            clipLength = clipLength.roundToInt(),
                            leadSeconds = lead.roundToInt(),
                            commentPages = commentPages.roundToInt(),
                        )
                    )
                }, shape = CircleShape) { Text("Save") }
                OutlinedButton(onClick = {
                    val d = AppSettings()
                    handle = d.handle
                    includeShorts = d.includeShorts
                    wReplay = d.weightReplay
                    wReaction = d.weightReaction
                    wComments = d.weightComments
                    clipLength = d.clipLength.toFloat()
                    lead = d.leadSeconds.toFloat()
                    commentPages = d.commentPages.toFloat()
                }, shape = CircleShape) { Text("Defaults") }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "YT Shorts Maker ${BuildConfig.VERSION_NAME} · by Buggy Games YT\n" +
                    "Finds moments with YouTube's public web pages (no account or API key). " +
                    "Shorts are cut on your phone from your own video files and saved to Movies/YT Shorts Maker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
