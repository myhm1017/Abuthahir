package app.hearby

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** The screen (app or widget pop-up) that hosts the Hearby design. */
interface BridgeHost {
    val activity: Activity
    /** "app" for the full app, "widget" for the expanded widget pop-up. */
    val mode: String
    val web: WebView?
    /** Where the home-screen widget sits, as a fraction of the screen height (widget pop-up only). */
    val top: Float get() = 0f
    fun openSettings()
    fun themeChanged() {}
    fun ready() {}
    fun close() {}
}

/** What the Hearby design can ask the phone to do. Called on a background thread, so UI work is posted. */
class HearbyBridge(private val host: BridgeHost) {
    private val act get() = host.activity
    private val pr get() = Prefs(act)
    private fun ui(f: () -> Unit) = act.runOnUiThread(f)

    @JavascriptInterface
    fun config(): String = JSONObject()
        .put("mode", host.mode).put("top", host.top.toDouble())
        .put("myName", pr.myName).put("partnerName", pr.partnerName)
        .put("myLook", pr.myLook).put("partnerLook", pr.partnerLook)
        .put("hue", Palette.hue(pr)).put("dark", Palette.isDark(act, pr))
        .put("widgetAspect", pr.widgetAspect.toDouble())
        .put("frameScene", if (host.mode == "widget" && Frames.count(act) > 0) pr.frameScene else "")
        // Which place the small widget is showing now (each shows for 5 seconds), so the pop-up opens on it.
        .put("frameIndex", ((System.currentTimeMillis() - pr.frameShownAt).coerceAtLeast(0) / 5000).toInt())
        .toString()

    @JavascriptInterface
    fun send(kind: String, text: String, data: String) {
        if (kind !in setOf("text", "kiss", "heart", "hug", "photos", "felt", "sync", "look", "touch")) return
        Relay.send(act, kind, text, data) { ok ->
            if (!ok && kind != "sync" && kind != "look") Toast.makeText(act, "Couldn't send. Check your internet and try again.", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface fun inbox(): String { pr.seenAt = System.currentTimeMillis(); return pr.inbox }
    @JavascriptInterface fun status(): String = pr.status
    @JavascriptInterface fun batteryOk(): Boolean = act.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(act.packageName)

    /** A number both phones show; if they match, nobody else is in the middle. */
    @JavascriptInterface
    fun safety(): String {
        val h = MessageDigest.getInstance("SHA-256").digest(("hearby-safety:" + HearbyCrypto.normalize(pr.code)).toByteArray())
        return h.take(12).joinToString("") { ((it.toInt() and 0xFF) % 10).toString() }.chunked(4).joinToString(" ")
    }

    @JavascriptInterface
    fun setMyLook(look: String) {
        if (look !in setOf("man", "hijab", "woman") || look == pr.myLook) return
        pr.myLook = look
        Inbox.refresh(act)
        ui {
            host.web?.evaluateJavascript("document.documentElement.dataset.hue='${Palette.hue(pr)}'", null)
            host.themeChanged()
        }
    }

    @JavascriptInterface
    fun setPartnerLook(look: String) {
        if (look in setOf("man", "hijab", "woman") && look != pr.partnerLook) { pr.partnerLook = look; Inbox.refresh(act) }
    }

    @JavascriptInterface
    fun setNames(my: String, partner: String) {
        if (my.isNotBlank()) pr.myName = my.trim().take(20)
        if (partner.isNotBlank()) pr.partnerName = partner.trim().take(20)
        Inbox.refresh(act)
    }

    // The widget's animation loop, painted by the design.
    @JavascriptInterface fun framesBegin(): Boolean = Frames.begin(act)
    @JavascriptInterface fun frame(i: Int, dataUrl: String) = Frames.put(act, i, dataUrl)
    @JavascriptInterface fun framesScene(json: String) { if (json.length < 4000) pr.frameScene = json }
    @JavascriptInterface fun framesDone(n: Int, ms: Int) = Frames.done(act, n, ms)

    /** Kept for older pages: a single still picture of the scene. */
    @JavascriptInterface
    fun saveScene(dataUrl: String) {
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:image/png") || comma < 0) return
        try {
            val f = SceneRenderer.sceneFile(act)
            val tmp = File(f.path + ".tmp")
            tmp.writeBytes(Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT))
            tmp.renameTo(f)
            HearbyWidget.updateAll(act.applicationContext)
        } catch (e: Exception) { /* keep the old picture */ }
    }

    @JavascriptInterface fun dirty() { if (host.mode == "widget") pr.webDirty = true }
    @JavascriptInterface fun ready() = ui { host.ready() }
    @JavascriptInterface fun close() = ui { host.close() }

    @JavascriptInterface
    fun openApp() = ui {
        act.startActivity(Intent(act, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        if (host.mode == "widget") act.finish()
    }

    /**
     * "Use my location for distance": a rough position (city level), sent back to the page.
     * Returns "asking" while Android asks for permission, "off" if location is off, otherwise "ok".
     */
    @JavascriptInterface
    fun locate(): String {
        if (act.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ui { act.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 21) }
            return "asking"
        }
        val lm = act.getSystemService(LocationManager::class.java)
        if (!lm.isLocationEnabled) return "off"
        val deliver = { l: Location? ->
            val js = if (l == null) "window.hearbyLocated && window.hearbyLocated(null, null)"
            else "window.hearbyLocated && window.hearbyLocated(${"%.2f".format(java.util.Locale.US, l.latitude)}, ${"%.2f".format(java.util.Locale.US, l.longitude)})"
            ui { host.web?.evaluateJavascript(js, null) }
        }
        try {
            val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .maxByOrNull { it.time }
            if (last != null && System.currentTimeMillis() - last.time < 6 * 60 * 60 * 1000L) { deliver(last); return "ok" }
            if (Build.VERSION.SDK_INT >= 30 && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, act.mainExecutor) { deliver(it ?: last) }
            } else deliver(last)
        } catch (e: SecurityException) { deliver(null) }
        return "ok"
    }

    @JavascriptInterface fun photoAdd(id: String, album: String, dataUrl: String): Boolean = PhotoStore.add(act, id, album, dataUrl)
    @JavascriptInterface fun photoMissing(id: String) = PhotoStore.missing(act, id)

    @JavascriptInterface fun presence(state: String) { if (state == "open" || state == "away") Presence.send(act, state) }
    @JavascriptInterface fun sharePresence(): Boolean = pr.sharePresence
    @JavascriptInterface
    fun setSharePresence(on: Boolean) {
        pr.sharePresence = on
        Presence.send(act, if (on) "open" else "hidden", force = true)
    }

    @JavascriptInterface
    fun partnerStatus(): String = JSONObject()
        .put("seenAt", pr.partnerSeenAt).put("state", pr.partnerState).put("stateAt", pr.partnerStateAt)
        .put("share", pr.partnerShares).put("now", System.currentTimeMillis())
        .toString()

    @JavascriptInterface fun settings() = ui { host.openSettings() }
    @JavascriptInterface fun heartbeat() = ui { Notifier.vibrateNow(act) }

    @JavascriptInterface
    fun pinWidget() = ui {
        val mgr = AppWidgetManager.getInstance(act)
        if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(ComponentName(act, HearbyWidget::class.java), null, null)
        else Toast.makeText(act, "Long-press your home screen, tap Widgets, then find Hearby", Toast.LENGTH_LONG).show()
    }

    @JavascriptInterface
    fun battery() = ui {
        try {
            @Suppress("BatteryLife")
            act.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${act.packageName}")))
        } catch (e: Exception) {
            act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
