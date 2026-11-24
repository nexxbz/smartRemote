package com.smartremote.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (32 * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
        }
        fun text(t: String, sp: Float) = TextView(this).apply { this.text = t; textSize = sp; setTextColor(Color.WHITE) }
        root.addView(text("Smart Remote TV", 34f))
        info = text("", 24f).apply { setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(info)
        val b1 = Button(this).apply {
            text = "1) Activar servicio en Accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val b2 = Button(this).apply {
            text = "2) Permitir control de brillo (opcional)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        root.addView(b1); root.addView(b2)
        setContentView(root)
        b1.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        val on = RemoteAccessibilityService.instance != null
        info.text = "PIN: ${Util.pin(this)}\nIP: ${Util.localIp()}   Puerto: ${RemoteAccessibilityService.PORT}\n\n" +
            if (on) "Servicio ACTIVO. Ya puedes conectar desde el celular." else "Servicio apagado: actívalo en Accesibilidad."
    }
}
