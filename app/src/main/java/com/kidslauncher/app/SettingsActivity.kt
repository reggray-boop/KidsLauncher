package com.kidslauncher.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Parent-only settings. Reached through the hold-to-open lock button + PIN. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    /** Closes the parent area automatically if it is left open (so the child can't use it). */
    private val autoClose = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        findViewById<View>(R.id.done_button).setOnClickListener { finish() }

        // ---- Apps ----
        findViewById<View>(R.id.choose_apps_button).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        // ---- Wallpaper ----
        findViewById<View>(R.id.choose_wallpaper_button).setOnClickListener { pickWallpaper() }
        findViewById<View>(R.id.remove_wallpaper_button).setOnClickListener {
            WallpaperStore.clear(this)
            showWallpaper()
        }
        buildPresetGrid()

        // ---- Icon size ----
        val iconLabel = findViewById<TextView>(R.id.icon_size_label)
        findViewById<SeekBar>(R.id.icon_size_seek).apply {
            min = ICON_MIN_DP
            max = ICON_MAX_DP
            progress = prefs.iconSizeDp
            iconLabel.text = iconSizeText(progress)
            setOnSeekBarChangeListener(onChange { value ->
                prefs.iconSizeDp = value
                iconLabel.text = iconSizeText(value)
            })
        }

        // ---- Volume ----
        val volSwitch = findViewById<Switch>(R.id.volume_switch)
        val volLabel = findViewById<TextView>(R.id.volume_label)
        val volSeek = findViewById<SeekBar>(R.id.volume_seek)
        volSwitch.isChecked = prefs.volumeLimitEnabled
        volSeek.min = 5
        volSeek.max = 100
        volSeek.progress = prefs.maxVolumePercent
        volSeek.isEnabled = prefs.volumeLimitEnabled
        volLabel.text = "Maximum volume: ${prefs.maxVolumePercent}%"
        volSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.volumeLimitEnabled = checked
            volSeek.isEnabled = checked
            VolumeGuardService.sync(this)
        }
        volSeek.setOnSeekBarChangeListener(onChange { value ->
            prefs.maxVolumePercent = value
            volLabel.text = "Maximum volume: $value%"
            VolumeGuardService.sync(this)
        })

        // ---- PIN ----
        findViewById<View>(R.id.change_pin_button).setOnClickListener {
            startActivity(PinActivity.setupIntent(this))
        }

        // ---- Lock ----
        findViewById<View>(R.id.pause_button).setOnClickListener {
            prefs.kidModePaused = true
            finish() // The home screen sees the flag and unlocks.
        }
        findViewById<View>(R.id.remove_owner_button).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove full lock?")
                .setMessage("Kid mode will be switched off and the app will stop being the device owner. " +
                    "You can then uninstall it. To lock the tablet fully again you will need to repeat the adb setup step.")
                .setPositiveButton("Remove") { _, _ ->
                    prefs.kidModePaused = true
                    prefs.removeOwnerRequested = true
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val count = prefs.allowedPackages.size
        findViewById<TextView>(R.id.apps_summary).text =
            if (count == 0) "No apps chosen yet." else "$count app${if (count == 1) "" else "s"} allowed."
        showWallpaper()
        showLockStatus()
        resetAutoClose()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoClose)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetAutoClose()
    }

    private fun resetAutoClose() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    private fun showWallpaper() {
        findViewById<ImageView>(R.id.wallpaper_preview).setImageDrawable(Wallpapers.current(this))
        findViewById<View>(R.id.remove_wallpaper_button).isEnabled = WallpaperStore.exists(this)
        buildPresetGrid()
    }

    /** One tappable swatch per ready-made gradient. Tapping one replaces any photo. */
    private fun buildPresetGrid() {
        val grid = findViewById<GridLayout>(R.id.preset_grid)
        grid.removeAllViews()
        // Fit as many 108dp-wide swatches per row as the screen allows (portrait or landscape).
        grid.columnCount = ((resources.configuration.screenWidthDp - 96) / 108).coerceIn(3, 7)
        val selectedKey = if (WallpaperStore.exists(this)) null else prefs.wallpaperPreset
        for (preset in Wallpapers.presets) {
            val selected = preset.key == selectedKey
            val swatch = View(this).apply {
                background = preset.drawable(dp(12).toFloat()).apply {
                    if (selected) setStroke(dp(4), Color.parseColor("#FF5722"))
                }
                contentDescription = preset.name
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
                addView(swatch, LinearLayout.LayoutParams(dp(96), dp(64)))
                addView(TextView(this@SettingsActivity).apply {
                    text = preset.name
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(if (selected) "#FF5722" else "#555555"))
                    if (selected) setTypeface(typeface, Typeface.BOLD)
                })
                setOnClickListener {
                    WallpaperStore.clear(this@SettingsActivity)
                    prefs.wallpaperPreset = preset.key
                    showWallpaper()
                }
            }
            grid.addView(cell)
        }
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun showLockStatus() {
        val owner = Kiosk.isDeviceOwner(this)
        findViewById<TextView>(R.id.lock_status).text = if (owner) {
            "Full lock is ON. The child cannot leave this app, open recent apps or pull down the notification bar. " +
                "The Home button always returns here."
        } else {
            "Basic mode. This app is the home screen, but a child may still reach other apps with the " +
                "recent-apps button or the notification bar.\n\n" +
                "For the full lock, connect the tablet to a computer and run (once):\n" +
                "adb shell dpm set-device-owner $packageName/.KidsAdminReceiver\n\n" +
                "This only works when no Google or other accounts are on the tablet. See the README."
        }
        findViewById<Button>(R.id.remove_owner_button).visibility = if (owner) View.VISIBLE else View.GONE
    }

    @Suppress("DEPRECATION")
    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        // When fully locked, the system picker must be allowed to open just for this moment.
        intent.resolveActivity(packageManager)?.packageName?.let { Kiosk.setAllowedPackages(this, it) }
        try {
            startActivityForResult(intent, REQ_WALLPAPER)
        } catch (e: Exception) {
            Kiosk.setAllowedPackages(this)
            Toast.makeText(this, "No picture picker available", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_WALLPAPER) return
        Kiosk.setAllowedPackages(this) // close the picker exception again
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        Thread {
            val ok = WallpaperStore.save(this, uri)
            runOnUiThread {
                if (!ok) Toast.makeText(this, "Couldn't use that picture", Toast.LENGTH_SHORT).show()
                showWallpaper()
            }
        }.start()
    }

    private fun iconSizeText(dp: Int) = when {
        dp < 120 -> "Large"
        dp < 170 -> "Extra large"
        else -> "Huge"
    } + " ($dp)"

    private fun onChange(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) block(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private companion object {
        const val REQ_WALLPAPER = 10
        const val ICON_MIN_DP = 96
        const val ICON_MAX_DP = 240
        const val AUTO_CLOSE_MS = 3 * 60_000L
    }
}
