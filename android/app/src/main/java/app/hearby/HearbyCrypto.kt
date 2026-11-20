package app.hearby

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for the two paired phones.
 *
 * Both phones share a secret pair code. From it we derive:
 *  - a relay topic name (where messages are posted), and
 *  - an AES-256-GCM key (so the relay only ever sees scrambled data).
 */
object HearbyCrypto {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val rng = SecureRandom()

    /** A new 12-character pair code, shown as XXXX-XXXX-XXXX. */
    fun newCode(): String {
        val sb = StringBuilder()
        for (i in 0 until 12) {
            if (i == 4 || i == 8) sb.append('-')
            sb.append(ALPHABET[rng.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    fun isValid(code: String): Boolean = normalize(code).length == 12

    fun pretty(code: String): String = normalize(code).chunked(4).joinToString("-")

    private fun sha256(s: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))

    fun topicFor(code: String): String =
        "hearby-" + sha256("hearby-topic:" + normalize(code)).joinToString("") { "%02x".format(it) }.take(32)

    private fun key(code: String) = SecretKeySpec(sha256("hearby-key:" + normalize(code)), "AES")

    fun encrypt(code: String, plain: String): String {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(code), GCMParameterSpec(128, iv))
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + out, Base64.NO_WRAP)
    }

    fun decrypt(code: String, b64: String): String? = try {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(code), GCMParameterSpec(128, all, 0, 12))
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
