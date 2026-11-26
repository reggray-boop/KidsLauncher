package com.kidslauncher.app

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** All parent settings, stored privately on the tablet. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("kids_launcher", Context.MODE_PRIVATE)

    // ---- Apps the child may use ----
    var allowedPackages: Set<String>
        get() = sp.getStringSet(KEY_ALLOWED, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_ALLOWED, value.toSet()).apply()

    // ---- Look & feel ----
    var iconSizeDp: Int
        get() = sp.getInt(KEY_ICON_SIZE, 140)
        set(value) = sp.edit().putInt(KEY_ICON_SIZE, value).apply()

    /** Key of the ready-made gradient wallpaper (used when no photo is chosen). */
    var wallpaperPreset: String
        get() = sp.getString(KEY_WP_PRESET, "meadow")!!
        set(value) = sp.edit().putString(KEY_WP_PRESET, value).apply()

    // ---- Volume limit ----
    var volumeLimitEnabled: Boolean
        get() = sp.getBoolean(KEY_VOL_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_VOL_ENABLED, value).apply()

    /** Maximum media volume, as a percentage of the tablet's maximum (5..100). */
    var maxVolumePercent: Int
        get() = sp.getInt(KEY_VOL_MAX, 50)
        set(value) = sp.edit().putInt(KEY_VOL_MAX, value.coerceIn(5, 100)).apply()

    // ---- Kid mode ----
    /** When true the parent has switched kid mode off and the tablet behaves normally. */
    var kidModePaused: Boolean
        get() = sp.getBoolean(KEY_PAUSED, false)
        set(value) = sp.edit().putBoolean(KEY_PAUSED, value).apply()

    /** Set by the parent; the home screen performs the removal (it owns the lock task). */
    var removeOwnerRequested: Boolean
        get() = sp.getBoolean(KEY_REMOVE_OWNER, false)
        set(value) = sp.edit().putBoolean(KEY_REMOVE_OWNER, value).apply()

    // ---- PIN ----
    fun hasPin(): Boolean = sp.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCKOUT, 0)
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = Base64.decode(sp.getString(KEY_PIN_SALT, null) ?: return false, Base64.NO_WRAP)
        val stored = sp.getString(KEY_PIN_HASH, null) ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(pin, salt).toByteArray())
    }

    var failedAttempts: Int
        get() = sp.getInt(KEY_FAILED, 0)
        set(value) = sp.edit().putInt(KEY_FAILED, value).apply()

    var lockoutUntil: Long
        get() = sp.getLong(KEY_LOCKOUT, 0)
        set(value) = sp.edit().putLong(KEY_LOCKOUT, value).apply()

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 20_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val KEY_ALLOWED = "allowed_packages"
        const val KEY_ICON_SIZE = "icon_size_dp"
        const val KEY_WP_PRESET = "wallpaper_preset"
        const val KEY_VOL_ENABLED = "volume_limit_enabled"
        const val KEY_VOL_MAX = "volume_max_percent"
        const val KEY_PAUSED = "kid_mode_paused"
        const val KEY_REMOVE_OWNER = "remove_owner_requested"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_FAILED = "pin_failed"
        const val KEY_LOCKOUT = "pin_lockout_until"
    }
}
