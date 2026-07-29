package com.forge.workshop.vpn

import java.net.NetworkInterface
import java.util.Collections

/**
 * Lightweight VPN presence check via network interfaces (no admin, no external calls). "Connected"
 * means a matching adapter is up and holds a routable address. In AUTO mode any VPN-looking adapter
 * counts; otherwise the user pins a specific adapter by its display name.
 */
object Vpn {

    private val KEYWORDS = listOf(
        "vpn", "wireguard", "wintun", "openvpn", "tap-windows", "tap-", "tunnel", "tun",
        "anyconnect", "cisco", "fortinet", "forticlient", "globalprotect", "pangp", "zscaler",
        "tailscale", "nordlynx", "mullvad", "ppp", "sonicwall", "pulse", "checkpoint", "openconnect",
    )

    /** Up, non-loopback adapters — the choices for the settings picker (display names). */
    fun candidates(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .map { it.displayName ?: it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())

    /** True if the tracked VPN is up. [selected] blank = AUTO (any VPN-like adapter). */
    fun isConnected(selected: String): Boolean = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).any { nif ->
            val up = runCatching { nif.isUp && !nif.isLoopback }.getOrDefault(false)
            if (!up) return@any false
            val label = "${nif.displayName.orEmpty()} ${nif.name}".lowercase()
            val match = if (selected.isBlank()) KEYWORDS.any { label.contains(it) }
            else (nif.displayName == selected || nif.name == selected)
            match && Collections.list(nif.inetAddresses).any { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        }
    }.getOrDefault(false)
}
