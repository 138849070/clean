package com.clean.cleaner

import android.app.Application
import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.clean.cleaner.scan.MainScanResult
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    companion object {
        lateinit var instance: App
            private set

        /** 本次打开 App 后累计清理的字节数 */
        var sessionCleaned: Long = 0L

        /** 主界面最近一次一键扫描结果（内存缓存，供页面恢复时展示） */
        var lastScan: MainScanResult? = null

        fun addCleaned(bytes: Long) {
            if (bytes <= 0) return
            sessionCleaned += bytes
            val prefs = instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
            val total = prefs.getLong("total_cleaned", 0L) + bytes
            prefs.edit()
                .putLong("total_cleaned", total)
                .putLong("last_clean_time", System.currentTimeMillis())
                .apply()
        }

        fun totalCleaned(): Long =
            instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
                .getLong("total_cleaned", 0L)

        /** 上次清理时间（用于定期清理提醒） */
        fun lastCleanTime(): Long =
            instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
                .getLong("last_clean_time", 0L)

        /** 「不要清理」保护的文件/文件夹列表 */
        fun protectedPaths(): Set<String> =
            instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
                .getStringSet("protected", emptySet()) ?: emptySet()

        fun addProtected(path: String) {
            val prefs = instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
            val set = HashSet(prefs.getStringSet("protected", emptySet()) ?: emptySet())
            set.add(path)
            prefs.edit().putStringSet("protected", set).apply()
        }

        fun removeProtected(path: String) {
            val prefs = instance.getSharedPreferences("clean", Context.MODE_PRIVATE)
            val set = HashSet(prefs.getStringSet("protected", emptySet()) ?: emptySet())
            set.remove(path)
            prefs.edit().putStringSet("protected", set).apply()
        }

        /** 是否处于保护路径下（路径本身或其祖先被保护） */
        fun isProtected(path: String): Boolean {
            val s = path.trimEnd('/')
            return protectedPaths().any { p ->
                val pp = p.trimEnd('/')
                s == pp || s.startsWith("$pp/")
            }
        }

        /** 崩溃日志文件（应用专属目录，无需权限） */
        fun crashLogFile(): File =
            File(instance.getExternalFilesDir(null), "crash.log")

        fun writeCrashLog(thread: Thread, throwable: Throwable) {
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("==== Crash at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ====")
                pw.println("Thread: ${thread.name}")
                throwable.printStackTrace(pw)
                pw.flush()
                val content = sw.toString()

                // 应用专属目录
                crashLogFile().writeText(content)

                // 同时写一份到公共下载目录（有存储权限时可写）
                try {
                    val down = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "clean_crash.txt"
                    )
                    down.writeText(content)
                } catch (ignored: Exception) {
                }
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 全局崩溃捕获：记录堆栈，便于定位闪退原因
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(thread, throwable)
            try {
                Toast.makeText(
                    this,
                    "应用出现异常，日志已保存到 Download/clean_crash.txt",
                    Toast.LENGTH_LONG
                ).show()
            } catch (ignored: Exception) {
            }
            // 不吞掉异常，让系统继续崩溃流程（避免应用停留在异常状态）
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
