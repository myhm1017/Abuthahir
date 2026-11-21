package app.hearby

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/** The 4x4 home-screen widget: the moving couple scene and the latest love. Tap it to expand. */
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

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val pr = Prefs(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_hearby)

            val opts = mgr.getAppWidgetOptions(id)
            val wDp = max(140, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250))
            val hDp = max(140, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250))
            pr.widgetAspect = wDp.toFloat() / hDp

            // The moving scene: a short loop the app painted. Before that, a still picture.
            val n = Frames.count(ctx)
            if (n > 0) {
                // Places crossfade every 5 seconds; inside each, a gentle loop of frames.
                val meta = try { org.json.JSONObject(pr.frameScene) } catch (e: Exception) { null }
                var per = meta?.optInt("per", n) ?: n
                if (per <= 0 || n % per != 0) per = n
                v.removeAllViews(R.id.flipper)
                for (g in 0 until n / per) {
                    val place = RemoteViews(ctx.packageName, R.layout.widget_place)
                    for (f in 0 until per) {
                        val fr = RemoteViews(ctx.packageName, R.layout.widget_frame)
                        fr.setImageViewUri(R.id.frame_img, Frames.uri(ctx, g * per + f))
                        place.addView(R.id.place_flipper, fr)
                    }
                    place.setInt(R.id.place_flipper, "setFlipInterval", pr.frameMs.toInt().coerceIn(150, 1000))
                    v.addView(R.id.flipper, place)
                }
                v.setInt(R.id.flipper, "setFlipInterval", 5000)
                pr.frameShownAt = System.currentTimeMillis()
                v.setViewVisibility(R.id.flipper, View.VISIBLE)
                v.setViewVisibility(R.id.scene, View.GONE)
                // Painted earlier in the day? Tint it so night still looks like night.
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val made = Calendar.getInstance().apply { timeInMillis = pr.frameVer }.get(Calendar.HOUR_OF_DAY)
                val mode = { h: Int -> when (h) { in 6..16 -> 0; in 17..19 -> 1; else -> 2 } }
                val tint = if (mode(hour) == mode(made)) 0 else when (mode(hour)) { 2 -> 0x6E141B46; 1 -> 0x38E8707A; else -> 0 }
                v.setInt(R.id.tint, "setBackgroundColor", tint)
            } else {
                val d = ctx.resources.displayMetrics.density
                val scale = min(1f, 640f / (max(wDp, hDp) * d))
                v.setImageViewBitmap(R.id.scene, SceneRenderer.render(ctx, (wDp * d * scale).toInt().coerceAtLeast(200), (hDp * d * scale).toInt().coerceAtLeast(200)))
                v.setViewVisibility(R.id.scene, View.VISIBLE)
                v.setViewVisibility(R.id.flipper, View.GONE)
                v.setInt(R.id.tint, "setBackgroundColor", 0)
            }

            val now = System.currentTimeMillis()
            if (pr.paired) {
                val partner = pr.partnerName.ifBlank { "Your person" }
                // 👀 while they're in Hearby right now.
                val viewing = pr.partnerShares && pr.partnerState == "open" && now - pr.partnerStateAt < 100 * 1000L
                v.setTextViewText(R.id.partner, if (viewing) "👀 $partner is here" else "💗 $partner")
                // Like the preview: the latest message sits in a pill at the bottom; nothing new, just the scene.
                val fresh = pr.lastKind.isNotEmpty() && now - pr.lastAt < 24 * 60 * 60 * 1000L
                val felt = now - pr.feltAt < 10 * 60 * 1000L
                val text = when {
                    felt -> "💗 $partner felt it"
                    fresh && pr.lastKind == "text" -> "$partner: ${pr.lastText}"
                    fresh -> Inbox.line(ctx)
                    else -> ""
                }
                v.setTextViewText(R.id.message, text)
                v.setViewVisibility(R.id.message, if (text.isEmpty()) View.GONE else View.VISIBLE)
                v.setViewVisibility(R.id.badge, if (fresh && pr.lastAt > pr.seenAt) View.VISIBLE else View.GONE)
            } else {
                v.setTextViewText(R.id.partner, "💗 Hearby")
                v.setTextViewText(R.id.message, "Tap to pair with your person")
                v.setViewVisibility(R.id.message, View.VISIBLE)
                v.setViewVisibility(R.id.badge, View.GONE)
            }

            // Tapping anywhere opens the expanded widget over the home screen.
            val open = PendingIntent.getActivity(
                ctx, 1,
                Intent(ctx, WidgetPopupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(android.R.id.background, open)
            mgr.updateAppWidget(id, v)
        }
    }
}
