package com.animehub.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.animehub.domain.model.AppSettings
import com.animehub.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val autoFind = booleanPreferencesKey("auto_find_sources")
        val preferHls = booleanPreferencesKey("prefer_hls")
        val autoNext = booleanPreferencesKey("auto_next")
        val secure = booleanPreferencesKey("secure_screen")
        val disclaimer = booleanPreferencesKey("disclaimer_accepted")
        val audioLanguage = stringPreferencesKey("preferred_audio_language")
        val subtitleLanguage = stringPreferencesKey("preferred_subtitle_language")
        val downloadQuality = stringPreferencesKey("download_quality")
        val downloadWifiOnly = booleanPreferencesKey("download_wifi_only")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "") }.getOrDefault(defaults.themeMode),
            dynamicColor = p[Keys.dynamic] ?: defaults.dynamicColor,
            autoFindOtherSources = p[Keys.autoFind] ?: defaults.autoFindOtherSources,
            preferHls = p[Keys.preferHls] ?: defaults.preferHls,
            autoPlayNext = p[Keys.autoNext] ?: defaults.autoPlayNext,
            secureScreen = p[Keys.secure] ?: defaults.secureScreen,
            disclaimerAccepted = p[Keys.disclaimer] ?: defaults.disclaimerAccepted,
            preferredAudioLanguage = p[Keys.audioLanguage],
            preferredSubtitleLanguage = p[Keys.subtitleLanguage],
            downloadQuality = p[Keys.downloadQuality] ?: defaults.downloadQuality,
            downloadWifiOnly = p[Keys.downloadWifiOnly] ?: defaults.downloadWifiOnly,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.settingsStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(v: Boolean) = context.settingsStore.edit { it[Keys.dynamic] = v }
    suspend fun setAutoFind(v: Boolean) = context.settingsStore.edit { it[Keys.autoFind] = v }
    suspend fun setPreferHls(v: Boolean) = context.settingsStore.edit { it[Keys.preferHls] = v }
    suspend fun setAutoNext(v: Boolean) = context.settingsStore.edit { it[Keys.autoNext] = v }
    suspend fun setSecureScreen(v: Boolean) = context.settingsStore.edit { it[Keys.secure] = v }
    suspend fun acceptDisclaimer() = context.settingsStore.edit { it[Keys.disclaimer] = true }

    suspend fun setPreferredAudioLanguage(language: String?) = context.settingsStore.edit {
        if (language.isNullOrBlank()) it.remove(Keys.audioLanguage) else it[Keys.audioLanguage] = language
    }

    suspend fun setPreferredSubtitleLanguage(language: String?) = context.settingsStore.edit {
        if (language.isNullOrBlank()) it.remove(Keys.subtitleLanguage) else it[Keys.subtitleLanguage] = language
    }

    suspend fun setDownloadQuality(quality: String) = context.settingsStore.edit {
        it[Keys.downloadQuality] = quality
    }

    suspend fun setDownloadWifiOnly(v: Boolean) = context.settingsStore.edit {
        it[Keys.downloadWifiOnly] = v
    }
}
