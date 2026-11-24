package com.smartremote.tv

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class CursorView(c: Context) : View(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f }
    override fun onDraw(c: Canvas) {
        c.drawCircle(width / 2f, height / 2f, 18f, fill)
        c.drawCircle(width / 2f, height / 2f, 18f, edge)
    }
}

class RemoteAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: RemoteAccessibilityService? = null
        const val PORT = 8765
    }

    private val main = Handler(Looper.getMainLooper())
    private var server: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var reg: NsdManager.RegistrationListener? = null
    private var wm: WindowManager? = null
    private var cursor: CursorView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var cx = -1f
    private var cy = -1f
    private val hideCursor = Runnable { removeCursor() }

    override fun onServiceConnected() {
        instance = this
        startServer()
        registerNsd()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }

    private fun shutdown() {
        instance = null
        try { server?.close() } catch (_: Exception) {}
        try { reg?.let { nsd?.unregisterService(it) } } catch (_: Exception) {}
        removeCursor()
    }

    // ---------- Red ----------
    private fun startServer() {
        Thread {
            try {
                val ss = ServerSocket(PORT)
                server = ss
                while (!ss.isClosed) {
                    val c = ss.accept()
                    Thread { handle(c) }.start()
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun handle(c: Socket) {
        try {
            c.tcpNoDelay = true
            val r = BufferedReader(InputStreamReader(c.getInputStream()))
            val w = PrintWriter(c.getOutputStream(), true)
            c.soTimeout = 5000
            val hello = r.readLine()
            if (hello != "HELLO:${Util.pin(this)}") {
                Thread.sleep(1000); w.println("ERR"); c.close(); return
            }
            c.soTimeout = 0
            w.println("OK:${Build.MODEL}")
            var line = r.readLine()
            while (line != null) {
                val l = line
                main.post { exec(l) }
                line = r.readLine()
            }
        } catch (_: Exception) {
        } finally { try { c.close() } catch (_: Exception) {} }
    }

    private fun registerNsd() {
        try {
            val info = NsdServiceInfo().apply {
                serviceName = "TV ${Build.MODEL}"; serviceType = "_smartremote._tcp."; port = PORT
            }
            val m = getSystemService(NSD_SERVICE) as NsdManager
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            nsd = m; reg = l
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: Exception) {}
    }

    // ---------- Comandos ----------
    private fun exec(cmd: String) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        when {
            cmd == "UP" -> nav(View.FOCUS_UP)
            cmd == "DOWN" -> nav(View.FOCUS_DOWN)
            cmd == "LEFT" -> nav(View.FOCUS_LEFT)
            cmd == "RIGHT" -> nav(View.FOCUS_RIGHT)
            cmd == "OK" -> ok()
            cmd == "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            cmd == "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            cmd == "MENU" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            cmd == "MORE" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            cmd == "POWER" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            cmd == "VOL_UP" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            cmd == "VOL_DOWN" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            cmd == "MUTE" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            cmd == "PLAY" -> media(am, KeyEvent.KEYCODE_MEDIA_PLAY)
            cmd == "PAUSE" -> media(am, KeyEvent.KEYCODE_MEDIA_PAUSE)
            cmd == "REW" -> media(am, KeyEvent.KEYCODE_MEDIA_REWIND)
            cmd == "FF" -> media(am, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            cmd == "BRI_UP" -> brightness(25)
            cmd == "BRI_DOWN" -> brightness(-25)
            cmd == "CC" -> startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            cmd == "DEL" -> editText { it.dropLast(1) }
            cmd.startsWith("TEXT:") -> { val t = cmd.substring(5); editText { it + t } }
            cmd.startsWith("MOVE:") -> {
                val p = cmd.substring(5).split(",")
                if (p.size == 2) moveCursor(p[0].toFloatOrNull() ?: 0f, p[1].toFloatOrNull() ?: 0f)
            }
            cmd == "CLICK" -> click()
            cmd == "SCROLL:UP" -> scroll(false)
            cmd == "SCROLL:DOWN" -> scroll(true)
        }
    }

    private fun media(am: AudioManager, code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun brightness(delta: Int) {
        if (Settings.System.canWrite(this)) {
            val v = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v + delta).coerceIn(10, 255))
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------- Navegación por foco ----------
    private fun focused(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    private fun firstFocusable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isFocusable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) firstFocusable(n.getChild(i))?.let { return it }
        return null
    }

    private fun nav(dir: Int) {
        val root = rootInActiveWindow ?: return
        val cur = focused()
        val next = if (cur != null) cur.focusSearch(dir) else firstFocusable(root)
        if (next != null) {
            next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        } else if (dir == View.FOCUS_UP) scroll(false)
        else if (dir == View.FOCUS_DOWN) scroll(true)
    }

    private fun ok() {
        var n = focused()
        val orig = n
        while (n != null && !n.isClickable) n = n.parent
        if (n == null || !n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            if (orig == null) click() else orig.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    // ---------- Texto ----------
    private fun editText(f: (String) -> String) {
        val n = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        if (!n.isEditable) return
        val hint = Build.VERSION.SDK_INT >= 26 && n.isShowingHintText
        val cur = if (hint) "" else n.text?.toString() ?: ""
        val a = Bundle()
        a.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, f(cur))
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a)
    }

    // ---------- Cursor / gestos ----------
    private fun ensureCursor() {
        main.removeCallbacks(hideCursor)
        main.postDelayed(hideCursor, 6000)
        if (cursor != null) return
        val m = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        if (cx < 0) { cx = dm.widthPixels / 2f; cy = dm.heightPixels / 2f }
        val v = CursorView(this)
        val p = WindowManager.LayoutParams(
            60, 60, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = (cx - 30).toInt(); p.y = (cy - 30).toInt()
        m.addView(v, p)
        wm = m; cursor = v; lp = p
    }

    private fun removeCursor() {
        try { cursor?.let { wm?.removeView(it) } } catch (_: Exception) {}
        cursor = null
    }

    private fun moveCursor(dx: Float, dy: Float) {
        ensureCursor()
        val dm = resources.displayMetrics
        cx = (cx + dx).coerceIn(0f, dm.widthPixels.toFloat())
        cy = (cy + dy).coerceIn(0f, dm.heightPixels.toFloat())
        lp?.let { it.x = (cx - 30).toInt(); it.y = (cy - 30).toInt(); try { wm?.updateViewLayout(cursor, it) } catch (_: Exception) {} }
    }

    private fun click() {
        ensureCursor()
        val p = Path().apply { moveTo(cx, cy) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    private fun scroll(down: Boolean) {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val a = dm.heightPixels * (if (down) 0.7f else 0.3f)
        val b = dm.heightPixels * (if (down) 0.3f else 0.7f)
        val p = Path().apply { moveTo(x, a); lineTo(x, b) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 300)).build(), null, null)
    }
}
