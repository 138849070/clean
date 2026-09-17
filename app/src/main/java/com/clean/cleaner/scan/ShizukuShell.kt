package com.clean.cleaner.scan

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 桥接：以 shell 权限访问 Android/data 等受限目录。
 * 通过反射调用 Shizuku 内部 newProcess（13.1.5 未公开但存在），
 * 返回的 ShizukuRemoteProcess 是 Process 子类。
 */
object ShizukuShell {

    private val newProcessMethod by lazy {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        m
    }

    /** Shizuku 服务是否运行且已授权 */
    fun available(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /** 请求 Shizuku 授权（结果在 onRequestPermissionsResult 回调） */
    fun requestPermission(requestCode: Int) {
        try {
            if (Shizuku.isPreV11() || android.os.Build.VERSION.SDK_INT >= 23) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (ignored: Throwable) {
        }
    }

    /** 执行一条 shell 命令并返回 stdout（失败返回 null） */
    fun exec(script: String): String? {
        return try {
            val p = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", script),
                null,
                null
            ) as Process
            p.inputStream.bufferedReader().use { it.readText() }.also { p.waitFor() }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 统计 Android/data（含 Android/obb）：
     * 文件数、总大小、其中缓存（cache/code_cache）大小。
     */
    data class DataStats(val fileCount: Long, val totalSize: Long, val cacheSize: Long)

    fun scanAndroidData(): DataStats? {
        if (!available()) return null
        val dataRoot = "/storage/emulated/0/Android/data"
        val obbRoot = "/storage/emulated/0/Android/obb"
        val script =
            "find $dataRoot $obbRoot -type f 2>/dev/null | wc -l;" +
                "du -sb $dataRoot $obbRoot 2>/dev/null | awk '{s+=\$1} END {print s+0}';" +
                "du -sb $dataRoot/*/cache $dataRoot/*/code_cache $obbRoot/* 2>/dev/null | awk '{s+=\$1} END {print s+0}'"
        val out = exec(script) ?: return null
        val lines = out.trim().split("\n").map { it.trim().toLongOrNull() ?: 0L }
        if (lines.size < 3) return null
        return DataStats(
            fileCount = lines[0],
            totalSize = lines[1],
            cacheSize = lines[2]
        )
    }
}
