package app.hearby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the 💋 ❤️ 🤗 💗 buttons on the home-screen widget. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val app = ctx.applicationContext
        val pr = Prefs(app)
        if (!pr.paired) return
        if (kind == "felt" && (pr.lastKind.isEmpty() || pr.feltSent)) return
        val pending = goAsync()
        try { Notifier.tick(app) } catch (e: Exception) { /* no vibrator */ }
        Thread {
            try {
                if (Relay.sendBlocking(app, kind)) Inbox.recordSent(app, kind, "")
                HearbyWidget.updateAll(app)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val EXTRA_KIND = "kind"
    }
}

/** Reconnects after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Prefs(ctx).paired) {
            try { HearbyService.start(ctx) } catch (e: Exception) { /* background start not allowed */ }
        }
        HearbyWidget.updateAll(ctx)
    }
}
