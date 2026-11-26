package com.kidslauncher.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

/** Asks for the parent PIN, or sets a new one. */
class PinActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var input: EditText
    private lateinit var confirm: EditText
    private lateinit var error: TextView
    private var setupMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)
        prefs = Prefs(this)
        setupMode = intent.getBooleanExtra(EXTRA_SETUP, false) || !prefs.hasPin()

        input = findViewById(R.id.pin_input)
        confirm = findViewById(R.id.pin_confirm)
        error = findViewById(R.id.pin_error)
        val title = findViewById<TextView>(R.id.pin_title)
        val message = findViewById<TextView>(R.id.pin_message)
        val cancel = findViewById<View>(R.id.pin_cancel)

        if (setupMode) {
            title.text = if (prefs.hasPin()) "Change parent PIN" else "Welcome, grown-up!"
            message.text = "Choose a PIN of at least 4 numbers.\nYou'll need it to change settings or unlock the tablet."
            confirm.visibility = View.VISIBLE
            // On first run there is no way around setting a PIN.
            cancel.visibility = if (prefs.hasPin()) View.VISIBLE else View.GONE
        } else {
            title.text = "Grown-ups only"
            message.text = "Enter the parent PIN"
        }

        cancel.setOnClickListener { setResult(RESULT_CANCELED); finish() }
        findViewById<View>(R.id.pin_ok).setOnClickListener { submit() }
        val lastField = if (setupMode) confirm else input
        lastField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) { submit(); true } else false
        }
        input.requestFocus()
    }

    private fun submit() {
        val pin = input.text.toString()
        if (setupMode) {
            when {
                pin.length < 4 -> error.text = "The PIN must be at least 4 numbers"
                pin != confirm.text.toString() -> error.text = "The two PINs don't match"
                else -> {
                    prefs.setPin(pin)
                    setResult(RESULT_OK)
                    finish()
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        if (now < prefs.lockoutUntil) {
            val secs = (prefs.lockoutUntil - now + 999) / 1000
            error.text = "Too many tries. Wait $secs seconds."
            input.text.clear()
            return
        }
        if (prefs.checkPin(pin)) {
            prefs.failedAttempts = 0
            setResult(RESULT_OK)
            finish()
        } else {
            val failed = prefs.failedAttempts + 1
            if (failed >= MAX_ATTEMPTS) {
                prefs.failedAttempts = 0
                prefs.lockoutUntil = now + LOCKOUT_MS
                error.text = "Too many tries. Wait ${LOCKOUT_MS / 1000} seconds."
            } else {
                prefs.failedAttempts = failed
                error.text = "Wrong PIN"
            }
            input.text.clear()
        }
    }

    companion object {
        private const val EXTRA_SETUP = "setup"
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 60_000L

        fun setupIntent(ctx: Context) = Intent(ctx, PinActivity::class.java).putExtra(EXTRA_SETUP, true)
        fun verifyIntent(ctx: Context) = Intent(ctx, PinActivity::class.java)
    }
}
