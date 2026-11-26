package com.kidslauncher.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Parent ticks the apps the child is allowed to see and open. */
class AppPickerActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var apps: List<AppEntry>
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        apps = InstalledApps.all(this)
        selected += prefs.allowedPackages

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF8F0"))
            fitsSystemWindows = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(32), dp(16), dp(32), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "Choose the apps your child can use"
            textSize = 24f
            setTextColor(Color.parseColor("#222222"))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            text = "Done"
            setOnClickListener { finish() }
        })
        root.addView(header)

        val list = ListView(this).apply {
            adapter = PickerAdapter()
            setOnItemClickListener { _, view, position, _ ->
                val pkg = apps[position].packageName
                if (!selected.remove(pkg)) selected += pkg
                (view as LinearLayout).findViewWithTag<CheckBox>(TAG_CHECK).isChecked = pkg in selected
                save()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun save() {
        prefs.allowedPackages = selected.toSet()
        // Let newly allowed apps run while the tablet is fully locked.
        if (!prefs.kidModePaused) Kiosk.setAllowedPackages(this)
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private inner class PickerAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView as? LinearLayout ?: createRow()
            val app = apps[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
            (row.getChildAt(2) as CheckBox).isChecked = app.packageName in selected
            return row
        }

        private fun createRow(): LinearLayout {
            val ctx = this@AppPickerActivity
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(32), dp(10), dp(32), dp(10))
                addView(ImageView(ctx), LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(TextView(ctx).apply {
                    textSize = 19f
                    setTextColor(Color.parseColor("#333333"))
                    setPadding(dp(20), 0, dp(20), 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(CheckBox(ctx).apply {
                    tag = TAG_CHECK
                    isClickable = false
                    isFocusable = false
                })
            }
        }
    }

    private companion object {
        const val TAG_CHECK = "check"
    }
}
