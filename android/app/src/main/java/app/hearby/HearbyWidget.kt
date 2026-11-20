package app.hearby

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/** The 4x4 home-screen widget: your couple scene, the latest love, and one-tap buttons. */
class HearbyWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
        if (Prefs(ctx).paired) {
            try { HearbyService.start(ctx) } catch (e: Exception) { /* app must be opened once */ }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(ctx, mgr, id)
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, HearbyWidget::class.java)).forEach { update(ctx, mgr, it) }
        }

        private fun action(ctx: Context, kind: String, req: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, WidgetActionReceiver::class.java).putExtra(WidgetActionReceiver.EXTRA_KIND, kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val pr = Prefs(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_hearby)

            val opts = mgr.getAppWidgetOptions(id)
            val wDp = max(140, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250))
            val hDp = max(140, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250))
            val d = ctx.resources.displayMetrics.density
            val scale = min(1f, 640f / (max(wDp, hDp) * d))
            val bw = (wDp * d * scale).toInt().coerceAtLeast(200)
            val bh = (hDp * d * scale).toInt().coerceAtLeast(200)
            v.setImageViewBitmap(R.id.scene, SceneRenderer.render(ctx, bw, bh))

            if (pr.paired) {
                val partner = pr.partnerName.ifBlank { "Your person" }
                val when_ = if (pr.lastKind.isNotEmpty() && System.currentTimeMillis() - pr.lastAt < 24 * 60 * 60 * 1000L) " · ${Inbox.ago(pr.lastAt)}" else ""
                v.setTextViewText(R.id.partner, "💗 $partner$when_")
                v.setTextViewText(R.id.message, Inbox.line(ctx))
            } else {
                v.setTextViewText(R.id.partner, "💗 Hearby")
                v.setTextViewText(R.id.message, "Open Hearby to pair with your person")
            }

            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.scene, open)
            v.setOnClickPendingIntent(R.id.message, open)
            v.setOnClickPendingIntent(R.id.partner, open)
            v.setOnClickPendingIntent(R.id.btn_kiss, action(ctx, "kiss", 11))
            v.setOnClickPendingIntent(R.id.btn_heart, action(ctx, "heart", 12))
            v.setOnClickPendingIntent(R.id.btn_hug, action(ctx, "hug", 13))
            v.setOnClickPendingIntent(R.id.btn_felt, action(ctx, "felt", 14))
            mgr.updateAppWidget(id, v)
        }
    }
}
