package app.hearby

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/** Handles messages arriving from your partner and what you've sent. */
object Inbox {
    const val ACTION_REFRESH = "app.hearby.REFRESH"
    private const val DAY = 24 * 60 * 60 * 1000L

    /** Kinds that are love from your partner: they buzz the heartbeat and show on the widget. */
    val LOVE = setOf("text", "kiss", "heart", "hug", "photos")

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
        val text = j.optString("text")
        val data = j.optString("data")
        val at = j.optLong("at", System.currentTimeMillis()).coerceAtMost(System.currentTimeMillis())
        val now = System.currentTimeMillis()
        // Anything from their phone means it's connected.
        if (now - at < 10 * 60 * 1000L) pr.partnerSeenAt = maxOf(pr.partnerSeenAt, at)
        if (kind == "presence") {
            if (now - at < 5 * 60 * 1000L) {
                val share = text != "hidden"
                pr.partnerShares = share
                if (text == "open" || text == "away" || text == "hidden") { pr.partnerState = text; pr.partnerStateAt = at }
                // They just opened Hearby and I'm in it too: say hi back right away so we both see it.
                if (text == "open" && now - pr.meOpenAt < 90 * 1000L) Presence.send(ctx, "open", force = false)
            }
            refresh(ctx)
            return
        }
        remember(pr, kind, text, data, at)
        when {
            kind == "felt" -> {
                pr.feltAt = System.currentTimeMillis()
                Notifier.felt(ctx, name)
            }
            kind == "look" -> {
                // Keep the partner's character type in step, so the widget draws them right.
                val type = try { JSONObject(data).optString("type") } catch (e: Exception) { "" }
                when (type) { "man" -> pr.partnerLook = "man"; "hijabi" -> pr.partnerLook = "hijab"; "woman" -> pr.partnerLook = "woman" }
            }
            kind == "touch" -> {
                // Only while they're actually holding (and not if you're already in Hearby, where it shows live).
                if (kotlin.math.abs(now - at) < 60 * 1000L && now - pr.meOpenAt > 90 * 1000L) Notifier.touch(ctx, name)
            }
            kind in LOVE -> {
                pr.lastKind = kind
                pr.lastText = when (kind) {
                    "photos" -> "📷 ${text.ifBlank { "sent you a photo" }}"
                    else -> text
                }
                pr.lastAt = at
                pr.feltSent = false
                Notifier.heartbeat(ctx, name)
            }
            // "sync" (moods, answers, special days…) arrives quietly.
        }
        refresh(ctx)
    }

    /** Keeps recent messages so the app screen can catch up when it opens. */
    private fun remember(pr: Prefs, kind: String, text: String, data: String, at: Long) {
        val list = try { JSONArray(pr.inbox) } catch (e: Exception) { JSONArray() }
        val keep = JSONArray()
        val cutoff = System.currentTimeMillis() - 3 * DAY
        var photos = 0
        // Newest first while trimming, so big photo messages don't pile up.
        val items = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.optLong("at") > cutoff } +
            JSONObject().put("kind", kind).put("text", text).put("data", data).put("at", at)
        val trimmed = items.reversed().filter { if (it.optString("kind") == "photos") ++photos <= 3 else true }.take(60).reversed()
        trimmed.forEach { keep.put(it) }
        pr.inbox = keep.toString()
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
        "photos" -> "📷 photos"
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
            "photos" -> "📷 $name sent you photos"
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
