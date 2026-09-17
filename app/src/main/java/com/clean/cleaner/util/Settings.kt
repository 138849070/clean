package com.clean.cleaner.util

import android.content.Context

/** 应用设置（SharedPreferences 封装） */
object Settings {
    private const val PREF = "settings"

    fun p(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 启动软件后自动扫描 */
    fun autoScan(ctx: Context): Boolean = p(ctx).getBoolean("auto_scan", false)
    fun setAutoScan(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("auto_scan", v).apply()
    }

    /** 扫描完成后自动进入垃圾清理 */
    fun autoClean(ctx: Context): Boolean = p(ctx).getBoolean("auto_clean", false)
    fun setAutoClean(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("auto_clean", v).apply()
    }

    /** 找出空白文件（0 字节） */
    fun emptyFiles(ctx: Context): Boolean = p(ctx).getBoolean("empty_files", false)
    fun setEmptyFiles(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("empty_files", v).apply()
    }

    /** 大文件最小值（MB），默认 10 */
    fun largeMinMb(ctx: Context): Int = p(ctx).getInt("large_min_mb", 10)
    fun setLargeMinMb(ctx: Context, v: Int) {
        p(ctx).edit().putInt("large_min_mb", v.coerceIn(1, 10240)).apply()
    }

    /** 排除扫描的文件夹（相对存储根目录，逗号分隔） */
    fun excludeRaw(ctx: Context): String = p(ctx).getString("exclude", "") ?: ""
    fun excludeList(ctx: Context): List<String> =
        excludeRaw(ctx).split(',', '，', ';', '；', '\n')
            .map { it.trim().trim('/') }
            .filter { it.isNotEmpty() }

    fun setExclude(ctx: Context, v: String) {
        p(ctx).edit().putString("exclude", v).apply()
    }

    /** 深色模式-跟随系统（关闭则强制深色） */
    fun darkFollow(ctx: Context): Boolean = p(ctx).getBoolean("dark_follow", true)
    fun setDarkFollow(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("dark_follow", v).apply()
    }

    /** 扫描/清理时屏幕常亮 */
    fun keepScreen(ctx: Context): Boolean = p(ctx).getBoolean("keep_screen", false)
    fun setKeepScreen(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("keep_screen", v).apply()
    }

    /** 首页显示累计清理记录 */
    fun showCleaned(ctx: Context): Boolean = p(ctx).getBoolean("show_cleaned", true)
    fun setShowCleaned(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("show_cleaned", v).apply()
    }
}
