package com.clean.cleaner.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.scan.ShizukuShell
import com.clean.cleaner.util.Settings
import com.clean.cleaner.util.StatusBarUtil

class SettingsActivity : AppCompatActivity() {

    /** SAF 选择器：授权 Android/data 访问（MT管理器同款） */
    private val openTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (ignored: Exception) {
                }
                Settings.setSafTreeUri(this, uri.toString())
                Toast.makeText(this, "已授权 Android/data 访问，重新扫描即可生效", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        val swAutoScan = findViewById<Switch>(R.id.swAutoScan)
        val swShizuku = findViewById<Switch>(R.id.swShizuku)
        val swAutoClean = findViewById<Switch>(R.id.swAutoClean)
        val swEmptyFiles = findViewById<Switch>(R.id.swEmptyFiles)
        val swDarkFollow = findViewById<Switch>(R.id.swDarkFollow)
        val swKeepScreen = findViewById<Switch>(R.id.swKeepScreen)
        val swShowCleaned = findViewById<Switch>(R.id.swShowCleaned)
        val swPeriodic = findViewById<Switch>(R.id.swPeriodic)
        val etLargeMin = findViewById<EditText>(R.id.etLargeMin)
        val etExclude = findViewById<EditText>(R.id.etExclude)
        val etPeriodicDays = findViewById<EditText>(R.id.etPeriodicDays)

        swAutoScan.isChecked = Settings.autoScan(this)
        swShizuku.isChecked = Settings.shizukuAccess(this)
        swAutoClean.isChecked = Settings.autoClean(this)
        swEmptyFiles.isChecked = Settings.emptyFiles(this)
        swDarkFollow.isChecked = Settings.darkFollow(this)
        swKeepScreen.isChecked = Settings.keepScreen(this)
        swShowCleaned.isChecked = Settings.showCleaned(this)
        swPeriodic.isChecked = Settings.periodicClean(this)
        etLargeMin.setText(Settings.largeMinMb(this).toString())
        etExclude.setText(Settings.excludeRaw(this))
        etPeriodicDays.setText(Settings.periodicDays(this).toString())

        swAutoScan.setOnCheckedChangeListener { _, v -> Settings.setAutoScan(this, v) }
        // Shizuku：访问 Android/data 受限目录
        swShizuku.setOnCheckedChangeListener { _, v ->
            Settings.setShizukuAccess(this, v)
            if (v && !ShizukuShell.available()) {
                ShizukuShell.requestPermission(9090)
            }
        }
        // SAF 授权：直接打开系统文件选择器并定位到 Android/data
        findViewById<android.widget.TextView>(R.id.btnSafAuthorize).setOnClickListener {
            val initialUri = try {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Android/data"
                )
            } catch (e: Exception) {
                null
            }
            if (initialUri != null) openTree.launch(initialUri)
            else openTree.launch(null)
        }
        swAutoClean.setOnCheckedChangeListener { _, v -> Settings.setAutoClean(this, v) }
        swEmptyFiles.setOnCheckedChangeListener { _, v -> Settings.setEmptyFiles(this, v) }
        swKeepScreen.setOnCheckedChangeListener { _, v -> Settings.setKeepScreen(this, v) }
        swShowCleaned.setOnCheckedChangeListener { _, v -> Settings.setShowCleaned(this, v) }
        swPeriodic.setOnCheckedChangeListener { _, v ->
            Settings.setPeriodicClean(this, v)
            if (v) Toast.makeText(this, "定期清理已开启", Toast.LENGTH_SHORT).show()
        }
        etPeriodicDays.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                etPeriodicDays.text.toString().toIntOrNull()?.let {
                    Settings.setPeriodicDays(this, it)
                }
            }
        }

        // 深色模式：开=跟随系统，关=强制深色
        swDarkFollow.setOnCheckedChangeListener { _, v ->
            Settings.setDarkFollow(this, v)
            AppCompatDelegate.setDefaultNightMode(
                if (v) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        findViewById<Button>(R.id.btnSaveLarge).setOnClickListener {
            val v = etLargeMin.text.toString().toIntOrNull()
            if (v == null || v < 1) {
                Toast.makeText(this, "请输入大于 0 的整数", Toast.LENGTH_SHORT).show()
            } else {
                Settings.setLargeMinMb(this, v)
                App.lastScan = null // 阈值变更后需重新扫描
                Toast.makeText(this, "已保存，重新扫描后生效", Toast.LENGTH_SHORT).show()
            }
        }

        etExclude.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                Settings.setExclude(this, etExclude.text.toString())
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9090) {
            if (ShizukuShell.available()) {
                Toast.makeText(this, "Shizuku 已授权，重新扫描即可扫全 Android/data", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Shizuku 未授权：请先安装 Shizuku 并在其中授权本应用", Toast.LENGTH_LONG).show()
            }
        }
    }
}
