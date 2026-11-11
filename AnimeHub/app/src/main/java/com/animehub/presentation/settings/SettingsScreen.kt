package com.animehub.presentation.settings

import android.app.DownloadManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animehub.data.settings.SettingsRepository
import com.animehub.domain.download.DownloadQuality
import com.animehub.domain.model.AppSettings
import com.animehub.domain.model.ThemeMode
import com.animehub.presentation.common.commonLanguages
import com.animehub.presentation.common.languageLabel
import com.animehub.presentation.common.languageMatches
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(m: ThemeMode) = viewModelScope.launch { repo.setTheme(m) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { repo.setDynamicColor(v) }
    fun setAutoFind(v: Boolean) = viewModelScope.launch { repo.setAutoFind(v) }
    fun setPreferHls(v: Boolean) = viewModelScope.launch { repo.setPreferHls(v) }
    fun setAutoNext(v: Boolean) = viewModelScope.launch { repo.setAutoNext(v) }
    fun setSecure(v: Boolean) = viewModelScope.launch { repo.setSecureScreen(v) }
    fun setAudioLanguage(language: String?) = viewModelScope.launch { repo.setPreferredAudioLanguage(language) }
    fun setSubtitleLanguage(language: String?) = viewModelScope.launch { repo.setPreferredSubtitleLanguage(language) }
    fun setDownloadQuality(quality: DownloadQuality) = viewModelScope.launch { repo.setDownloadQuality(quality.name) }
    fun setDownloadWifiOnly(v: Boolean) = viewModelScope.launch { repo.setDownloadWifiOnly(v) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val audioOptions = remember(s.preferredAudioLanguage) {
        commonLanguages.let { base ->
            val current = s.preferredAudioLanguage
            if (current != null && base.none { languageMatches(current, it) }) base + current else base
        }
    }
    val subtitleOptions = remember(s.preferredSubtitleLanguage) {
        commonLanguages.let { base ->
            val current = s.preferredSubtitleLanguage
            if (current != null && base.none { languageMatches(current, it) }) base + current else base
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajustes") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Header("Aparência")
            ListItem(
                headlineContent = { Text("Tema") },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")
                            .forEach { (mode, label) ->
                                FilterChip(
                                    selected = s.themeMode == mode,
                                    onClick = { vm.setTheme(mode) },
                                    label = { Text(label) },
                                )
                            }
                    }
                },
            )
            SwitchItem("Cores dinâmicas", "Usa as cores do papel de parede (Android 12+)", s.dynamicColor, vm::setDynamic)

            HorizontalDivider()
            Header("Fontes")
            SwitchItem(
                "Procurar a obra em outras fontes",
                "Ao abrir uma obra, busca automaticamente nos outros sites ativos para você escolher onde assistir",
                s.autoFindOtherSources, vm::setAutoFind,
            )

            HorizontalDivider()
            Header("Reprodução")
            SwitchItem("Preferir HLS", "Qualidade adaptativa quando o site oferece", s.preferHls, vm::setPreferHls)
            SwitchItem("Próximo episódio automático", "Ao terminar, abre o episódio seguinte", s.autoPlayNext, vm::setAutoNext)
            ListItem(
                headlineContent = { Text("Idioma preferido do áudio") },
                supportingContent = {
                    Column {
                        Text("Aplicado automaticamente ao abrir um vídeo com várias faixas de áudio.")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            FilterChip(
                                selected = s.preferredAudioLanguage == null,
                                onClick = { vm.setAudioLanguage(null) },
                                label = { Text("Automático") },
                            )
                            audioOptions.forEach { language ->
                                FilterChip(
                                    selected = languageMatches(s.preferredAudioLanguage, language),
                                    onClick = { vm.setAudioLanguage(language) },
                                    label = { Text(languageLabel(language)) },
                                )
                            }
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Idioma preferido das legendas") },
                supportingContent = {
                    Column {
                        Text("Desativadas por padrão; você também pode trocar no player.")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            FilterChip(
                                selected = s.preferredSubtitleLanguage == null,
                                onClick = { vm.setSubtitleLanguage(null) },
                                label = { Text("Desativadas") },
                            )
                            subtitleOptions.forEach { language ->
                                FilterChip(
                                    selected = languageMatches(s.preferredSubtitleLanguage, language),
                                    onClick = { vm.setSubtitleLanguage(language) },
                                    label = { Text(languageLabel(language)) },
                                )
                            }
                        }
                    }
                },
            )

            HorizontalDivider()
            Header("Downloads")
            ListItem(
                headlineContent = { Text("Qualidade dos downloads") },
                supportingContent = {
                    Column {
                        Text("Usada ao baixar vários episódios pela tela da obra.")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            DownloadQuality.values().forEach { quality ->
                                FilterChip(
                                    selected = DownloadQuality.fromName(s.downloadQuality) == quality,
                                    onClick = { vm.setDownloadQuality(quality) },
                                    label = { Text(quality.label) },
                                )
                            }
                        }
                    }
                },
            )
            SwitchItem(
                "Baixar apenas no Wi-Fi",
                "Evita consumir dados móveis ao preparar os downloads",
                s.downloadWifiOnly, vm::setDownloadWifiOnly,
            )
            ListItem(
                headlineContent = { Text("Onde ficam os arquivos") },
                supportingContent = {
                    Text(
                        "Pasta Downloads/AnimeHub do aparelho, com nomes como AnimeHub_Obra_E1.mp4. " +
                            "Só links diretos HTTPS (MP4, MKV, WebM) são baixados; HLS/DASH ficam de fora. " +
                            "Em versões antigas do Android pode ser preciso permitir o acesso ao armazenamento."
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Abrir gerenciador de downloads") },
                supportingContent = { Text("Veja o progresso e os arquivos baixados") },
                modifier = Modifier.clickable {
                    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "Não foi possível abrir o gerenciador de downloads.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )

            HorizontalDivider()
            Header("Segurança")
            SwitchItem(
                "Bloquear capturas de tela",
                "Impede prints, gravação de tela e miniatura nos apps recentes",
                s.secureScreen, vm::setSecure,
            )
            ListItem(
                headlineContent = { Text("Proteções sempre ativas") },
                supportingContent = {
                    Text(
                        "• Somente conexões https (TLS 1.2+)\n" +
                            "• Endereços de rede local e IPs privados bloqueados\n" +
                            "• Limite de tamanho nas páginas lidas\n" +
                            "• Sem backup automático dos dados do app\n" +
                            "• Redirecionamentos para http recusados"
                    )
                },
            )

            HorizontalDivider()
            Header("Sobre")
            ListItem(
                headlineContent = { Text("AnimeHub 1.1") },
                supportingContent = {
                    Text(
                        "O app não hospeda conteúdo e não inclui sites prontos. Use apenas fontes e conteúdos " +
                            "que você tem direito de acessar e respeite os termos de cada site."
                    )
                },
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
    )
}

@Composable
private fun SwitchItem(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
