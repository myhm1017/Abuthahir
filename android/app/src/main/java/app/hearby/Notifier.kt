package app.hearby

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Notifier {
    private const val CH_BEAT = "heartbeat_v1"
    private const val CH_SOFT = "soft_v1"
    private const val CH_SERVICE = "service_v1"

    /** The signature 2-beat heartbeat: ba-dum. */
    val PATTERN = longArrayOf(0, 120, 90, 200)

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_BEAT, "Heartbeats", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "The 2-beat heartbeat when your person thinks of you"
                enableVibration(true)
                vibrationPattern = PATTERN
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SOFT, "Quiet updates", NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Staying connected", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun heartbeat(ctx: Context, name: String) {
        channels(ctx)
        if (!canPost(ctx)) { vibrateNow(ctx); return }
        val public = Notification.Builder(ctx, CH_BEAT)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("💗 Hearby")
            .build()
        val n = Notification.Builder(ctx, CH_BEAT)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("💗 Hearby")
            .setContentText("$name is thinking of you")
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(1001, n)
    }

    fun felt(ctx: Context, name: String) {
        channels(ctx)
        if (!canPost(ctx)) return
        val n = Notification.Builder(ctx, CH_SOFT)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("💗 $name felt it")
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(1002, n)
    }

    fun service(ctx: Context): Notification {
        channels(ctx)
        val name = Prefs(ctx).partnerName.ifBlank { "your person" }
        return Notification.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("Hearby is connected")
            .setContentText("Listening for heartbeats from $name")
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .build()
    }

    fun vibrateNow(ctx: Context) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
        v.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    }

    fun tick(ctx: Context) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
        v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
