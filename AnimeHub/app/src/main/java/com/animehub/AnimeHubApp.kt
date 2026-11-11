package com.animehub

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.animehub.data.local.AppDatabase
import com.animehub.data.network.HttpClientFactory
import com.animehub.data.repository.AnimeRepositoryImpl
import com.animehub.data.repository.SiteRepositoryImpl
import com.animehub.data.scraper.SourceRegistry
import com.animehub.data.settings.SettingsRepository
import com.animehub.domain.repository.AnimeRepository
import com.animehub.domain.repository.SiteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient = HttpClientFactory.create()
    private val db = Room.databaseBuilder(app, AppDatabase::class.java, "animehub.db")
        // v1 nunca foi publicada; ao subir de versão o banco de testes é recriado.
        .fallbackToDestructiveMigration()
        .build()

    val settings = SettingsRepository(app)
    val siteRepository: SiteRepository = SiteRepositoryImpl(db.siteDao(), httpClient)
    private val registry = SourceRegistry(db.siteDao(), httpClient, scope)
    val repository: AnimeRepository = AnimeRepositoryImpl(registry, db.libraryDao())

    init {
        scope.launch { siteRepository.ensureBuiltIns() }
    }
}

class AnimeHubApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // Capas passam pelo mesmo cliente seguro (HTTPS, DNS que bloqueia rede local).
    // Cache de memória (20% do heap) e de disco (100 MB) para capas: menos rede e rolagem mais fluida.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(container.httpClient)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
