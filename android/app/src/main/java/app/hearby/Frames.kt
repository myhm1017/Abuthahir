package app.hearby

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.File

/**
 * The short animation loop the home-screen widget plays. The app paints the frames from its scene
 * (a new place each time you look) and the widget flips through them.
 */
object Frames {
    private const val AUTHORITY = "app.hearby.frames"
    @Volatile private var busySince = 0L

    private fun dir(ctx: Context) = File(ctx.filesDir, "frames").apply { mkdirs() }

    /** Only one painting at a time (the app and the widget pop-up can both be open). */
    @Synchronized
    fun begin(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - busySince < 15000) return false
        busySince = now
        dir(ctx).listFiles()?.filter { it.name.startsWith("new_") }?.forEach { it.delete() }
        return true
    }

    fun put(ctx: Context, i: Int, dataUrl: String) {
        val comma = dataUrl.indexOf(',')
        if (i !in 0..47 || !dataUrl.startsWith("data:image/jpeg") || comma < 0) return
        try { File(dir(ctx), "new_$i.jpg").writeBytes(Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)) } catch (e: Exception) { /* skip */ }
    }

    @Synchronized
    fun done(ctx: Context, n: Int, ms: Int) {
        busySince = 0L
        val d = dir(ctx)
        val fresh = (0 until n).map { File(d, "new_$it.jpg") }
        if (n < 4 || fresh.any { !it.exists() }) return
        fresh.forEachIndexed { i, f -> f.renameTo(File(d, "f_$i.jpg")) }
        d.listFiles()?.filter { it.name.startsWith("f_") && (it.name.removePrefix("f_").removeSuffix(".jpg").toIntOrNull() ?: 99) >= n }?.forEach { it.delete() }
        val pr = Prefs(ctx)
        pr.frameCount = n.toLong()
        pr.frameMs = ms.coerceIn(80, 1000).toLong()
        pr.frameVer = System.currentTimeMillis()
        HearbyWidget.updateAll(ctx.applicationContext)
    }

    fun count(ctx: Context): Int {
        val n = Prefs(ctx).frameCount.toInt()
        return if (n > 0 && File(dir(ctx), "f_${n - 1}.jpg").exists()) n else 0
    }

    /** A versioned link to one frame, readable only by the home-screen app it is granted to. */
    fun uri(ctx: Context, i: Int): Uri {
        val u = Uri.parse("content://$AUTHORITY/f_$i.jpg?v=${Prefs(ctx).frameVer}")
        launcher(ctx)?.let { pkg -> try { ctx.grantUriPermission(pkg, u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { /* not allowed */ } }
        return u
    }

    private fun launcher(ctx: Context): String? = try {
        ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
    } catch (e: Exception) { null }

    fun file(ctx: Context, name: String): File? =
        if (Regex("f_\\d{1,2}\\.jpg").matches(name)) File(dir(ctx), name).takeIf { it.exists() } else null
}

/** Serves the widget's animation frames to the launcher (not exported; access is granted per frame). */
class FramesProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/jpeg"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val f = Frames.file(ctx, uri.lastPathSegment ?: return null) ?: return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
