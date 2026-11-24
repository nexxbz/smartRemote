package com.smartremote.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

const val PORT = 8765

/** Cliente TCP: HELLO:<pin> -> OK:<nombre>, luego comandos de una línea. */
class RemoteClient {
    var onState: (Boolean, String) -> Unit = { _, _ -> }
    var host = ""
    var pin = ""
    @Volatile private var out: PrintWriter? = null
    @Volatile private var sock: Socket? = null
    @Volatile private var gen = 0
    private val sender = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    fun connect(h: String, p: String) {
        close()
        host = h; pin = p
        val my = ++gen
        Thread {
            var ok = false
            try {
                val s = Socket()
                s.connect(InetSocketAddress(h, PORT), 4000)
                s.tcpNoDelay = true
                val w = PrintWriter(s.getOutputStream(), true)
                val r = BufferedReader(InputStreamReader(s.getInputStream()))
                s.soTimeout = 6000
                w.println("HELLO:$p")
                val resp = r.readLine()
                if (resp != null && resp.startsWith("OK")) {
                    s.soTimeout = 0
                    sock = s; out = w; ok = true
                    ui.post { if (gen == my) onState(true, resp.substringAfter("OK:", "TV")) }
                    while (r.readLine() != null) { }
                } else ui.post { if (gen == my) onState(false, "PIN incorrecto") }
            } catch (e: Exception) {
                if (!ok) ui.post { if (gen == my) onState(false, "No se pudo conectar: ${e.message}") }
            }
            if (ok) ui.post { if (gen == my) onState(false, "Desconectado") }
        }.start()
    }

    fun send(cmd: String) { sender.execute { out?.println(cmd) } }

    fun close() {
        gen++
        out = null
        try { sock?.close() } catch (_: Exception) {}
        sock = null
    }
}

/** Descubre TVs con el servicio _smartremote._tcp. en la red WiFi. */
class TvDiscovery(
    ctx: Context,
    private val onFound: (String, String) -> Unit,
    private val onLost: (String) -> Unit
) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val ui = Handler(Looper.getMainLooper())
    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
        override fun onServiceFound(i: NsdServiceInfo) {
            nsd.resolveService(i, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val ip = r.host?.hostAddress ?: return
                    ui.post { onFound(r.serviceName, ip) }
                }
            })
        }
        override fun onServiceLost(i: NsdServiceInfo) { ui.post { onLost(i.serviceName) } }
    }
    fun start() { try { nsd.discoverServices("_smartremote._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) {} }
    fun stop() { try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {} }
}
