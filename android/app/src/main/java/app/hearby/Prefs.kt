package app.hearby

import android.content.Context
import java.util.UUID

/** Small on-device store. Nothing here leaves the phone except encrypted messages. */
class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("hearby", Context.MODE_PRIVATE)

    private fun s(k: String, d: String = "") = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun l(k: String) = p.getLong(k, 0L)
    private fun putL(k: String, v: Long) = p.edit().putLong(k, v).apply()

    var myName: String get() = s("myName"); set(v) = put("myName", v)
    var partnerName: String get() = s("partnerName"); set(v) = put("partnerName", v)
    /** "man", "hijab" or "woman" */
    var myLook: String get() = s("myLook", "man"); set(v) = put("myLook", v)
    var partnerLook: String get() = s("partnerLook", "hijab"); set(v) = put("partnerLook", v)
    /** "auto" (from my look), "blue" or "pink" */
    var themeColor: String get() = s("themeColor", "auto"); set(v) = put("themeColor", v)
    /** "system", "light" or "dark" */
    var themeMode: String get() = s("themeMode", "system"); set(v) = put("themeMode", v)
    var code: String get() = s("code"); set(v) = put("code", v)

    var lastId: String get() = s("lastId"); set(v) = put("lastId", v)
    var lastKind: String get() = s("lastKind"); set(v) = put("lastKind", v)
    var lastText: String get() = s("lastText"); set(v) = put("lastText", v)
    var lastAt: Long get() = l("lastAt"); set(v) = putL("lastAt", v)
    var feltSent: Boolean get() = p.getBoolean("feltSent", false); set(v) = p.edit().putBoolean("feltSent", v).apply()
    var feltAt: Long get() = l("feltAt"); set(v) = putL("feltAt", v)
    var sentNote: String get() = s("sentNote"); set(v) = put("sentNote", v)
    var sentAt: Long get() = l("sentAt"); set(v) = putL("sentAt", v)
    /** Recent messages for the app screen, as a JSON array (newest last). */
    var inbox: String get() = s("inbox", "[]"); set(v) = put("inbox", v)
    var frameCount: Long get() = l("frameCount"); set(v) = putL("frameCount", v)
    var frameMs: Long get() = l("frameMs"); set(v) = putL("frameMs", v)
    /** Which place and season the frames show, so the expanded widget opens on the same one. */
    var frameScene: String get() = s("frameScene"); set(v) = put("frameScene", v)
    var frameVer: Long get() = l("frameVer"); set(v) = putL("frameVer", v)
    /** Width / height of the home-screen widget, so the frames are painted the right shape. */
    var widgetAspect: Float get() = p.getFloat("widgetAspect", 1f); set(v) = p.edit().putFloat("widgetAspect", v).apply()
    /** The widget pop-up changed something; the app screen reloads to pick it up. */
    var webDirty: Boolean get() = p.getBoolean("webDirty", false); set(v) = p.edit().putBoolean("webDirty", v).apply()
    /** When you last looked at a message (for the ♥ badge on the widget). */
    var seenAt: Long get() = l("seenAt"); set(v) = putL("seenAt", v)
    // Presence: when we last heard from the partner's phone, and whether they're in Hearby.
    var partnerSeenAt: Long get() = l("partnerSeenAt"); set(v) = putL("partnerSeenAt", v)
    var partnerState: String get() = s("partnerState"); set(v) = put("partnerState", v)
    var partnerStateAt: Long get() = l("partnerStateAt"); set(v) = putL("partnerStateAt", v)
    var partnerShares: Boolean get() = p.getBoolean("partnerShares", true); set(v) = p.edit().putBoolean("partnerShares", v).apply()
    /** Let the partner see when I'm in Hearby. */
    var sharePresence: Boolean get() = p.getBoolean("sharePresence", true); set(v) = p.edit().putBoolean("sharePresence", v).apply()
    var presenceSentAt: Long get() = l("presenceSentAt"); set(v) = putL("presenceSentAt", v)
    var presenceSent: String get() = s("presenceSent"); set(v) = put("presenceSent", v)
    var meOpenAt: Long get() = l("meOpenAt"); set(v) = putL("meOpenAt", v)
    var status: String get() = s("status", "starting"); set(v) = put("status", v)

    val deviceId: String
        get() {
            var id = p.getString("deviceId", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                p.edit().putString("deviceId", id).apply()
            }
            return id
        }

    val paired: Boolean get() = HearbyCrypto.isValid(code) && myName.isNotBlank()

    fun unpair() {
        p.edit().remove("code").remove("lastId").remove("lastKind").remove("lastText").remove("lastAt")
            .remove("feltAt").remove("sentNote").remove("sentAt").remove("inbox")
            .remove("partnerSeenAt").remove("partnerState").remove("partnerStateAt").apply()
    }
}
