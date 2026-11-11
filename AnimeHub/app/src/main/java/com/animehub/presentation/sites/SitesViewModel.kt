package com.animehub.presentation.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animehub.data.settings.SettingsRepository
import com.animehub.domain.model.SiteInfo
import com.animehub.domain.repository.AddSiteResult
import com.animehub.domain.repository.SiteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteEvent(val message: String? = null, val closeDialogs: Boolean = false)

data class SitesUi(
    val adding: Boolean = false,
    val addError: String? = null,
    val testing: Boolean = false,
    val testResult: String? = null,
    val editError: String? = null,
)

class SitesViewModel(
    private val siteRepo: SiteRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    val sites: StateFlow<List<SiteInfo>> =
        siteRepo.sites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val disclaimerAccepted: StateFlow<Boolean> = settingsRepo.settings.map { it.disclaimerAccepted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _ui = MutableStateFlow(SitesUi())
    val ui: StateFlow<SitesUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<SiteEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SiteEvent> = _events.asSharedFlow()

    fun acceptDisclaimer() {
        viewModelScope.launch { settingsRepo.acceptDisclaimer() }
    }

    fun resetDialogState() = _ui.update { SitesUi() }

    fun add(url: String) {
        if (_ui.value.adding) return
        viewModelScope.launch {
            _ui.update { it.copy(adding = true, addError = null) }
            when (val result = siteRepo.detectAndAdd(url)) {
                is AddSiteResult.Added -> {
                    _ui.update { it.copy(adding = false) }
                    val msg = buildString {
                        append("Site adicionado: ${result.site.name}. ")
                        if (result.itemsFound == 0) append("Não detectei obras na página inicial — ajuste em Editar. ")
                        else append("${result.itemsFound} obras detectadas. ")
                        if (!result.searchDetected) append("Busca não detectada; será filtrada localmente.")
                    }
                    _events.emit(SiteEvent(msg, closeDialogs = true))
                }
                is AddSiteResult.Failed -> _ui.update { it.copy(adding = false, addError = result.message) }
            }
        }
    }

    fun setEnabled(site: SiteInfo, enabled: Boolean) {
        viewModelScope.launch { siteRepo.setEnabled(site.id, enabled) }
    }

    fun delete(site: SiteInfo) {
        viewModelScope.launch {
            siteRepo.delete(site.id)
            _events.emit(SiteEvent("Site removido: ${site.name}"))
        }
    }

    fun save(site: SiteInfo) {
        viewModelScope.launch {
            val error = siteRepo.update(site)
            if (error != null) _ui.update { it.copy(editError = error) }
            else {
                _ui.update { it.copy(editError = null) }
                _events.emit(SiteEvent("Configurações salvas", closeDialogs = true))
            }
        }
    }

    fun test(site: SiteInfo) {
        if (_ui.value.testing) return
        viewModelScope.launch {
            _ui.update { it.copy(testing = true, testResult = null) }
            val summary = siteRepo.test(site).summary()
            _ui.update { it.copy(testing = false, testResult = summary) }
            _events.emit(SiteEvent("${site.name}: $summary"))
        }
    }
}
