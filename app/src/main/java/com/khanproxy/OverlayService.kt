package com.khanproxy

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.lifecycle.Observer

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var title: TextView
    private lateinit var params: WindowManager.LayoutParams
    private var observer: Observer<List<TokenEntry>>? = null
    private var minimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            build(); observe()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun build() {
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(20, 12, 20, 12)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#0A0E17"))
        bg.cornerRadius = 20f
        bg.setStroke(3, Color.parseColor("#00E5FF"))
        box.background = bg
        box.elevation = 16f

        val h = LinearLayout(this); h.orientation = LinearLayout.HORIZONTAL
        title = TextView(this)
        title.text = "● 0 tokens · :8080"
        title.setTextColor(Color.parseColor("#00E5FF"))
        title.textSize = 12f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setPadding(0, 0, 12, 0)
        h.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val minB = Button(this); minB.text = "−"
        minB.setTextColor(Color.WHITE); minB.textSize = 14f
        minB.setBackgroundColor(Color.parseColor("#141F2C"))
        h.addView(minB, LinearLayout.LayoutParams(80, 80))

        val clB = Button(this); clB.text = "✕"
        clB.setTextColor(Color.WHITE); clB.textSize = 14f
        clB.setBackgroundColor(Color.parseColor("#FF3045"))
        h.addView(clB, LinearLayout.LayoutParams(80, 80))
        box.addView(h)

        val sv = ScrollView(this)
        sv.layoutParams = LinearLayout.LayoutParams(720, 520)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 8, 0, 8)
        sv.addView(list)
        box.addView(sv)

        minB.setOnClickListener {
            minimized = !minimized
            sv.visibility = if (minimized) LinearLayout.GONE else LinearLayout.VISIBLE
        }
        clB.setOnClickListener { stopSelf() }

        box.setOnTouchListener(object : View.OnTouchListener {
            private var iX = 0; private var iY = 0; private var tX = 0f; private var tY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { iX = params.x; iY = params.y; tX = e.rawX; tY = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = iX + (e.rawX - tX).toInt()
                        params.y = iY + (e.rawY - tY).toInt()
                        try { wm.updateViewLayout(box, params) } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })

        val t = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, t,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 150 }

        try { wm.addView(box, params) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun observe() {
        observer = Observer { entries ->
            try {
                title.text = "● ${entries.size} token(s) · :8080"
                list.removeAllViews()
                for (e in entries.take(20)) {
                    val r = LinearLayout(this)
                    r.orientation = LinearLayout.VERTICAL
                    r.setPadding(6, 8, 6, 8)

                    val color = if (e.mode == "JWT") "#00E676" else "#9C4DFF"

                    val tv1 = TextView(this)
                    tv1.text = "✔ ${e.mode} · ${e.region}" + (if (e.player.isNotEmpty() && e.player != "—") " · ${e.player}" else "")
                    tv1.setTextColor(Color.parseColor(color))
                    tv1.textSize = 11f
                    tv1.typeface = Typeface.DEFAULT_BOLD
                    r.addView(tv1)

                    val payload = if (e.mode == "JWT") e.jwt else "OpenID: ${e.openId}\nToken: ${e.accessToken}"
                    val tv2 = TextView(this)
                    tv2.text = payload.take(220)
                    tv2.setTextColor(Color.parseColor("#F5F7FA"))
                    tv2.textSize = 9f
                    tv2.typeface = Typeface.MONOSPACE
                    r.addView(tv2)

                    list.addView(r)
                }
            } catch (_: Exception) {}
        }
        TokenStore.tokens.observeForever(observer!!)
    }

    override fun onDestroy() {
        try {
            observer?.let { TokenStore.tokens.removeObserver(it) }
            wm.removeView(box)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
