package com.robothub

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetUtil {
    fun localIpv4(): String {
        val ifaces = NetworkInterface.getNetworkInterfaces()
            ?.let { Collections.list(it) }
            .orEmpty()

        val ips = ifaces
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }

        // Hotspot Android biasanya 192.168.43.x, prioritaskan itu
        return ips.firstOrNull { it.startsWith("192.168.43.") }
            ?: ips.firstOrNull()
            ?: "-"
    }
}