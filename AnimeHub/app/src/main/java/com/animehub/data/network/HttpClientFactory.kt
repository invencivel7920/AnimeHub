package com.animehub.data.network

import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientFactory {
    /**
     * Cliente HTTP endurecido:
     * - TLS moderno (1.2/1.3) apenas;
     * - sem redirecionamento https -> http;
     * - DNS que bloqueia endereços de rede local;
     * - timeouts curtos.
     *
     * @param pins host -> pins "sha256/..." (opcional; só use para hosts cujos pins você mantém).
     */
    fun create(pins: Map<String, List<String>> = emptyMap()): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .dns(SafeDns)
            .dispatcher(Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 8
            })
            .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AnimeHub/1.1")
                        .build()
                )
            }
        if (pins.isNotEmpty()) {
            val pinner = CertificatePinner.Builder().apply {
                pins.forEach { (host, list) -> add(host, *list.toTypedArray()) }
            }.build()
            builder.certificatePinner(pinner)
        }
        return builder.build()
    }
}
