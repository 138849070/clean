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

    /** Shizuku 服务是否运行 */
    fun serviceRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    /** 本应用是否已获得 Shizuku 授权 */
    fun permissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /** Shizuku 是否就绪（服务运行 + 已授权） */
    fun available(): Boolean = serviceRunning() && permissionGranted()

    /** 请求 Shizuku 授权（ShizukuManager 会弹出授权对话框） */
    fun requestPermission(requestCode: Int) {
        try {
            if (serviceRunning()) {
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
     * 文件数、总大小、其中缓存大小（扩展规则，覆盖各应用自定义缓存目录）。
     */
    data class DataStats(val fileCount: Long, val totalSize: Long, val cacheSize: Long)

    /** Android/data 下识别出的缓存目录条目 */
    data class CacheEntry(val path: String, val size: Long, val pkg: String)

    /**
     * 通过 Shizuku 列出 Android/data 下所有命中缓存规则的子目录（含各应用自定义缓存目录）。
     * 规则：cache/caches/.cache/code_cache 精确名 + 名称含 cache/temp/log/thumb 的目录，
     * 覆盖 imageloader/webcache/adcache/videocache 等非标准缓存目录（原应用同等级覆盖）。
     */
    fun listAndroidDataCaches(): List<CacheEntry> {
        if (!available()) return emptyList()
        val dataRoot = "/storage/emulated/0/Android/data"
        val script =
            "find $dataRoot -mindepth 2 -maxdepth 8 -type d \\(" +
                " -name \"cache\" -o -name \"caches\" -o -name \".cache\" -o -name \"code_cache\"" +
                " -o -iname \"*cache*\" -o -iname \"*temp*\" -o -iname \"*log*\" -o -iname \"*thumb*\"" +
                " \\) 2>/dev/null | while read d; do du -sk \"\$d\" 2>/dev/null; done"
        val out = exec(script) ?: return emptyList()
        val result = ArrayList<CacheEntry>()
        for (line in out.lineSequence()) {
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) continue
            val kb = parts[0].toLongOrNull() ?: continue
            val path = parts[1]
            val pkg = path.removePrefix("$dataRoot/").substringBefore('/')
            result.add(CacheEntry(path, kb * 1024, pkg))
        }
        return result
    }

    fun scanAndroidData(): DataStats? {
        if (!available()) return null
        val dataRoot = "/storage/emulated/0/Android/data"
        val obbRoot = "/storage/emulated/0/Android/obb"
        // 注意：Android toybox 的 du 不支持 -b，用 -sk 输出 KB 再换算字节
        val script =
            "find $dataRoot $obbRoot -type f 2>/dev/null | wc -l;" +
                "du -sk $dataRoot $obbRoot 2>/dev/null | awk '{s+=\$1} END {print s*1024}'"
        val out = exec(script) ?: return null
        val lines = out.trim().split("\n")
        if (lines.size < 2) return null
        val cacheSize = listAndroidDataCaches().sumOf { it.size }
        return DataStats(
            fileCount = lines[0].trim().toLongOrNull() ?: 0L,
            totalSize = lines[1].trim().toLongOrNull() ?: 0L,
            cacheSize = cacheSize
        )
    }
}
