package com.animehub.domain.model

import com.animehub.domain.download.DownloadQuality

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoFindOtherSources: Boolean = true,
    val preferHls: Boolean = true,
    val autoPlayNext: Boolean = true,
    val secureScreen: Boolean = false,
    val disclaimerAccepted: Boolean = false,
    /** Código ISO (ex.: "pt", "en"). null = automático. */
    val preferredAudioLanguage: String? = null,
    /** Código ISO (ex.: "pt", "en"). null = legendas desativadas por padrão. */
    val preferredSubtitleLanguage: String? = null,
    val downloadQuality: String = DownloadQuality.BEST.name,
    val downloadWifiOnly: Boolean = false,
)
