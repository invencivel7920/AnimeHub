package com.animehub.data.repository

import com.animehub.data.local.SiteDao
import com.animehub.data.local.SiteEntity
import com.animehub.data.local.toInfo
import com.animehub.data.network.UrlValidator
import com.animehub.data.scraper.DemoSource
import com.animehub.data.scraper.PageFetcher
import com.animehub.data.scraper.ScrapeHeuristics
import com.animehub.data.scraper.SiteBlockedException
import com.animehub.data.scraper.SiteDetector
import com.animehub.data.scraper.SourceFactory
import com.animehub.domain.model.SiteInfo
import com.animehub.domain.repository.AddSiteResult
import com.animehub.domain.repository.SiteRepository
import com.animehub.domain.repository.TestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.io.IOException
import java.util.UUID

class SiteRepositoryImpl(
    private val dao: SiteDao,
    private val client: OkHttpClient,
) : SiteRepository {

    private val detector = SiteDetector(PageFetcher(client))

    override fun sites(): Flow<List<SiteInfo>> = dao.observeAll().map { rows -> rows.map { it.toInfo() } }

    override suspend fun ensureBuiltIns() {
        dao.insertIgnore(
            SiteEntity(
                id = DemoSource.ID, name = DemoSource.NAME, baseUrl = DemoSource.URL,
                searchUrl = null, itemSelector = null, episodeSelector = null,
                enabled = true, builtIn = true, sortOrder = 0,
            )
        )
    }

    override suspend fun detectAndAdd(input: String): AddSiteResult {
        val base = UrlValidator.normalizeBase(input)
            ?: return AddSiteResult.Failed("Endereço inválido. Use um site público com https (sem IP local).")
        dao.all().firstOrNull { ScrapeHeuristics.sameHost(it.baseUrl, base.toString()) }?.let {
            return AddSiteResult.Failed("Este site já foi adicionado (${it.name}).")
        }
        return try {
            val detected = detector.detect(base)
            val entity = SiteEntity(
                id = "site-" + UUID.randomUUID().toString().take(8),
                name = detected.name, baseUrl = detected.baseUrl, searchUrl = detected.searchUrl,
                itemSelector = null, episodeSelector = null,
                enabled = true, builtIn = false, sortOrder = dao.all().size,
            )
            dao.upsert(entity)
            AddSiteResult.Added(entity.toInfo(), detected.itemsFound, detected.searchUrl != null)
        } catch (e: SiteBlockedException) {
            AddSiteResult.Failed(e.message ?: "Site bloqueado por verificação anti-bot.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AddSiteResult.Failed("Não foi possível acessar o site: ${e.message ?: "erro de rede"}")
        } catch (e: Exception) {
            AddSiteResult.Failed("Falha ao analisar o site: ${e.message ?: "erro desconhecido"}")
        }
    }

    override suspend fun update(site: SiteInfo): String? {
        val existing = dao.get(site.id) ?: return "Site não encontrado."
        if (existing.builtIn) return "Sites embutidos não podem ser editados."

        val search = site.searchUrl?.trim()?.ifBlank { null }
        if (search != null) {
            if (!search.contains("{q}")) return "A URL de busca precisa conter {q} onde vai o termo pesquisado."
            if (!UrlValidator.isSafeUrl(search.replace("{q}", "x"))) return "URL de busca inválida (use https público)."
        }
        val item = site.itemSelector?.trim()?.ifBlank { null }
        val episode = site.episodeSelector?.trim()?.ifBlank { null }
        for (sel in listOf(item, episode)) {
            if (sel != null && !validSelector(sel)) return "Seletor CSS inválido: $sel"
        }
        dao.upsert(
            existing.copy(
                name = site.name.trim().ifBlank { existing.name }.take(40),
                searchUrl = search, itemSelector = item, episodeSelector = episode,
            )
        )
        return null
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun test(site: SiteInfo): TestResult {
        PageFetcher.clearCache() // o teste precisa refletir a configuração atual
        val source = SourceFactory.create(site, client)
        return try {
            val items = withTimeout(30_000) { source.popular(1) }.size
            val search = try {
                withTimeout(30_000) { source.search("a", 1) }.size
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) null else throw e
            } catch (e: Exception) {
                null
            }
            TestResult(items, search, null)
        } catch (e: TimeoutCancellationException) {
            TestResult(0, null, "Tempo esgotado")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TestResult(0, null, e.message ?: "erro desconhecido")
        }
    }

    private fun validSelector(selector: String): Boolean = try {
        Jsoup.parse("<a></a>").select(selector)
        true
    } catch (e: RuntimeException) {
        false
    }
}
