package com.smartremote.tv

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections

object Util {
    fun pin(c: Context): String {
        val p = c.getSharedPreferences("tv", 0)
        var v = p.getString("pin", null)
        if (v == null) {
            v = (1000 + SecureRandom().nextInt(9000)).toString()
            p.edit().putString("pin", v).apply()
        }
        return v
    }

    fun localIp(): String {
        try {
            for (i in Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (a in Collections.list(i.inetAddresses))
                    if (!a.isLoopbackAddress && a is Inet4Address) return a.hostAddress ?: "?"
        } catch (_: Exception) {}
        return "?"
    }
}
