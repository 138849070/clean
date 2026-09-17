package com.clean.cleaner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.clean.cleaner.R

class PermissionActivity : AppCompatActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        findViewById<android.widget.Button>(R.id.btnGrant).setOnClickListener {
            requestAccess()
        }
        findViewById<android.widget.Button>(R.id.btnCheck).setOnClickListener {
            if (hasStorageAccess()) {
                setResult(RESULT_OK)
                finish()
            } else {
                requestAccess()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasStorageAccess()) {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    companion object {
        fun hasStorageAccess(): Boolean {
            return if (Build.VERSION.SDK_INT >= 30) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    com.clean.cleaner.App.instance,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
