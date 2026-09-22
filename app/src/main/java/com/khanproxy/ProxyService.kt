package com.khanproxy

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.util.regex.Pattern

class ProxyService : Service() {
    companion object {
        const val CH = "khanproxy_ch"
        const val ID = 2001
        const val TAG = "KhanProxy"
    }

    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            mkChannel()
            startForeground(ID, mkNotif("Starting..."))
        } catch (e: Exception) { Log.e(TAG, "onCreate", e) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == "STOP") { stopProxy(); return START_NOT_STICKY }
            if (server != null) return START_STICKY
            ProxyState.set(Status.STARTING)
            startProxy()
        } catch (e: Exception) {
            ProxyState.set(Status.ERROR, e.message ?: "Error")
        }
        return START_STICKY
    }

    private fun startProxy() {
        scope.launch {
            try {
                val probe = ServerSocket()
                probe.reuseAddress = true
                try { probe.bind(InetSocketAddress(ProxyState.HOST, ProxyState.PORT)) }
                catch (e: Exception) {
                    try { probe.close() } catch (_: Exception) {}
                    ProxyState.set(Status.ERROR, "Port ${ProxyState.PORT} is busy")
                    stopSelf(); return@launch
                }
                try { probe.close() } catch (_: Exception) {}

                val srv = ServerSocket()
                srv.reuseAddress = true
                srv.bind(InetSocketAddress(ProxyState.HOST, ProxyState.PORT))
                server = srv
                ProxyState.set(Status.RUNNING)
                updateNotif("● Running :${ProxyState.PORT} · ${ProxyState.region}")

                acceptJob = scope.launch {
                    while (isActive && server != null) {
                        try {
                            val client = srv.accept()
                            activeSockets.add(client)
                            launch { handleClient(client) }
                        } catch (e: Exception) {
                            if (server?.isClosed == true) break
                        }
                    }
                }
            } catch (e: Exception) {
                ProxyState.set(Status.ERROR, e.message ?: "Unknown")
                stopSelf()
            }
        }
    }

    private fun stopProxy() {
        try {
            ProxyState.set(Status.STOPPING)
            try { acceptJob?.cancel() } catch (_: Exception) {}
            try { server?.close() } catch (_: Exception) {}
            server = null
            for (s in activeSockets) { try { s.close() } catch (_: Exception) {} }
            activeSockets.clear()
            ProxyState.set(Status.STOPPED)
            updateNotif("● Stopped")
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "stop", e) }
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.soTimeout = 30000
            val cin = BufferedInputStream(client.getInputStream())
            val cout = BufferedOutputStream(client.getOutputStream())

            val requestLine = readLine(cin) ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 3) return@withContext
            val method = parts[0].uppercase()
            val target = parts[1]

            val reqHeaders = LinkedHashMap<String, String>()
            while (true) {
                val h = readLine(cin) ?: break
                if (h.isEmpty()) break
                val idx = h.indexOf(":")
                if (idx > 0) reqHeaders[h.substring(0, idx).trim()] = h.substring(idx + 1).trim()
            }

            if (method == "CONNECT") {
                val host = target.substringBefore(":")
                val port = target.substringAfter(":", "443").toIntOrNull() ?: 443
                try {
                    val upstream = Socket()
                    upstream.connect(InetSocketAddress(host, port), 15000)
                    cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    cout.flush()
                    val t1 = launch { pipe(cin, upstream.getOutputStream()) }
                    val t2 = launch { pipe(upstream.getInputStream(), cout) }
                    t1.join(); t2.join()
                    try { upstream.close() } catch (_: Exception) {}
                } catch (e: Exception) {
                    try { cout.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray()); cout.flush() } catch (_: Exception) {}
                }
                return@withContext
            }

            var body = ByteArray(0)
            val cl = reqHeaders.entries.firstOrNull { it.key.equals("Content-Length", true) }?.value?.toIntOrNull() ?: 0
            if (cl in 1..2097152) {
                body = ByteArray(cl)
                var read = 0
                while (read < cl) {
                    val r = cin.read(body, read, cl - read)
                    if (r <= 0) break
                    read += r
                }
            }

            val pathLow = target.lowercase().trimEnd('/')
            val isTarget = pathLow.endsWith("/getlogindata") || pathLow == "getlogindata"

            var path = target
            var host = reqHeaders.entries.firstOrNull { it.key.equals("Host", true) }?.value ?: ProxyState.targetHost
            if (path.startsWith("http://") || path.startsWith("https://")) {
                try {
                    val u = java.net.URL(path); host = u.host; path = u.file.ifEmpty { "/" }
                } catch (_: Exception) {}
            }

            val targetHost = if (isTarget) ProxyState.targetHost else host
            val useSSL = path.startsWith("https") || host.endsWith("ggpolarbear.com") ||
                         host.endsWith("freefiremobile.com") || isTarget
            val targetPort = if (useSSL) 443 else 80

            val upstream: Socket = if (useSSL) {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(targetHost, targetPort) as SSLSocket
                ssl.startHandshake(); ssl
            } else {
                Socket().apply { connect(InetSocketAddress(targetHost, targetPort), 15000) }
            }

            val uOut = BufferedOutputStream(upstream.getOutputStream())
            val uIn = BufferedInputStream(upstream.getInputStream())

            val sb = StringBuilder()
            sb.append("$method $path HTTP/1.1\r\n")
            for ((k, v) in reqHeaders) {
                if (k.equals("Proxy-Connection", true)) continue
                sb.append("$k: $v\r\n")
            }
            sb.append("Connection: close\r\n\r\n")
            uOut.write(sb.toString().toByteArray())
            if (body.isNotEmpty()) uOut.write(body)
            uOut.flush()

            val statusLine = readLine(uIn) ?: ""
            val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0

            val respHeaders = LinkedHashMap<String, String>()
            while (true) {
                val h = readLine(uIn) ?: break
                if (h.isEmpty()) break
                val idx = h.indexOf(":")
                if (idx > 0) respHeaders[h.substring(0, idx).trim()] = h.substring(idx + 1).trim()
            }

            val respBody = uIn.readBytes()

            if (isTarget) {
                try { intercept(method, reqHeaders, body, respBody) } catch (e: Exception) { Log.e(TAG, "intercept", e) }
            }

            val respSb = StringBuilder()
            respSb.append(statusLine).append("\r\n")
            for ((k, v) in respHeaders) {
                if (k.equals("Transfer-Encoding", true)) continue
                respSb.append("$k: $v\r\n")
            }
            respSb.append("\r\n")
            cout.write(respSb.toString().toByteArray())
            if (respBody.isNotEmpty()) cout.write(respBody)
            cout.flush()

            try { upstream.close() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "handleClient", e)
        } finally {
            activeSockets.remove(client)
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun intercept(method: String, headers: Map<String, String>, body: ByteArray, respBody: ByteArray) {
        try {
            val player = decodeResponseName(respBody)

            if (ProxyState.mode == 1) {
                var jwt = ""
                val jwtPattern = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                for ((_, v) in headers) {
                    if (v.startsWith("Bearer ", true)) { jwt = v.substring(7).trim(); break }
                    val m = jwtPattern.matcher(v)
                    if (m.find()) { jwt = m.group(0); break }
                }
                if (jwt.isEmpty() && body.isNotEmpty()) {
                    val m = jwtPattern.matcher(String(body, Charsets.UTF_8))
                    if (m.find()) jwt = m.group(0)
                }
                if (jwt.isEmpty()) return

                val pl = jwtNick(jwt).ifEmpty { player }
                TokenStore.add(TokenEntry(
                    id = TokenStore.nextId(),
                    mode = "JWT",
                    player = pl,
                    region = ProxyState.region,
                    jwt = jwt
                ))
            } else {
                var openId = ""; var accessToken = ""
                if (body.isNotEmpty() && body.size >= 16) {
                    val proto = CryptoHelper.aesDecrypt(body)
                    if (proto.isNotEmpty()) {
                        val fields = ProtoHelper.extract(proto, 22, 29)
                        openId = fields[22] ?: ""
                        accessToken = fields[29] ?: ""
                    }
                }
                if (openId.isEmpty() && accessToken.isEmpty() && body.isNotEmpty()) {
                    val fields = ProtoHelper.extract(body, 22, 29)
                    openId = fields[22] ?: ""
                    accessToken = fields[29] ?: ""
                }
                if (openId.isEmpty() && accessToken.isEmpty()) return

                TokenStore.add(TokenEntry(
                    id = TokenStore.nextId(),
                    mode = "ACCESS",
                    player = player,
                    region = ProxyState.region,
                    openId = openId,
                    accessToken = accessToken
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "intercept inner", e)
        }
    }

    private fun decodeResponseName(respBody: ByteArray): String {
        return try {
            var body = respBody
            if (body.size >= 16) {
                val dec = CryptoHelper.aesDecrypt(body)
                if (dec.isNotEmpty()) body = dec
            }
            val fields = ProtoHelper.extract(body, 4)
            fields[4] ?: "—"
        } catch (_: Exception) { "—" }
    }

    private fun jwtNick(jwt: String): String {
        return try {
            val parts = jwt.split(".")
            if (parts.size != 3) return ""
            val pad = (4 - parts[1].length % 4) % 4
            val payload = Base64.decode(parts[1] + "=".repeat(pad), Base64.URL_SAFE or Base64.NO_WRAP)
            val json = JSONObject(String(payload, Charsets.UTF_8))
            json.optString("nickname", "")
        } catch (_: Exception) { "" }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder(); var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code && prev == '\r'.code) { sb.deleteCharAt(sb.length - 1); return sb.toString() }
            sb.append(b.toChar()); prev = b
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        try {
            val buf = ByteArray(16384)
            while (true) {
                val n = input.read(buf); if (n <= 0) break
                output.write(buf, 0, n); output.flush()
            }
        } catch (_: Exception) {}
    }

    private fun mkChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Khan Proxy", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun mkNotif(text: String): Notification =
        NotificationCompat.Builder(this, CH)
            .setContentTitle("Khan Proxy")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).build()

    private fun updateNotif(text: String) {
        try { (getSystemService(NotificationManager::class.java)).notify(ID, mkNotif(text)) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { acceptJob?.cancel() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        for (s in activeSockets) { try { s.close() } catch (_: Exception) {} }
        activeSockets.clear()
        scope.cancel()
        ProxyState.set(Status.STOPPED)
        super.onDestroy()
    }
}
