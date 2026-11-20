package app.hearby

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Base64
import java.io.File
import java.security.MessageDigest

class MainActivity : Activity() {
    companion object { private const val PICK_PHOTOS = 7 }

    private lateinit var pr: Prefs
    private var sceneView: ImageView? = null
    private var partnerView: TextView? = null
    private var lineView: TextView? = null
    private var statusView: TextView? = null
    private var feltButton: Button? = null
    private var batteryButton: Button? = null
    private var web: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private lateinit var pal: Palette
    private val ink: Int get() = pal.ink
    private val muted: Int get() = pal.muted
    private val rose: Int get() = pal.accent

    private val refresh = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (web != null) pull() else if (pr.paired) renderMain() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pr = Prefs(this)
        Notifier.channels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        show()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val f = IntentFilter(Inbox.ACTION_REFRESH)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refresh, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(refresh, f)
        web?.onResume()
        if (pr.paired) {
            HearbyService.start(this)
            if (web != null) pull() else renderMain()
        }
    }

    override fun onPause() {
        super.onPause()
        web?.onPause()
        try { unregisterReceiver(refresh) } catch (e: Exception) { /* not registered */ }
    }

    private fun show() {
        pal = Palette.of(this)
        applyWindowColors()
        web?.destroy()
        web = null
        if (pr.paired) { if (!buildWeb()) buildMain() } else buildSetup()
    }

    // ---------- The app screen: the Hearby design, running in real phone mode ----------
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWeb(): Boolean {
        val wv = try { WebView(this) } catch (e: Exception) { return false }
        wv.setBackgroundColor(pal.bg)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100
            allowFileAccess = false
            allowContentAccess = false
        }
        wv.isVerticalScrollBarEnabled = false
        wv.addJavascriptInterface(Bridge(), "HearbyNative")
        wv.webViewClient = object : WebViewClient() {
            // Everything is inside the app; never open other pages.
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = true
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                }
                return try { startActivityForResult(Intent.createChooser(pick, "Choose photos"), PICK_PHOTOS); true } catch (e: Exception) { fileCallback = null; false }
            }
        }
        setContentView(wv)
        wv.loadUrl("file:///android_asset/hearby.html")
        web = wv
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTOS) return
        val cb = fileCallback ?: return
        fileCallback = null
        if (resultCode != RESULT_OK || data == null) { cb.onReceiveValue(null); return }
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris.add(c.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        cb.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    private fun pull() { web?.evaluateJavascript("window.hearbyPull && window.hearbyPull()", null) }

    private fun js(code: String) = runOnUiThread { web?.evaluateJavascript(code, null) }

    /** What the app screen can ask the phone to do. Runs on a background thread, so UI work is posted. */
    inner class Bridge {
        @JavascriptInterface
        fun config(): String = org.json.JSONObject()
            .put("myName", pr.myName).put("partnerName", pr.partnerName)
            .put("myLook", pr.myLook).put("partnerLook", pr.partnerLook)
            .put("hue", Palette.hue(pr)).put("dark", Palette.isDark(this@MainActivity, pr))
            .toString()

        @JavascriptInterface
        fun send(kind: String, text: String, data: String) {
            if (kind !in setOf("text", "kiss", "heart", "hug", "photos", "felt", "sync", "look")) return
            Relay.send(this@MainActivity, kind, text, data) { ok ->
                if (!ok && kind != "sync" && kind != "look") toast("Couldn't send. Check your internet and try again.")
            }
        }

        @JavascriptInterface fun inbox(): String = pr.inbox
        @JavascriptInterface fun status(): String = pr.status
        @JavascriptInterface fun batteryOk(): Boolean = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        /** A number both phones show; if they match, nobody else is in the middle. */
        @JavascriptInterface
        fun safety(): String {
            val h = MessageDigest.getInstance("SHA-256").digest(("hearby-safety:" + HearbyCrypto.normalize(pr.code)).toByteArray())
            val d = h.take(12).joinToString("") { ((it.toInt() and 0xFF) % 10).toString() }
            return d.chunked(4).joinToString(" ")
        }

        @JavascriptInterface
        fun setMyLook(look: String) {
            if (look !in setOf("man", "hijab", "woman") || look == pr.myLook) return
            pr.myLook = look
            Inbox.refresh(this@MainActivity)
            runOnUiThread {
                pal = Palette.of(this@MainActivity)
                applyWindowColors()
                web?.setBackgroundColor(pal.bg)
                web?.evaluateJavascript("document.documentElement.dataset.hue='${Palette.hue(pr)}'", null)
            }
        }

        @JavascriptInterface
        fun setPartnerLook(look: String) {
            if (look in setOf("man", "hijab", "woman") && look != pr.partnerLook) { pr.partnerLook = look; Inbox.refresh(this@MainActivity) }
        }

        @JavascriptInterface
        fun setNames(my: String, partner: String) {
            if (my.isNotBlank()) pr.myName = my.trim().take(20)
            if (partner.isNotBlank()) pr.partnerName = partner.trim().take(20)
            Inbox.refresh(this@MainActivity)
        }

        /** The painted scene, so the home-screen widget shows the same place. */
        @JavascriptInterface
        fun saveScene(dataUrl: String) {
            val comma = dataUrl.indexOf(',')
            if (!dataUrl.startsWith("data:image/png") || comma < 0) return
            Thread {
                try {
                    val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                    val f = SceneRenderer.sceneFile(this@MainActivity)
                    val tmp = File(f.path + ".tmp")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(f)
                    HearbyWidget.updateAll(applicationContext)
                } catch (e: Exception) { /* keep the old picture */ }
            }.start()
        }

        @JavascriptInterface fun settings() = runOnUiThread { openSettings() }
        @JavascriptInterface fun pinWidget() = runOnUiThread { this@MainActivity.pinWidget() }
        @JavascriptInterface fun battery() = runOnUiThread { askBattery() }
        @JavascriptInterface fun heartbeat() = runOnUiThread { Notifier.vibrateNow(this@MainActivity) }
    }

    @Suppress("DEPRECATION")
    private fun applyWindowColors() {
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(pal.bg))
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        var flags = window.decorView.systemUiVisibility
        flags = if (pal.dark) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = flags
    }

    private fun dialogTheme() = if (pal.dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert

    // ---------- Small UI helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun text(t: String, size: Float = 15f, bold: Boolean = false, color: Int = ink) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) pal.onAccent else ink)
        background = rounded(if (primary) rose else pal.field, 14, if (primary) 0 else pal.stroke)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun field(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 16f; setSingleLine(true)
        setTextColor(ink); setHintTextColor(muted)
        background = rounded(pal.field, 12, pal.stroke)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(pal.card, 20, pal.stroke)
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun ViewGroup.add(v: View, top: Int = 10): View {
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) })
        return v
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    private fun page(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(col); isFillViewport = true })
        return col
    }

    private fun header(col: LinearLayout, withSettings: Boolean) {
        val title = text("hearby ♥", 30f, true).apply { gravity = Gravity.START }
        if (withSettings) {
            val gear = button("⚙️") { openSettings() }
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(gear, LinearLayout.LayoutParams(dp(52), dp(48)))
            }
            col.add(r, 0)
        } else col.add(title, 0)
        col.add(text("Miles apart, a heartbeat away.", 15f, false, rose), 2)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun lookPicker(label: String, current: String, onPick: (String) -> Unit) =
        choicePicker(label, listOf("man" to "🧔 Man", "hijab" to "🧕 Hijab", "woman" to "👩 Long hair"), current, onPick)

    private fun choicePicker(label: String, opts: List<Pair<String, String>>, current: String, onPick: (String) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.add(text(label, 13f, true, muted), 4)
        val buttons = mutableListOf<Button>()
        fun paint(sel: String) = buttons.forEachIndexed { i, b ->
            val on = opts[i].first == sel
            b.background = rounded(if (on) rose else pal.field, 12, if (on) 0 else pal.stroke)
            b.setTextColor(if (on) pal.onAccent else ink)
        }
        opts.forEach { (key, name) -> buttons.add(button(name) { onPick(key); paint(key) }.apply { textSize = 13f }) }
        box.add(row(*buttons.toTypedArray()), 6)
        paint(current)
        return box
    }

    // ---------- First launch: names, looks and pairing ----------
    private fun buildSetup() {
        val col = page()
        header(col, false)
        var myLook = pr.myLook
        var theirLook = pr.partnerLook

        val who = card()
        who.add(text("About the two of you", 17f, true), 0)
        val myName = field("Your name", pr.myName)
        val theirName = field("Their name", pr.partnerName)
        who.add(myName)
        who.add(theirName)
        who.add(lookPicker("You look like", myLook) {
            myLook = it
            // Recolour right away (blue for 🧔, pink otherwise) while keeping what was typed.
            val before = Palette.hue(pr)
            pr.myLook = it; pr.partnerLook = theirLook
            pr.myName = myName.text.toString(); pr.partnerName = theirName.text.toString()
            if (Palette.hue(pr) != before) show()
        })
        who.add(lookPicker("They look like", theirLook) { theirLook = it })
        col.add(who, 18)

        fun save(code: String) {
            if (myName.text.isBlank() || theirName.text.isBlank()) { toast("Add both names first 💗"); return }
            if (!HearbyCrypto.isValid(code)) { toast("That code doesn't look right. It has 12 letters and numbers."); return }
            pr.myName = myName.text.toString().trim()
            pr.partnerName = theirName.text.toString().trim()
            pr.myLook = myLook
            pr.partnerLook = theirLook
            pr.code = HearbyCrypto.pretty(code)
            pr.lastId = ""
            HearbyService.start(this, reconnect = true)
            Inbox.refresh(this)
            show()
        }

        val pair = card()
        pair.add(text("Pair your phones", 17f, true), 0)
        pair.add(text("One of you creates a code and sends it to the other. The other enters it. That's it.", 14f, false, muted), 6)
        val codeView = text("", 26f, true, rose).apply { gravity = Gravity.CENTER; setTextIsSelectable(true); visibility = View.GONE }
        val afterCreate = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        var created = ""
        pair.add(button("✨ Create a pair code", true) {
            created = HearbyCrypto.newCode()
            codeView.text = created; codeView.visibility = View.VISIBLE; afterCreate.visibility = View.VISIBLE
        }, 12)
        pair.add(codeView, 12)
        afterCreate.add(button("📤 Send the code to them") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Our Hearby pair code 💗 $created\nOpen Hearby, tap \"Enter a code\" and type it in.")
            }, "Send your pair code"))
        }, 4)
        afterCreate.add(button("💞 Done, start Hearby", true) { save(created) }, 8)
        pair.add(afterCreate, 0)
        pair.add(text("or enter a code you received", 13f, true, muted).apply { gravity = Gravity.CENTER }, 16)
        val codeIn = field("XXXX-XXXX-XXXX").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        pair.add(codeIn, 8)
        pair.add(button("🔗 Pair with this code") { save(codeIn.text.toString()) }, 8)
        col.add(pair, 14)

        col.add(text("🔒 Everything you send is end-to-end encrypted with your pair code. Keep the code just between the two of you.", 13f, false, muted), 14)
    }

    // ---------- Main screen ----------
    private fun buildMain() {
        val col = page()
        header(col, true)

        val hero = card()
        sceneView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; adjustViewBounds = false }
        hero.addView(sceneView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)))
        partnerView = text("", 14f, true, muted)
        hero.add(partnerView!!, 10)
        lineView = text("", 18f, true)
        hero.add(lineView!!, 4)
        feltButton = button("💗 Felt it") { send("felt") }
        hero.add(feltButton!!, 10)
        col.add(hero, 16)

        val love = card()
        love.add(text("Send love", 17f, true), 0)
        love.add(row(button("💋 Kiss") { send("kiss") }, button("❤️ Heart") { send("heart") }, button("🤗 Hug") { send("hug") }), 10)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("thinking of you 🤍", "miss you 🥺", "good night 🌙", "good morning ☀️", "drink water, cutie 💧", "proud of you ⭐").forEach { t ->
            chips.addView(button(t) { send("text", t) }.apply { textSize = 13f }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginEnd = dp(8) })
        }
        love.add(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, 10)
        val input = field("Write something sweet…").apply { imeOptions = EditorInfo.IME_ACTION_SEND }
        val sendBtn = button("Send 💌", true) {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) { send("text", t); input.setText("") }
        }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendBtn.performClick(); true } else false }
        love.add(input, 10)
        love.add(sendBtn, 8)
        col.add(love, 14)

        val tools = card()
        tools.add(text("Set up", 17f, true), 0)
        tools.add(button("📱 Add the Hearby widget") { pinWidget() }, 10)
        batteryButton = button("🔋 Keep Hearby connected in the background") { askBattery() }
        tools.add(batteryButton!!, 8)
        tools.add(button("💓 Feel the heartbeat") { Notifier.vibrateNow(this) }, 8)
        statusView = text("", 13f, false, muted)
        tools.add(statusView!!, 10)
        col.add(tools, 14)

        col.add(text("🔒 End-to-end encrypted with your pair code. Messages travel through the ntfy.sh relay, which only ever sees scrambled data.", 12f, false, muted), 14)
        renderMain()
    }

    private fun renderMain() {
        val sv = sceneView ?: return
        sv.post {
            val w = sv.width.coerceAtLeast(dp(300))
            val h = sv.height.coerceAtLeast(dp(240))
            val s = minOf(1f, 900f / w)
            sv.setImageBitmap(SceneRenderer.render(this, (w * s).toInt(), (h * s).toInt()))
        }
        val name = pr.partnerName.ifBlank { "Your person" }
        partnerView?.text = if (pr.lastKind.isNotEmpty()) "💗 $name · ${Inbox.ago(pr.lastAt)}" else "💗 $name"
        lineView?.text = Inbox.line(this)
        val canFelt = pr.lastKind.isNotEmpty() && !pr.feltSent && System.currentTimeMillis() - pr.lastAt < 24 * 60 * 60 * 1000L
        feltButton?.isEnabled = canFelt
        feltButton?.alpha = if (canFelt) 1f else 0.5f
        feltButton?.text = if (pr.feltSent) "💞 You felt it" else "💗 Felt it"
        statusView?.text = when (pr.status) {
            "connected" -> "🟢 Connected. Heartbeats from $name will reach you."
            "offline" -> "🟠 Offline. Hearby will reconnect when you're back online."
            else -> "⚪ Connecting…"
        }
        val pm = getSystemService(PowerManager::class.java)
        batteryButton?.visibility = if (pm.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE
    }

    private fun send(kind: String, t: String = "") {
        Relay.send(this, kind, t) { ok ->
            toast(if (ok) (if (kind == "felt") "They'll know you felt it 💗" else "Sent 💌") else "Couldn't send. Check your internet and try again.")
            renderMain()
        }
    }

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, HearbyWidget::class.java), null, null)
        } else {
            toast("Long-press your home screen, tap Widgets, then find Hearby")
        }
    }

    @SuppressLint("BatteryLife")
    private fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ---------- Settings ----------
    private fun openSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        var myLook = pr.myLook
        var theirLook = pr.partnerLook
        val myName = field("Your name", pr.myName)
        val theirName = field("Their name", pr.partnerName)
        box.add(myName, 4)
        box.add(theirName)
        box.add(lookPicker("You look like", myLook) { myLook = it })
        box.add(lookPicker("They look like", theirLook) { theirLook = it })
        var color = pr.themeColor
        var mode = pr.themeMode
        box.add(choicePicker("App colour", listOf("auto" to "✨ From my look", "blue" to "💙 Blue", "pink" to "💗 Pink"), color) { color = it }, 14)
        box.add(choicePicker("Light or dark", listOf("system" to "📱 Like my phone", "light" to "☀️ Light", "dark" to "🌙 Dark"), mode) { mode = it })
        box.add(text("Pair code: ${pr.code}", 14f, true, muted).apply { setTextIsSelectable(true) }, 14)
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Settings")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                if (myName.text.isNotBlank()) pr.myName = myName.text.toString().trim()
                if (theirName.text.isNotBlank()) pr.partnerName = theirName.text.toString().trim()
                pr.myLook = myLook
                pr.partnerLook = theirLook
                pr.themeColor = color
                pr.themeMode = mode
                Inbox.refresh(this)
                show()
            }
            .setNeutralButton("Unpair") { _, _ ->
                AlertDialog.Builder(this, dialogTheme())
                    .setTitle("Unpair from ${pr.partnerName}?")
                    .setMessage("This phone stops sending and receiving. You can pair again with a new code.")
                    .setPositiveButton("Unpair") { _, _ ->
                        pr.unpair(); HearbyService.stop(this); Inbox.refresh(this); show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
