package com.khanproxy

import android.os.Environment
import java.io.File

object FileHelper {
    private val FF_PKGS = listOf(
        "com.dts.freefiremax",
        "com.dts.freefireth"
    )

    fun getFFFilesDir(): File? {
        for (pkg in FF_PKGS) {
            try {
                val dir = File(Environment.getExternalStorageDirectory(),
                    "Android/data/$pkg/files")
                if (dir.exists() || dir.mkdirs()) return dir
            } catch (_: Exception) {}
        }
        return null
    }

    fun writeLocalConfig(): Boolean {
        return try {
            val dir = getFFFilesDir() ?: return false
            val f = File(dir, "localconfig.json")
            f.writeText("{\"serverLoginUrl\":\"http://127.0.0.1:8080/\"}")
            true
        } catch (_: Exception) { false }
    }
}
