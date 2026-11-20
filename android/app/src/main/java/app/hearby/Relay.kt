package app.hearby

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends encrypted messages through ntfy.sh, a free public relay.
 * The relay only sees the random-looking topic name and encrypted text.
 */
object Relay {
    const val SERVER = "https://ntfy.sh"

    fun sendBlocking(ctx: Context, kind: String, text: String = "", data: String = ""): Boolean {
        val pr = Prefs(ctx)
        if (!pr.paired) return false
        val body = JSONObject()
            .put("v", 1)
            .put("from", pr.deviceId)
            .put("name", pr.myName)
            .put("kind", kind)
            .put("text", text)
            .put("at", System.currentTimeMillis())
            .apply { if (data.isNotEmpty()) put("data", data) }
            .toString()
        val enc = HearbyCrypto.encrypt(pr.code, body)
        return try {
            val c = URL("$SERVER/${HearbyCrypto.topicFor(pr.code)}").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15000
            c.readTimeout = 60000
            // Big messages (photos) are stored by the relay as an encrypted attachment for a few hours.
            if (enc.length > 3900) c.setRequestProperty("Filename", "hearby.txt")
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(enc.toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** Sends on a background thread and reports back on the main thread. */
    fun send(ctx: Context, kind: String, text: String = "", data: String = "", done: (Boolean) -> Unit = {}) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val ok = sendBlocking(app, kind, text, data)
            if (ok && kind in Inbox.LOVE + "felt") Inbox.recordSent(app, kind, text)
            main.post { done(ok) }
        }.start()
    }
}
