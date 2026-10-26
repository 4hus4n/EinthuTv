package mv.ahusan.einthusantv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saves the Einthusan email + password on the device.
 * The password is encrypted with a key kept in the Android Keystore (never leaves the TV).
 */
object CredentialStore {

    private const val PREFS = "einthutv"
    private const val KEY_ALIAS = "einthutv_login"
    private const val ANDROID_KS = "AndroidKeyStore"

    data class Login(val email: String, val password: String)

    fun save(ctx: Context, email: String, password: String) {
        val enc = try { encrypt(password) } catch (e: Exception) { null }
        prefs(ctx).edit()
            .putString("email", email)
            .putString("pass_enc", enc)
            .apply()
    }

    fun load(ctx: Context): Login? {
        val p = prefs(ctx)
        val email = p.getString("email", null) ?: return null
        val enc = p.getString("pass_enc", null) ?: return null
        val pass = try { decrypt(enc) } catch (e: Exception) { null } ?: return null
        return if (email.isNotBlank() && pass.isNotEmpty()) Login(email, pass) else null
    }

    fun savedEmail(ctx: Context): String = prefs(ctx).getString("email", "") ?: ""

    /** Keeps the email, forgets the password. */
    fun forgetPassword(ctx: Context) {
        prefs(ctx).edit().remove("pass_enc").apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(":")
        if (parts.size != 2) throw IllegalArgumentException("bad data")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(data), Charsets.UTF_8)
    }
}
