package com.animehub.data.network

import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
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
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
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
