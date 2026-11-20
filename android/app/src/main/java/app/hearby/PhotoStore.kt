package app.hearby

import android.content.Context
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Shared album photos. Each photo is a file on both phones (filesDir/photos/<id>.jpg); the app shows it
 * from https://hearby.photos/<id>.jpg, which never leaves the phone (the WebView answers it from the file).
 * Photos travel to the partner end-to-end encrypted, like messages.
 */
object PhotoStore {
    private val ID = Regex("[a-z0-9]{6,32}")
    private val asked = HashMap<String, Long>()
    private val resent = HashMap<String, Long>()

    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }
    fun file(ctx: Context, id: String): File? = if (ID.matches(id)) File(dir(ctx), "$id.jpg") else null
    fun has(ctx: Context, id: String) = file(ctx, id)?.exists() == true

    fun save(ctx: Context, id: String, dataUrl: String): Boolean {
        val f = file(ctx, id) ?: return false
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:image/") || comma < 0 || dataUrl.length > 3_000_000) return false
        return try {
            val tmp = File(f.path + ".tmp")
            tmp.writeBytes(Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT))
            tmp.renameTo(f)
        } catch (e: Exception) { false }
    }

    private fun dataUrl(ctx: Context, id: String): String? = try {
        "data:image/jpeg;base64," + Base64.encodeToString(file(ctx, id)!!.readBytes(), Base64.NO_WRAP)
    } catch (e: Exception) { null }

    /** A photo you added: keep it and send it to your partner's phone. */
    fun add(ctx: Context, id: String, album: String, dataUrl: String): Boolean {
        if (!save(ctx, id, dataUrl)) return false
        send(ctx, id, album, dataUrl)
        return true
    }

    private fun send(ctx: Context, id: String, album: String, dataUrl: String) {
        val data = JSONObject().put("id", id).put("album", album.take(60)).put("src", dataUrl).toString()
        Relay.send(ctx, "photo", album.take(60), data)
    }

    /** A photo in our albums that hasn't arrived here: ask the other phone to send it again (at most every 10 minutes). */
    @Synchronized
    fun missing(ctx: Context, id: String) {
        if (!ID.matches(id) || has(ctx, id)) return
        val now = System.currentTimeMillis()
        if (now - (asked[id] ?: 0L) < 10 * 60 * 1000L) return
        asked[id] = now
        Relay.send(ctx, "photoReq", "", JSONObject().put("id", id).toString())
    }

    /** Your partner asked for a photo: send it again if this phone has it. */
    @Synchronized
    fun resend(ctx: Context, id: String) {
        if (!has(ctx, id)) return
        val now = System.currentTimeMillis()
        if (now - (resent[id] ?: 0L) < 10 * 60 * 1000L) return
        resent[id] = now
        dataUrl(ctx, id)?.let { send(ctx, id, "", it) }
    }

    /** Answers https://hearby.photos/<id>.jpg from the file on this phone. */
    fun intercept(ctx: Context, req: WebResourceRequest): WebResourceResponse? {
        val u = req.url
        if (u.host != "hearby.photos") return null
        val id = (u.lastPathSegment ?: "").removeSuffix(".jpg")
        val f = file(ctx, id)
        return if (f != null && f.exists()) WebResourceResponse("image/jpeg", null, FileInputStream(f))
        else WebResourceResponse("text/plain", "utf-8", 404, "Not found", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }
}
