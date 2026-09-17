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
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.util.StatusBarUtil

class PermissionActivity : AppCompatActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val mediaLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            ensureManageAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_permission)

        findViewById<android.widget.Button>(R.id.btnGrant).setOnClickListener {
            requestAccess()
        }
        findViewById<android.widget.Button>(R.id.btnCheck).setOnClickListener {
            if (hasStorageAccess() && hasMediaAccess()) {
                setResult(RESULT_OK)
                finish()
            } else {
                requestAccess()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasStorageAccess() && hasMediaAccess()) {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun requestAccess() {
        // Android 13+：先请求读取媒体库的权限（全量扫描需要）
        if (Build.VERSION.SDK_INT >= 33) {
            val needed = listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                mediaLauncher.launch(needed.toTypedArray())
                return
            }
        }
        ensureManageAccess()
        if (Build.VERSION.SDK_INT < 30) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun ensureManageAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    companion object {
        fun hasStorageAccess(): Boolean {
            return if (Build.VERSION.SDK_INT >= 30) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    App.instance,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        /** Android 13+ 需要媒体权限才能读到媒体库全量数据 */
        fun hasMediaAccess(): Boolean {
            if (Build.VERSION.SDK_INT < 33) return true
            return listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ).all {
                ContextCompat.checkSelfPermission(App.instance, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
