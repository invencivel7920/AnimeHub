package com.animehub.data.network

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Validação de URLs digitadas pelo usuário ou extraídas de páginas.
 * Regras: somente https, sem credenciais embutidas e sem destinos de rede local
 * (evita que uma página maliciosa faça o app acessar roteador, localhost, etc.).
 */
object UrlValidator {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val blockedSuffixes = listOf(".localhost", ".local", ".internal", ".lan", ".home", ".corp", ".intranet")

    fun normalizeBase(input: String): HttpUrl? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        val url = s.toHttpUrlOrNull() ?: return null
        return if (isSafe(url)) url else null
    }

    fun isSafeUrl(url: String): Boolean = url.toHttpUrlOrNull()?.let { isSafe(it) } ?: false

    fun isSafe(url: HttpUrl): Boolean {
        if (url.scheme != "https") return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        return !isBlockedHost(url.host)
    }

    fun isBlockedHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost" || blockedSuffixes.any { h.endsWith(it) }) return true
        if (ipv4.matches(h) || h.contains(':')) {
            return try {
                isPrivateAddress(InetAddress.getByName(h))
            } catch (e: Exception) {
                true
            }
        }
        return !h.contains('.')
    }

    fun isPrivateAddress(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress ||
            a.isSiteLocalAddress || a.isMulticastAddress
        ) return true
        val b = a.address
        if (a is Inet4Address) {
            val b0 = b[0].toInt() and 0xff
            val b1 = b[1].toInt() and 0xff
            if (b0 == 0) return true
            if (b0 == 100 && b1 in 64..127) return true // CGNAT
        }
        if (a is Inet6Address && (b[0].toInt() and 0xfe) == 0xfc) return true // fc00::/7
        return false
    }
}

/** DNS que recusa nomes que resolvem para endereços privados (proteção contra DNS rebinding). */
object SafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.any { UrlValidator.isPrivateAddress(it) }) {
            throw UnknownHostException("Endereço de rede local bloqueado")
        }
        return addresses
    }
}
