package com.animehub.data.scraper

import com.animehub.data.local.SiteDao
import com.animehub.data.local.toInfo
import com.animehub.domain.model.SiteInfo
import com.animehub.domain.source.AnimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient

class Site(val info: SiteInfo, val source: AnimeSource)

object SourceFactory {
    fun create(info: SiteInfo, client: OkHttpClient): AnimeSource =
        if (info.builtIn && info.id == DemoSource.ID) DemoSource() else GenericWebSource(info, client)
}

/** Mantém as fontes em sincronia com a tabela de sites (adicionar/editar/ativar reflete na hora). */
class SourceRegistry(dao: SiteDao, client: OkHttpClient, scope: CoroutineScope) {
    /** null até a primeira leitura do banco. */
    val sites: StateFlow<List<Site>?> = dao.observeAll()
        .map<List<com.animehub.data.local.SiteEntity>, List<Site>?> { rows ->
            rows.map { row -> row.toInfo().let { Site(it, SourceFactory.create(it, client)) } }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun awaitSites(): List<Site> = sites.filterNotNull().first()

    suspend fun get(id: String): AnimeSource =
        awaitSites().firstOrNull { it.info.id == id }?.source ?: error("Fonte removida ou desconhecida")
}
