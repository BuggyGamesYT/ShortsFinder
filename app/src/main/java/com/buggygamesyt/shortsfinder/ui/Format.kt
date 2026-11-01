package com.buggygamesyt.shortsfinder.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** 83 -> "1:23", 3723 -> "1:02:03". */
fun formatClock(totalSeconds: Int): String {
    val t = if (totalSeconds < 0) 0 else totalSeconds
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatRange(start: Int, end: Int): String = "${formatClock(start)} - ${formatClock(end)}"

fun formatUpdated(millis: Long): String =
    if (millis <= 0L) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun youtubeLinkAt(videoId: String, seconds: Int): String = "https://youtu.be/$videoId?t=${if (seconds < 0) 0 else seconds}"

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open YouTube links.", Toast.LENGTH_SHORT).show()
    }
}

fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (cm == null) {
        Toast.makeText(context, "Clipboard isn't available.", Toast.LENGTH_SHORT).show()
        return
    }
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
}
