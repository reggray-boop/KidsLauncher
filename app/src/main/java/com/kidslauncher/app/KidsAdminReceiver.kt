package com.kidslauncher.app

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin receiver. Making this app the Device Owner (one adb command, see README)
 * unlocks the full kiosk lock: the child cannot leave the app, pull down the status bar
 * or open recent apps.
 */
class KidsAdminReceiver : DeviceAdminReceiver()
