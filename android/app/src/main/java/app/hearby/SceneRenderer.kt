package app.hearby

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.io.File
import java.util.Calendar
import kotlin.math.sin

/**
 * Paints the couple scene for the widget and the app: a soft sky that follows the time of day,
 * rolling hills, a blossom tree and the two chibi characters.
 */
object SceneRenderer {
    private fun c(v: Long) = v.toInt()

    private data class Look(val skin: Int, val hair: Int, val outfit: Int, val pants: Int, val hijab: Int, val type: String)

    private fun lookFor(type: String) = when (type) {
        "hijab" -> Look(c(0xFFF3D0B5), c(0xFF2B1D17), c(0xFF8F7BB8), c(0xFF6B5A8A), c(0xFFC98FB4), "hijab")
        "woman" -> Look(c(0xFFEFC3A2), c(0xFF4A2F22), c(0xFFF2A7BF), c(0xFFB7708A), 0, "woman")
        else -> Look(c(0xFFE8B894), c(0xFF2B1D17), c(0xFF7FA6D9), c(0xFF3F4A66), 0, "man")
    }

    fun render(ctx: Context, w: Int, h: Int): Bitmap {
        val pr = Prefs(ctx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val W = w.toFloat()
        val H = h.toFloat()

        // Rounded corners so the scene looks right on every launcher.
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, W, H), 26f * W / 300f, 26f * W / 300f, Path.Direction.CW) }
        cv.clipPath(clip)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val mode = modeAt(hour) // day, sunset, night

        // The app saves its painted scene (a new place each time you open it); the widget shows the same picture.
        val snap = snapshot(ctx)
        if (snap != null) {
            val s = maxOf(W / snap.first.width, H / snap.first.height)
            val dw = snap.first.width * s
            val dh = snap.first.height * s
            val left = (W - dw) / 2f
            val top = (H - dh) * 0.7f
            cv.drawBitmap(snap.first, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            // If the time of day has changed since then, tint it so night still looks like night.
            if (mode != snap.second) {
                p.color = when (mode) { 2 -> c(0x7A141B46); 1 -> c(0x40E8707A); else -> c(0x00000000) }
                cv.drawRect(0f, 0f, W, H, p)
                if (mode == 2) {
                    p.color = c(0xCCFFF6D8)
                    for (i in 0 until 24) {
                        val x = ((i * 97.31) % 1.0).toFloat() * W
                        val y = ((i * 0.6180339) % 1.0).toFloat() * H * 0.35f
                        cv.drawCircle(x, y, if (i % 5 == 0) W * 0.006f else W * 0.0035f, p)
                    }
                }
            }
            drawEmoji(cv, p, pr, W, H)
            return bmp
        }
        val sky = when (mode) {
            0 -> intArrayOf(c(0xFF78B2E3), c(0xFFB9DCF2), c(0xFFFDE5D3))
            1 -> intArrayOf(c(0xFF4B2F73), c(0xFFE8707A), c(0xFFFFC46B))
            else -> intArrayOf(c(0xFF141B46), c(0xFF2C3270), c(0xFF5D4F88))
        }
        p.shader = LinearGradient(0f, 0f, 0f, H * 0.65f, sky, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, W, H, p)
        p.shader = null

        if (mode == 2) {
            p.color = c(0xFFFFF6D8)
            for (i in 0 until 30) {
                val x = ((i * 97.31) % 1.0).toFloat() * W
                val y = ((i * 0.6180339) % 1.0).toFloat() * H * 0.42f
                cv.drawCircle(x, y, if (i % 5 == 0) W * 0.006f else W * 0.0035f, p)
            }
            p.shader = RadialGradient(W * 0.8f, H * 0.17f, W * 0.25f, c(0x66FFF0C8), 0, Shader.TileMode.CLAMP)
            cv.drawCircle(W * 0.8f, H * 0.17f, W * 0.25f, p)
            p.shader = null
            p.color = c(0xFFFFF3CF)
            cv.drawCircle(W * 0.8f, H * 0.17f, W * 0.045f, p)
        } else {
            p.shader = RadialGradient(W * 0.15f, H * 0.08f, W * 0.6f, c(0xBBFFF4D6), 0, Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, W, H, p)
            p.shader = null
            p.color = if (mode == 0) c(0xFFFFFFFF) else c(0xCCFFE3D0)
            for ((cx, cy, s) in listOf(Triple(0.18f, 0.16f, 1f), Triple(0.66f, 0.1f, 1.2f))) {
                val k = s * W / 260f
                for ((dx, dy, r) in listOf(Triple(0f, 0f, 13f), Triple(13f, -7f, 15f), Triple(28f, -2f, 13f), Triple(8f, 5f, 11f), Triple(22f, 6f, 12f))) {
                    cv.drawCircle(cx * W + dx * k, cy * H + dy * k, r * k, p)
                }
            }
        }

        // Far hills
        val hills = Path().apply {
            moveTo(0f, H)
            var x = 0f
            while (x <= W + 6) {
                lineTo(x, H * 0.47f + sin(x * 0.021 * 260 / W).toFloat() * H * 0.035f + sin(x * 0.047 * 260 / W + 2).toFloat() * H * 0.016f)
                x += 6f
            }
            lineTo(W, H); close()
        }
        p.color = when (mode) { 0 -> c(0xFF9FC9B2); 1 -> c(0xFF9A7C8F); else -> c(0xFF3E5A74) }
        cv.drawPath(hills, p)

        // Blossom tree
        val tx = W * 0.2f
        val tb = H * 0.62f
        val u = H / 260f
        p.color = if (mode == 2) c(0xFF4A3A36) else c(0xFF7A5642)
        cv.drawRect(tx - 4 * u, tb - 52 * u, tx + 4 * u, tb, p)
        val greens = when (mode) { 2 -> intArrayOf(c(0xFF2F5546), c(0xFF3B6A55), c(0xFF4C7A69)); else -> intArrayOf(c(0xFF3F7F4C), c(0xFF5B9E5A), c(0xFF84C06C)) }
        val blobs = listOf(
            listOf(Triple(-22f, -58f, 18f), Triple(20f, -60f, 18f), Triple(0f, -74f, 20f), Triple(-8f, -52f, 16f), Triple(12f, -50f, 15f)),
            listOf(Triple(-14f, -66f, 15f), Triple(12f, -70f, 15f), Triple(0f, -82f, 14f)),
            listOf(Triple(-8f, -76f, 9f), Triple(10f, -78f, 8f), Triple(4f, -88f, 7f))
        )
        blobs.forEachIndexed { i, layer -> p.color = greens[i]; layer.forEach { (bx, by, r) -> cv.drawCircle(tx + bx * u, tb + by * u, r * u, p) } }
        if (mode != 2) { p.color = c(0xFFFFD6E6); listOf(-16f to -60f, 14f to -66f, 2f to -80f, -4f to -64f, 20f to -54f).forEach { (bx, by) -> cv.drawCircle(tx + bx * u, tb + by * u, 1.8f * u, p) } }

        // Near meadow
        val meadow = Path().apply {
            moveTo(0f, H)
            var x = 0f
            while (x <= W + 6) {
                lineTo(x, nearY(x, W, H))
                x += 6f
            }
            lineTo(W, H); close()
        }
        val grass = when (mode) { 0 -> intArrayOf(c(0xFF9AD37D), c(0xFF5A9D55)); 1 -> intArrayOf(c(0xFF8DB27A), c(0xFF55805A)); else -> intArrayOf(c(0xFF3F6A5A), c(0xFF25443C)) }
        p.shader = LinearGradient(0f, H * 0.58f, 0f, H, grass, null, Shader.TileMode.CLAMP)
        cv.drawPath(meadow, p)
        p.shader = null

        // The couple
        val ku = H / 92f
        val xa = W * 0.43f
        val xb = W * 0.57f
        val me = lookFor(pr.myLook)
        val you = lookFor(pr.partnerLook)
        val handY = nearY(xa, W, H) + 3 * ku - 6.6f * ku
        drawKid(cv, p, xa, nearY(xa, W, H) + 3 * ku, ku, me, 1f, (xa + xb) / 2, handY)
        val head = drawKid(cv, p, xb, nearY(xb, W, H) + 3 * ku, ku, you, -1f, (xa + xb) / 2, handY)
        drawHeart(cv, p, W * 0.5f, head - 7.5f * ku, 1.6f * ku, c(0xFFFF6F9F))

        drawEmoji(cv, p, pr, W, H)
        return bmp
    }

    private fun modeAt(hour: Int) = when (hour) { in 6..16 -> 0; in 17..19 -> 1; else -> 2 }

    /** A big emoji floats up for a few minutes after a kiss, heart or hug arrives. */
    private fun drawEmoji(cv: Canvas, p: Paint, pr: Prefs, W: Float, H: Float) {
        if (pr.lastKind.isNotEmpty() && System.currentTimeMillis() - pr.lastAt < 3 * 60 * 1000L) {
            p.textSize = H * 0.18f
            p.textAlign = Paint.Align.CENTER
            p.color = c(0xFFFFFFFF)
            cv.drawText(Inbox.emojiFor(pr.lastKind, pr.lastText), W * 0.72f, H * 0.36f, p)
        }
    }

    fun sceneFile(ctx: Context) = File(ctx.filesDir, "scene.png")

    private var cached: Bitmap? = null
    private var cachedAt = 0L

    /** The scene the app last saved and the time of day it was painted, or null. */
    private fun snapshot(ctx: Context): Pair<Bitmap, Int>? {
        val f = sceneFile(ctx)
        if (!f.exists()) return null
        if (cached == null || cachedAt != f.lastModified()) {
            cached = try { BitmapFactory.decodeFile(f.path) } catch (e: Exception) { null }
            cachedAt = f.lastModified()
        }
        val b = cached ?: return null
        val made = Calendar.getInstance().apply { timeInMillis = f.lastModified() }.get(Calendar.HOUR_OF_DAY)
        return b to modeAt(made)
    }

    private fun nearY(x: Float, W: Float, H: Float): Float {
        val xs = x * 260f / W
        return H * 0.62f + sin(xs * 0.013 + 1).toFloat() * H * 0.022f + sin(xs * 0.031).toFloat() * H * 0.01f
    }

    private fun drawHeart(cv: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.color = color
        val path = Path().apply {
            moveTo(x, y + r * 0.9f)
            cubicTo(x - r * 1.4f, y - r * 0.1f, x - r * 0.7f, y - r * 1.2f, x, y - r * 0.45f)
            cubicTo(x + r * 0.7f, y - r * 1.2f, x + r * 1.4f, y - r * 0.1f, x, y + r * 0.9f)
            close()
        }
        cv.drawPath(path, p)
    }

    /** Draws one chibi character standing at (x, gy). Returns the head's top edge. */
    private fun drawKid(cv: Canvas, p: Paint, x: Float, gy: Float, u: Float, k: Look, face: Float, handX: Float, handY: Float): Float {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        p.color = c(0x2E1E321E)
        cv.drawOval(RectF(x - 4.6f * u, gy - 0.1f * u, x + 4.6f * u, gy + 2.1f * u), p)

        val hipY = gy - 3.3f * u
        val top = hipY - 5.2f * u
        // legs
        stroke.color = k.pants; stroke.strokeWidth = 1.6f * u
        cv.drawLine(x - 0.9f * u, hipY, x - 0.9f * u, gy, stroke)
        cv.drawLine(x + 0.9f * u, hipY, x + 0.9f * u, gy, stroke)
        p.color = c(0xFF4E3C45)
        cv.drawOval(RectF(x - 2f * u, gy - 0.6f * u, x - 0.2f * u + face * 0.4f * u, gy + 0.6f * u), p)
        cv.drawOval(RectF(x - 0.2f * u + face * 0.4f * u, gy - 0.6f * u, x + 2f * u, gy + 0.6f * u), p)

        // body (a long modest dress for the hijab look)
        p.color = k.outfit
        val hem = if (k.type == "hijab") gy - 0.8f * u else hipY + 0.6f * u
        val flare = if (k.type == "hijab") 3.5f else 2.75f
        val body = Path().apply {
            moveTo(x - 2.2f * u, top + 0.6f * u)
            quadTo(x, top - 0.4f * u, x + 2.2f * u, top + 0.6f * u)
            lineTo(x + flare * u, hem)
            quadTo(x, hem + 0.9f * u, x - flare * u, hem)
            close()
        }
        cv.drawPath(body, p)

        // arms: the inner arm holds hands
        val sh = top + 1f * u
        stroke.strokeWidth = 1.3f * u
        stroke.color = k.outfit
        val outer = x - face * 2.1f * u
        cv.drawLine(outer, sh, outer - face * 0.8f * u, sh + 3.2f * u, stroke)
        val inner = x + face * 2.1f * u
        cv.drawLine(inner, sh, handX, handY, stroke)
        p.color = k.skin
        cv.drawCircle(outer - face * 0.8f * u, sh + 3.2f * u, 0.8f * u, p)
        cv.drawCircle(handX, handY, 0.8f * u, p)

        // head
        val hx = x + face * 0.2f * u
        val hy = top - 3.9f * u
        val r = 4.3f * u
        var fx = hx
        var fy = hy
        var fr = r
        if (k.type == "hijab") {
            p.color = k.hijab
            cv.drawOval(RectF(hx - r * 1.18f, hy - r * 1.1f, hx + r * 1.18f, hy + r * 1.3f), p)
            val drape = Path().apply {
                moveTo(hx - r * 1.12f, hy + r * 0.2f)
                quadTo(hx - r * 1.4f, top + 2.6f * u, hx - r * 0.75f, top + 3.6f * u)
                quadTo(hx, top + 4.6f * u, hx + r * 0.75f, top + 3.6f * u)
                quadTo(hx + r * 1.4f, top + 2.6f * u, hx + r * 1.12f, hy + r * 0.2f)
                close()
            }
            cv.drawPath(drape, p)
            fx = hx + face * 0.25f * u; fy = hy + 0.55f * u; fr = r * 0.78f
            p.color = k.skin
            cv.drawOval(RectF(fx - fr, fy - fr * 1.04f, fx + fr, fy + fr * 1.04f), p)
        } else {
            if (k.type == "woman") {
                p.color = k.hair
                cv.drawOval(RectF(hx - r * 1.12f, hy - r * 0.6f, hx + r * 1.12f, hy + r * 2.6f), p)
            }
            p.color = k.skin
            cv.drawCircle(hx, hy, r, p)
            p.color = k.hair
            cv.drawArc(RectF(hx - r * 1.05f, hy - r * 1.05f, hx + r * 1.05f, hy + r * 1.05f), 180f, 180f, true, p)
            cv.drawOval(RectF(hx - r * 0.2f + face * r * 0.2f, hy - r * 0.35f, hx + r * 0.9f * face.coerceAtLeast(0f) + r * 0.6f, hy + r * 0.05f), p)
            if (k.type == "man") {
                cv.drawOval(RectF(hx - r * 1.15f, hy - r * 0.25f, hx - r * 0.75f, hy + r * 0.5f), p)
                cv.drawOval(RectF(hx + r * 0.75f, hy - r * 0.25f, hx + r * 1.15f, hy + r * 0.5f), p)
            }
        }

        val ex = fx + face * (if (k.type == "hijab") 0.35f else 0.55f) * u
        val ey = fy + (if (k.type == "hijab") 0.5f else 0.7f) * u
        val gap = (if (k.type == "hijab") 1.4f else 1.55f) * u

        // full natural beard for the man
        if (k.type == "man") {
            p.color = k.hair
            val beard = Path().apply {
                moveTo(hx - r * 0.98f, hy)
                quadTo(hx - r * 1.1f, hy + r * 0.95f, hx, hy + r * 1.4f)
                quadTo(hx + r * 1.1f, hy + r * 0.95f, hx + r * 0.98f, hy)
                lineTo(hx + r * 0.8f, hy)
                quadTo(hx + r * 0.75f, hy + r * 0.5f, hx, hy + r * 0.58f)
                quadTo(hx - r * 0.75f, hy + r * 0.5f, hx - r * 0.8f, hy)
                close()
            }
            cv.drawPath(beard, p)
        }

        // blush
        p.color = if (k.type == "man") c(0x2EFF7896) else c(0x61FF7896)
        cv.drawOval(RectF(ex - 3.3f * u, ey + 0.8f * u, ex - 1.5f * u, ey + 1.8f * u), p)
        cv.drawOval(RectF(ex + 1.5f * u, ey + 0.8f * u, ex + 3.3f * u, ey + 1.8f * u), p)
        // eyes
        p.color = c(0xFF3B2A3F)
        for (sd in intArrayOf(-1, 1)) cv.drawOval(RectF(ex + sd * gap - 0.5f * u, ey - 0.72f * u, ex + sd * gap + 0.5f * u, ey + 0.72f * u), p)
        p.color = c(0xFFFFFFFF)
        for (sd in intArrayOf(-1, 1)) cv.drawCircle(ex + sd * gap + 0.18f * u, ey - 0.28f * u, 0.2f * u, p)
        // smile and moustache
        stroke.color = c(0xFFA34E63); stroke.strokeWidth = 0.45f * u
        val my = ey + (if (k.type == "man") 2.05f else 1.3f) * u
        cv.drawArc(RectF(ex - 0.5f * u, my - 0.5f * u, ex + 0.5f * u, my + 0.5f * u), 20f, 140f, false, stroke)
        if (k.type == "man") {
            p.color = k.hair
            cv.drawOval(RectF(ex - 2f * u, ey + 0.65f * u, ex, ey + 1.85f * u), p)
            cv.drawOval(RectF(ex, ey + 0.65f * u, ex + 2f * u, ey + 1.85f * u), p)
        }
        return hy - r
    }
}
