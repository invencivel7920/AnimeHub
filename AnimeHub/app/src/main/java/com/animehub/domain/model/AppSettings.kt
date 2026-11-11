package com.animehub.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoFindOtherSources: Boolean = true,
    val preferHls: Boolean = true,
    val autoPlayNext: Boolean = true,
    val secureScreen: Boolean = false,
    val disclaimerAccepted: Boolean = false,
)
