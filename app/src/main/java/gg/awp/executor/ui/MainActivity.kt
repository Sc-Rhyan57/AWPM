package gg.awp.executor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import gg.awp.executor.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasOverlayPermission()) {
            checkStoragePermissionThenStart()
        } else {
            Toast.makeText(this, "Overlay permission denied — AWP cannot float without it.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val storagePermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasOverlayPermission()) {
            checkStoragePermissionThenStart()
        } else {
            Toast.makeText(this, "AWP needs overlay permission to float over apps.", Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                overlayPermLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }

    private fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this)
        else true

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else true

    private fun checkStoragePermissionThenStart() {
        if (hasStoragePermission()) {
            startOverlay()
        } else {
            Toast.makeText(this, "AWP needs full access to storage to read scripts from your Executor.", Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    storagePermLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                    storagePermLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
                }
            } else {
                startOverlay()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasOverlayPermission() && !OverlayService.running) {
            startOverlay()
        }
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }
}
