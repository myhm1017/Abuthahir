package app.hearby

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** App colours: light blue for him, light pink for her, each with a light and a dark version. */
data class Palette(
    val dark: Boolean,
    val bg: Int,
    val card: Int,
    val field: Int,
    val stroke: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
) {
    companion object {
        private fun c(s: String) = Color.parseColor(s)

        /** "blue" or "pink". "auto" picks from your own look: 🧔 gets blue, 🧕 and 👩 get pink. */
        fun hue(pr: Prefs) = when (pr.themeColor) {
            "blue", "pink" -> pr.themeColor
            else -> if (pr.myLook == "man") "blue" else "pink"
        }

        /** "light" or "dark". "system" follows the phone's dark mode setting. */
        fun isDark(ctx: Context, pr: Prefs) = when (pr.themeMode) {
            "light" -> false
            "dark" -> true
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        fun of(ctx: Context): Palette {
            val pr = Prefs(ctx)
            val dark = isDark(ctx, pr)
            return if (hue(pr) == "blue") {
                if (dark) Palette(true, c("#0E1726"), c("#1A2533"), c("#213040"), c("#2C3D52"), c("#E8F0FA"), c("#9AABC0"), c("#7FB6F0"), c("#0B1420"))
                else Palette(false, c("#E4F0FC"), c("#CCFFFFFF"), c("#FFFFFF"), c("#D3E3F3"), c("#1F2A3A"), c("#5E6E82"), c("#4A8FD8"), c("#FFFFFF"))
            } else {
                if (dark) Palette(true, c("#21131D"), c("#2B1A24"), c("#34202C"), c("#4A2E3D"), c("#FAEAF1"), c("#C29FB0"), c("#F59BC0"), c("#2A0F1C"))
                else Palette(false, c("#F9E3F0"), c("#CCFFFFFF"), c("#FFFFFF"), c("#F2D6E3"), c("#3A2330"), c("#806272"), c("#E0679A"), c("#FFFFFF"))
            }
        }
    }
}
