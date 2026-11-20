package app.hearby

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The expanded widget. Tapping the home-screen widget opens this see-through screen: the widget grows
 * out of where it sits, with the message, the 📷 🖊️ 💬 💋 ❤️ 💗 buttons and the moving scene, over the
 * dimmed home screen. Tap outside to close it.
 */
class WidgetPopupActivity : Activity() {
    private var web: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var closing = false

    private val host = object : BridgeHost {
        override val activity get() = this@WidgetPopupActivity
        override val mode = "widget"
        override val web get() = this@WidgetPopupActivity.web
        override val top: Float
            get() {
                val b = intent.sourceBounds ?: return 0.12f
                return b.top.toFloat() / resources.displayMetrics.heightPixels.coerceAtLeast(1)
            }
        override fun openSettings() {
            startActivity(Intent(this@WidgetPopupActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        override fun ready() = grow()
        override fun close() = shrinkAndFinish()
    }

    private val refresh = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { web?.evaluateJavascript("window.hearbyPull && window.hearbyPull()", null) }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs(this).paired) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        // Draw edge to edge, so the widget opens exactly where it sits on the home screen.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val wv = WebView(this)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.alpha = 0f
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            textZoom = 100
            allowFileAccess = false
            allowContentAccess = false
        }
        wv.isVerticalScrollBarEnabled = false
        wv.addJavascriptInterface(HearbyBridge(host), "HearbyNative")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
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
                return try { startActivityForResult(Intent.createChooser(pick, "Choose photos"), 8); true } catch (e: Exception) { fileCallback = null; false }
            }
        }
        setContentView(wv)
        web = wv
        wv.loadUrl("file:///android_asset/hearby.html")
        // If the page is slow to say it's ready, show it anyway.
        wv.postDelayed({ if (wv.alpha == 0f) grow() }, 1500)
        HearbyService.start(this)
    }

    private fun grow() {
        val wv = web ?: return
        if (wv.alpha > 0f || closing) return
        val b = intent.sourceBounds
        wv.pivotX = b?.exactCenterX() ?: (wv.width / 2f)
        wv.pivotY = (b?.top?.toFloat() ?: 0f)
        wv.scaleX = 0.6f
        wv.scaleY = 0.6f
        wv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(OvershootInterpolator(0.9f)).start()
    }

    private fun shrinkAndFinish() {
        if (closing) return
        closing = true
        val wv = web
        if (wv == null || wv.alpha == 0f) { finish(); return }
        wv.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(200).setInterpolator(DecelerateInterpolator())
            .withEndAction { finish() }.start()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = shrinkAndFinish()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        web?.onResume()
        val f = IntentFilter(Inbox.ACTION_REFRESH)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refresh, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(refresh, f)
    }

    override fun onPause() {
        super.onPause()
        web?.onPause()
        try { unregisterReceiver(refresh) } catch (e: Exception) { /* not registered */ }
    }

    override fun onStop() {
        super.onStop()
        // Leaving for the home screen or another app closes the pop-up, like a real widget.
        if (fileCallback == null && !isFinishing) finish()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 8) return
        val cb = fileCallback ?: return
        fileCallback = null
        if (resultCode != RESULT_OK || data == null) { cb.onReceiveValue(null); return }
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris.add(c.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        cb.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }
}
