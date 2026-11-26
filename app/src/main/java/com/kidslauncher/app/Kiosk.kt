package com.kidslauncher.app

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Wraps the Device Owner / lock task APIs.
 *
 * Without Device Owner the app still works as a kid-friendly home screen, but a determined
 * child could use the recent-apps button or the status bar to get out. With Device Owner
 * the tablet is fully locked to this app and the apps the parent has allowed.
 */
object Kiosk {

    fun admin(ctx: Context) = ComponentName(ctx, KidsAdminReceiver::class.java)

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)

    fun isDeviceOwner(ctx: Context): Boolean = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun isInLockTask(ctx: Context): Boolean =
        ctx.getSystemService(ActivityManager::class.java).lockTaskModeState !=
            ActivityManager.LOCK_TASK_MODE_NONE

    /** Turn on all kiosk restrictions. Safe to call repeatedly. */
    fun apply(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = admin(ctx)

        setAllowedPackages(ctx)

        // Only the Home button works (it brings the child back to this launcher).
        // No recent apps, no notifications, no power menu.
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_HOME)

        // Force this app to be the home screen.
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(ctx, MainActivity::class.java))

        dpm.setStatusBarDisabled(admin, true)
    }

    /**
     * Which apps may run while the tablet is locked. [extraPackage] lets the parent area
     * temporarily open a system screen (e.g. the photo picker for wallpaper).
     */
    fun setAllowedPackages(ctx: Context, extraPackage: String? = null) {
        if (!isDeviceOwner(ctx)) return
        val pkgs = Prefs(ctx).allowedPackages + ctx.packageName + listOfNotNull(extraPackage)
        dpm(ctx).setLockTaskPackages(admin(ctx), pkgs.toTypedArray())
    }

    /** Turn off kiosk restrictions (parent switched kid mode off). */
    fun release(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        dpm.setStatusBarDisabled(admin, false)
        dpm.setLockTaskPackages(admin, emptyArray())
    }

    /** Give up Device Owner so the app can be uninstalled normally. */
    @Suppress("DEPRECATION")
    fun removeDeviceOwner(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        release(ctx)
        dpm(ctx).clearDeviceOwnerApp(ctx.packageName)
    }
}
