package com.khanproxy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var selectedRegionKey = "1"
    private var selectedMode = 1
    private lateinit var adapter: TokenAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            requestNotif()
            requestStorage()
            setupRegionSpinner()
            setupModeRadio()
            setupButtons()
            setupTokenList()
            observe()
            autoDeployConfig()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupRegionSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerRegion)
        val regionLabels = ProxyState.REGIONS.values.map { "${it.first} — ${it.second}" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, regionLabels)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedRegionKey = (pos + 1).toString()
                val reg = ProxyState.REGIONS[selectedRegionKey]!!
                ProxyState.region = reg.first
                ProxyState.targetHost = reg.second
                findViewById<TextView>(R.id.tvRegionInfo).text = "https://${reg.second}/"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupModeRadio() {
        val rgMode = findViewById<RadioGroup>(R.id.rgMode)
        rgMode.setOnCheckedChangeListener { _, id ->
            selectedMode = if (id == R.id.rbJwt) 1 else 2
            ProxyState.mode = selectedMode
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnStart).setOnClickListener { startProxy() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopProxy() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { showOverlay() }
        findViewById<Button>(R.id.btnWriteConfig).setOnClickListener { writeConfig() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { confirmClear() }
    }

    private fun setupTokenList() {
        adapter = TokenAdapter()
        findViewById<RecyclerView>(R.id.rvTokens).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        TokenStore.tokens.observe(this) { adapter.submit(it) }
    }

    private fun autoDeployConfig() {
        // Auto-deploy localconfig.json on app start
        try {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                // Request permission silently; will prompt user once
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
                return
            }
            val ok = FileHelper.writeLocalConfig()
            if (ok) {
                Toast.makeText(this, "✔ localconfig.json auto-deployed", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear all tokens?")
            .setMessage("This will remove all captured tokens.")
            .setPositiveButton("CLEAR") { _, _ ->
                TokenStore.clear()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun writeConfig() {
        try {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
                Toast.makeText(this, "Grant All Files Access, then tap again", Toast.LENGTH_LONG).show()
                return
            }
            val ok = FileHelper.writeLocalConfig()
            if (ok) Toast.makeText(this, "✔ localconfig.json created", Toast.LENGTH_LONG).show()
            else Toast.makeText(this, "✘ Failed — check storage permission", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {}
    }

    private fun observe() {
        ProxyState.status.observe(this) { status ->
            try {
                val tv = findViewById<TextView>(R.id.tvStatus)
                val btnStart = findViewById<Button>(R.id.btnStart)
                val btnStop = findViewById<Button>(R.id.btnStop)
                when (status) {
                    Status.STOPPED -> {
                        tv.text = "● STOPPED"
                        tv.setTextColor(0xFFFFC107.toInt())
                        btnStart.isEnabled = true; btnStop.isEnabled = false
                    }
                    Status.STARTING -> { tv.text = "Starting..."; btnStart.isEnabled = false; btnStop.isEnabled = false }
                    Status.RUNNING -> {
                        tv.text = "● RUNNING :${ProxyState.PORT}"
                        tv.setTextColor(0xFF00E676.toInt())
                        btnStart.isEnabled = false; btnStop.isEnabled = true
                        pulseGlow(tv)
                    }
                    Status.STOPPING -> { tv.text = "Stopping..."; btnStart.isEnabled = false; btnStop.isEnabled = false }
                    Status.ERROR -> {
                        tv.text = "● ERROR"
                        tv.setTextColor(0xFFFF3045.toInt())
                        btnStart.isEnabled = true; btnStop.isEnabled = false
                    }
                }
            } catch (_: Exception) {}
        }
        TokenStore.tokens.observe(this) { list ->
            try {
                findViewById<TextView>(R.id.tvTokenCount).text = list.size.toString()
            } catch (_: Exception) {}
        }
    }

    private fun pulseGlow(v: View) {
        try {
            val anim = AlphaAnimation(1f, 0.6f)
            anim.duration = 800
            anim.repeatMode = Animation.REVERSE
            anim.repeatCount = Animation.INFINITE
            v.startAnimation(anim)
        } catch (_: Exception) {}
    }

    private fun startProxy() {
        try {
            val reg = ProxyState.REGIONS[selectedRegionKey]!!
            ProxyState.region = reg.first
            ProxyState.targetHost = reg.second
            ProxyState.mode = selectedMode
            val i = Intent(this, ProxyService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (_: Exception) {}
    }

    private fun stopProxy() {
        try {
            val i = Intent(this, ProxyService::class.java).apply { action = "STOP" }
            startService(i)
        } catch (_: Exception) {}
    }

    private fun showOverlay() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(i)
                return
            }
            startService(Intent(this, OverlayService::class.java))
        } catch (_: Exception) {}
    }

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {}
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 201)
        }
    }
}
