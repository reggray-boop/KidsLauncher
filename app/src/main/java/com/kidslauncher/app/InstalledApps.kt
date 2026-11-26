package com.kidslauncher.app

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(val packageName: String, val label: String, val icon: Drawable)

object InstalledApps {

    /** Every app on the tablet that has an icon in the normal app drawer (except this one). */
    fun all(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppEntry(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Only the apps the parent has allowed. */
    fun allowed(ctx: Context): List<AppEntry> {
        val allowed = Prefs(ctx).allowedPackages
        return all(ctx).filter { it.packageName in allowed }
    }
}
