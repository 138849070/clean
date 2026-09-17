package com.clean.cleaner

import android.app.Application
import android.content.Context

class App : Application() {
    companion object {
        lateinit var instance: App
            private set

        /** 本次打开 App 后累计清理的字节数 */
        var sessionCleaned: Long = 0L

        fun addCleaned(bytes: Long) {
            if (bytes <= 0) return
            sessionCleaned += bytes
            val prefs = instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
            val total = prefs.getLong("total_cleaned", 0L) + bytes
            prefs.edit().putLong("total_cleaned", total).apply()
        }

        fun totalCleaned(): Long =
            instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
                .getLong("total_cleaned", 0L)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
