package com.kidslauncher.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** The child's home screen: big icons for the allowed apps only. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var grid: GridView
    private lateinit var wallpaper: ImageView
    private lateinit var emptyText: TextView
    private lateinit var pausedBar: View

    private val handler = Handler(Looper.getMainLooper())
    private val openParentArea = Runnable {
        @Suppress("DEPRECATION")
        startActivityForResult(PinActivity.verifyIntent(this), REQ_PIN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        grid = findViewById(R.id.app_grid)
        wallpaper = findViewById(R.id.wallpaper)
        emptyText = findViewById(R.id.empty_text)
        pausedBar = findViewById(R.id.paused_bar)

        // Hold-to-open parent button, so a quick tap from a child does nothing.
        findViewById<View>(R.id.parent_button).setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    handler.postDelayed(openParentArea, HOLD_TO_OPEN_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(openParentArea)
                }
            }
            true
        }

        findViewById<View>(R.id.resume_button).setOnClickListener {
            prefs.kidModePaused = false
            refresh()
        }
        findViewById<View>(R.id.android_settings_button).setOnClickListener {
            startSafely(Intent(Settings.ACTION_SETTINGS))
        }
        findViewById<View>(R.id.home_settings_button).setOnClickListener {
            startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
        }

        grid.setOnItemClickListener { parent, _, position, _ ->
            val app = parent.adapter.getItem(position) as AppEntry
            launch(app)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.hasPin()) {
            // First run: a grown-up must set a PIN before anything else.
            startActivity(PinActivity.setupIntent(this))
            return
        }
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !prefs.kidModePaused) hideSystemBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // This is the home screen: Back never leaves it.
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PIN && resultCode == RESULT_OK) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun refresh() {
        applyKidMode()
        loadWallpaper()
        loadApps()
        VolumeGuardService.sync(this)
    }

    private fun applyKidMode() {
        if (prefs.kidModePaused) {
            pausedBar.visibility = View.VISIBLE
            showSystemBars()
            if (Kiosk.isInLockTask(this)) runCatching { stopLockTask() }
            Kiosk.release(this)
            if (prefs.removeOwnerRequested) {
                prefs.removeOwnerRequested = false
                Kiosk.removeDeviceOwner(this)
                Toast.makeText(this, "Full lock removed. The app can now be uninstalled.", Toast.LENGTH_LONG).show()
            }
        } else {
            pausedBar.visibility = View.GONE
            hideSystemBars()
            if (Kiosk.isDeviceOwner(this)) {
                Kiosk.apply(this)
                if (!Kiosk.isInLockTask(this)) runCatching { startLockTask() }
            }
        }
    }

    private fun loadWallpaper() {
        wallpaper.setImageDrawable(Wallpapers.current(this))
    }

    private fun loadApps() {
        val apps = InstalledApps.allowed(this)
        val iconPx = dp(prefs.iconSizeDp)
        grid.columnWidth = iconPx + dp(32)
        grid.adapter = AppAdapter(apps, iconPx)
        emptyText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launch(app: AppEntry) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "${app.label} can't be opened", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(intent)
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "That can't be opened right now", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    @Suppress("DEPRECATION")
    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    /** Builds one big tile (icon + name) per allowed app. */
    private inner class AppAdapter(private val apps: List<AppEntry>, private val iconPx: Int) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tile = convertView as? LinearLayout ?: createTile()
            val app = apps[position]
            (tile.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (tile.getChildAt(1) as TextView).text = app.label
            tile.contentDescription = app.label
            return tile
        }

        private fun createTile(): LinearLayout {
            val ctx = this@MainActivity
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(16), dp(16), dp(16), dp(12))
                setBackgroundResource(R.drawable.tile_bg)
                addView(ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(iconPx, iconPx))
                addView(TextView(ctx).apply {
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setShadowLayer(6f, 0f, 2f, Color.argb(200, 0, 0, 0))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, (iconPx / resources.displayMetrics.density / 6f).coerceIn(16f, 30f))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(8), 0, 0)
                }, LinearLayout.LayoutParams(iconPx, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private companion object {
        const val REQ_PIN = 1
        const val HOLD_TO_OPEN_MS = 3000L
    }
}
