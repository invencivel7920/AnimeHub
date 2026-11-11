package com.animehub.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/**
 * Centraliza o uso do DownloadManager: só aceita https, aplica cabeçalhos da fonte
 * (ex.: Referer) e respeita a preferência de baixar apenas em Wi-Fi.
 */
object Downloader {
    fun enqueue(
        context: Context,
        url: String,
        headers: Map<String, String>,
        fileName: String,
        title: String,
        description: String,
        wifiOnly: Boolean,
    ): Long? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(description)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AnimeHub/$fileName")
        headers.forEach { (name, value) -> request.addRequestHeader(name, value) }
        return try {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        } catch (_: Exception) {
            null
        }
    }
}
