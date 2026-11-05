package com.manuscript.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import java.io.File

fun Context.openUrl(url: String, inApp: Boolean = true) {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
    val web = uri.scheme == "http" || uri.scheme == "https"
    if (inApp && web) {
        val launched = runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
                .launchUrl(this, uri)
        }.isSuccess
        if (launched) return
    }
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun Context.shareText(text: String, subject: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    runCatching { startActivity(Intent.createChooser(intent, subject ?: "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun Context.shareFile(file: File, mime: String, subject: String? = null) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(Intent.createChooser(intent, subject ?: "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun Context.copyText(text: String, label: String = "Manuscript") {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun Context.clipboardText(): String? {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
}
