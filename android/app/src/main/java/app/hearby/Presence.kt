package app.hearby

import android.content.Context

/**
 * Tiny encrypted "I'm here" signals, so each of you can see whether the other's phone is connected
 * and whether they're in Hearby right now. States: "open" (in the app), "away" (left the app),
 * "alive" (phone connected, in the background) and "hidden" (chose not to share being in the app).
 */
object Presence {
    fun send(ctx: Context, state: String, force: Boolean = false) {
        val pr = Prefs(ctx)
        if (!pr.paired) return
        var st = state
        if (st == "open") pr.meOpenAt = System.currentTimeMillis()
        if (st == "away") pr.meOpenAt = 0
        if (!pr.sharePresence && (st == "open" || st == "away")) st = "hidden"
        val now = System.currentTimeMillis()
        // Don't repeat the same signal too often.
        val gap = if (st == "alive") 5 * 60 * 1000L else 40 * 1000L
        if (!force && st == pr.presenceSent && now - pr.presenceSentAt < gap) return
        pr.presenceSent = st
        pr.presenceSentAt = now
        Relay.send(ctx, "presence", st)
    }
}
