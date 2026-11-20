package app.hearby

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps a quiet connection to the relay so heartbeats arrive even when the app is closed.
 * Shows a small "Hearby is connected" notification, as Android requires.
 */
class HearbyService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null
    @Volatile private var conn: HttpURLConnection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.service(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(42, n)
        }
        if (intent?.getBooleanExtra(EXTRA_RECONNECT, false) == true) conn?.disconnect()
        if (!running) {
            running = true
            worker = Thread({ loop() }, "hearby-relay").also { it.start() }
        }
        return START_STICKY
    }

    private fun nap(ms: Long) {
        try { Thread.sleep(ms) } catch (e: InterruptedException) { /* stopping */ }
    }

    private fun loop() {
        var backoff = 2000L
        while (running) {
            val pr = Prefs(this)
            if (!pr.paired) { nap(5000); continue }
            try {
                val since = pr.lastId.ifEmpty { "1m" }
                val c = URL("${Relay.SERVER}/${HearbyCrypto.topicFor(pr.code)}/json?since=$since")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 120000
                conn = c
                c.inputStream.bufferedReader().use { r ->
                    setStatus("connected")
                    backoff = 2000L
                    while (running) {
                        val line = r.readLine() ?: break
                        if (line.isBlank()) continue
                        val j = try { JSONObject(line) } catch (e: Exception) { continue }
                        if (j.optString("event") == "message") {
                            val att = j.optJSONObject("attachment")
                            val body = if (att != null) download(att.optString("url")) else j.optString("message")
                            if (body != null) Inbox.handle(this, j.optString("id"), body.trim())
                        }
                    }
                }
                c.disconnect()
            } catch (e: Exception) {
                setStatus("offline")
                nap(backoff)
                backoff = (backoff * 2).coerceAtMost(60000)
            }
        }
    }

    /** Photos travel as an encrypted attachment; fetch it (it is still encrypted). */
    private fun download(url: String): String? = try {
        if (!url.startsWith(Relay.SERVER)) null
        else {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 60000
            val t = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            t
        }
    } catch (e: Exception) { null }

    private fun setStatus(s: String) {
        val pr = Prefs(this)
        if (pr.status != s) {
            pr.status = s
            Inbox.refresh(this)
        }
    }

    override fun onDestroy() {
        running = false
        conn?.disconnect()
        worker?.interrupt()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_RECONNECT = "reconnect"

        fun start(ctx: Context, reconnect: Boolean = false) {
            val i = Intent(ctx, HearbyService::class.java).putExtra(EXTRA_RECONNECT, reconnect)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, HearbyService::class.java))
        }
    }
}
