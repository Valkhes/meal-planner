package dev.valkhes.mealplanner.sync

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * The only addresses the sync may talk to: private and link-local ones, never the internet
 * (README, constraint 1). Checked before every connection, in both directions.
 */
internal object LocalNetwork {
    fun isLocal(address: InetAddress): Boolean =
        when {
            address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress -> true
            // IPv6 unique local addresses, fc00::/7 (isSiteLocalAddress only knows the old fec0::/10).
            address is Inet6Address -> (address.address[0].toInt() and 0xFE) == 0xFC
            // 100.64.0.0/10 is carrier-grade NAT: shared with strangers, so not local.
            address is Inet4Address -> false
            else -> false
        }
}
