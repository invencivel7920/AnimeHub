package com.animehub.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animehub.data.settings.SettingsRepository
import com.animehub.domain.model.AppSettings
import com.animehub.domain.model.ThemeMode
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()

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
