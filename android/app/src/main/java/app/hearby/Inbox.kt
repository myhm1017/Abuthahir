package app.hearby

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Handles messages arriving from your partner and what you've sent. */
object Inbox {
    const val ACTION_REFRESH = "app.hearby.REFRESH"
    private const val DAY = 24 * 60 * 60 * 1000L

    fun handle(ctx: Context, id: String, payload: String) {
        val pr = Prefs(ctx)
        if (id.isNotEmpty()) {
            if (id == pr.lastId) return
            pr.lastId = id
        }
        val plain = HearbyCrypto.decrypt(pr.code, payload) ?: return
        val j = try { JSONObject(plain) } catch (e: Exception) { return }
        if (j.optString("from") == pr.deviceId) return

        val name = pr.partnerName.ifBlank { j.optString("name", "Your person") }
        val kind = j.optString("kind")
        if (kind == "felt") {
            pr.feltAt = System.currentTimeMillis()
            Notifier.felt(ctx, name)
        } else {
            pr.lastKind = kind
            pr.lastText = j.optString("text")
            pr.lastAt = j.optLong("at", System.currentTimeMillis()).coerceAtMost(System.currentTimeMillis())
            pr.feltSent = false
            Notifier.heartbeat(ctx, name)
        }
        refresh(ctx)
    }

    fun recordSent(ctx: Context, kind: String, text: String) {
        val pr = Prefs(ctx)
        if (kind == "felt") pr.feltSent = true
        else {
            pr.sentNote = "You sent ${label(kind, text)}"
            pr.sentAt = System.currentTimeMillis()
        }
        refresh(ctx)
    }

    fun refresh(ctx: Context) {
        HearbyWidget.updateAll(ctx)
        ctx.sendBroadcast(Intent(ACTION_REFRESH).setPackage(ctx.packageName))
    }

    fun label(kind: String, text: String) = when (kind) {
        "kiss" -> "💋 a kiss"
        "heart" -> "❤️ love"
        "hug" -> "🤗 a hug"
        else -> "\"$text\""
    }

    fun emojiFor(kind: String, text: String): String = when (kind) {
        "kiss" -> "💋"
        "heart" -> "💖"
        "hug" -> "🤗"
        else -> when {
            text.contains("night", true) || text.contains("🌙") -> "🌙"
            text.contains("miss", true) -> "🥺"
            else -> "💗"
        }
    }

    /** The line shown on the widget and in the app. */
    fun line(ctx: Context): String {
        val pr = Prefs(ctx)
        val name = pr.partnerName.ifBlank { "Your person" }
        val now = System.currentTimeMillis()
        if (now - pr.feltAt < 10 * 60 * 1000L) return "💗 $name felt it"
        if (pr.lastKind.isEmpty() || now - pr.lastAt > DAY) {
            return if (now - pr.sentAt < 10 * 60 * 1000L) "${pr.sentNote} 💌" else "Thinking of $name? Send a little love 💗"
        }
        return when (pr.lastKind) {
            "kiss" -> "💋 $name sent you a kiss"
            "heart" -> "❤️ $name sent you love"
            "hug" -> "🤗 $name sent you a hug"
            else -> pr.lastText
        }
    }

    fun ago(at: Long): String {
        val m = ((System.currentTimeMillis() - at) / 60000).toInt()
        return when {
            m < 1 -> "just now"
            m < 60 -> "${m}m ago"
            else -> "${m / 60}h ago"
        }
    }
}
