package com.clean.cleaner.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager

object StatusBarUtil {

    /** 透明状态栏 + 设置图标明暗。lightIcons=true 表示深色图标（浅色背景） */
    fun transparent(activity: Activity, lightIcons: Boolean) {
        try {
            val window = activity.window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 30) {
                val controller = window.insetsController
                controller?.setSystemBarsAppearance(
                    if (lightIcons) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                val flag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    if (lightIcons) window.decorView.systemUiVisibility or flag
                    else window.decorView.systemUiVisibility and flag.inv()
            }
        } catch (ignored: Throwable) {
            // 个别 ROM 可能限制系统栏配置，忽略即可，不影响使用
        }
    }

    /** 当前是否深色模式 */
    fun isDarkMode(activity: Activity): Boolean {
        val mode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    /** 状态栏高度（px） */
    fun statusBarHeight(activity: Activity): Int {
        val res = activity.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }
}
