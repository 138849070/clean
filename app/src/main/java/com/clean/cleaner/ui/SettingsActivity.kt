package com.clean.cleaner.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.util.Settings
import com.clean.cleaner.util.StatusBarUtil

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        val swAutoScan = findViewById<Switch>(R.id.swAutoScan)
        val swAutoClean = findViewById<Switch>(R.id.swAutoClean)
        val swEmptyFiles = findViewById<Switch>(R.id.swEmptyFiles)
        val swDarkFollow = findViewById<Switch>(R.id.swDarkFollow)
        val swKeepScreen = findViewById<Switch>(R.id.swKeepScreen)
        val swShowCleaned = findViewById<Switch>(R.id.swShowCleaned)
        val etLargeMin = findViewById<EditText>(R.id.etLargeMin)
        val etExclude = findViewById<EditText>(R.id.etExclude)

        swAutoScan.isChecked = Settings.autoScan(this)
        swAutoClean.isChecked = Settings.autoClean(this)
        swEmptyFiles.isChecked = Settings.emptyFiles(this)
        swDarkFollow.isChecked = Settings.darkFollow(this)
        swKeepScreen.isChecked = Settings.keepScreen(this)
        swShowCleaned.isChecked = Settings.showCleaned(this)
        etLargeMin.setText(Settings.largeMinMb(this).toString())
        etExclude.setText(Settings.excludeRaw(this))

        swAutoScan.setOnCheckedChangeListener { _, v -> Settings.setAutoScan(this, v) }
        swAutoClean.setOnCheckedChangeListener { _, v -> Settings.setAutoClean(this, v) }
        swEmptyFiles.setOnCheckedChangeListener { _, v -> Settings.setEmptyFiles(this, v) }
        swKeepScreen.setOnCheckedChangeListener { _, v -> Settings.setKeepScreen(this, v) }
        swShowCleaned.setOnCheckedChangeListener { _, v -> Settings.setShowCleaned(this, v) }

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
}
