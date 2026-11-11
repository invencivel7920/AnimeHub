package com.animehub.domain.repository

import com.animehub.domain.model.SiteInfo
import kotlinx.coroutines.flow.Flow

sealed interface AddSiteResult {
    data class Added(val site: SiteInfo, val itemsFound: Int, val searchDetected: Boolean) : AddSiteResult
    data class Failed(val message: String) : AddSiteResult
}

data class TestResult(val items: Int, val searchItems: Int?, val error: String?) {
    fun summary(): String = when {
        error != null -> "Erro: $error"
        items == 0 -> "Nenhuma obra encontrada na página inicial. Ajuste o seletor CSS dos itens."
        else -> "Início: $items obras · Busca: " +
            (searchItems?.let { "$it resultados" } ?: "indisponível (será filtrada localmente)")
    }
}

interface SiteRepository {
    fun sites(): Flow<List<SiteInfo>>
    suspend fun ensureBuiltIns()

    /** Valida a URL, analisa o site automaticamente e o salva. */
    suspend fun detectAndAdd(input: String): AddSiteResult

    /** Retorna uma mensagem de erro de validação ou null se salvou. */
    suspend fun update(site: SiteInfo): String?
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)
    suspend fun test(site: SiteInfo): TestResult
}
