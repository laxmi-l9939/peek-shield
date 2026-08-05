package com.satish.peekshield

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.actionButton)

        actionButton.setOnClickListener {
            if (canDrawOverApps()) {
                startShade()
            } else {
                requestOverlayPermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        if (canDrawOverApps()) {
            statusText.text = getString(R.string.permission_granted_msg)
            actionButton.text = getString(R.string.activate_shade)
        } else {
            statusText.text = getString(R.string.permission_needed_msg)
            actionButton.text = getString(R.string.grant_permission)
        }
    }

    private fun canDrawOverApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startShade() {
        val intent = Intent(this, ShadeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }
}
