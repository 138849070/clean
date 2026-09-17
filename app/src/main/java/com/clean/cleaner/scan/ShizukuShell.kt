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

    /** Android/data 全量统计（含 obb） */
    data class DataStatsFull(
        val fileCount: Long,
        val folderCount: Long,
        val totalSize: Long,
        val apkCount: Long,
        val apkSize: Long,
        val largeCount: Long,
        val largeSize: Long,
        val emptyCount: Long,
        /** 全部缓存关键词目录（缓存垃圾） */
        val cacheSize: Long,
        val cacheCount: Long,
        /** 非精确 cache 名的关键词缓存（疑似缓存） */
        val suspectSize: Long,
        val suspectCount: Long
    )

    /**
     * Shizuku 全量统计 Android/data（含 Android/obb）：
     * 文件数/文件夹数/总大小/安装包/大文件(>20MB)/空文件夹。
     * 缓存部分由 listAndroidDataCaches 分类补充。
     */
    fun scanAndroidDataFull(): DataStatsFull? {
        if (!available()) return null
        val dataRoot = "/storage/emulated/0/Android/data"
        val obbRoot = "/storage/emulated/0/Android/obb"
        val script =
            "find $dataRoot $obbRoot -type f 2>/dev/null | wc -l;" +
                "find $dataRoot $obbRoot -type d 2>/dev/null | wc -l;" +
                "du -sk $dataRoot $obbRoot 2>/dev/null | awk 'BEGIN{s=0}{s+=\$1}END{print s*1024}';" +
                "find $dataRoot $obbRoot -type f -iname \"*.apk\" 2>/dev/null | wc -l;" +
                "find $dataRoot $obbRoot -type f -iname \"*.apk\" -exec du -sk {} + 2>/dev/null | awk 'BEGIN{s=0}{s+=\$1}END{print s*1024}';" +
                "find $dataRoot $obbRoot -type f -size +20480k 2>/dev/null | wc -l;" +
                "find $dataRoot $obbRoot -type f -size +20480k -exec du -sk {} + 2>/dev/null | awk 'BEGIN{s=0}{s+=\$1}END{print s*1024}';" +
                "find $dataRoot $obbRoot -type d -empty 2>/dev/null | wc -l"
        val out = exec(script) ?: return null
        val lines = out.trim().split("\n").map { it.trim().toLongOrNull() ?: 0L }
        if (lines.size < 8) return null
        // 缓存分类：全部关键词目录都算缓存垃圾（与清理页一致），非精确名单独记疑似缓存
        val caches = listAndroidDataCaches()
        val exactNames = setOf("cache", "caches", ".cache", "code_cache")
        val exact = caches.filter { exactNames.contains(it.path.substringAfterLast('/').lowercase()) }
        val suspect = caches.filterNot { exactNames.contains(it.path.substringAfterLast('/').lowercase()) }
        return DataStatsFull(
            fileCount = lines[0],
            folderCount = lines[1],
            totalSize = lines[2],
            apkCount = lines[3],
            apkSize = lines[4],
            largeCount = lines[5],
            largeSize = lines[6],
            emptyCount = lines[7],
            cacheSize = caches.sumOf { it.size },
            cacheCount = caches.size.toLong(),
            suspectSize = suspect.sumOf { it.size },
            suspectCount = suspect.size.toLong()
        )
    }

    /** Android/data 下非精确 cache 名的关键词缓存目录（疑似缓存） */
    fun listAndroidDataSuspect(): List<CacheEntry> {
        val exactNames = setOf("cache", "caches", ".cache", "code_cache")
        return listAndroidDataCaches()
            .filterNot { exactNames.contains(it.path.substringAfterLast('/').lowercase()) }
    }

    /** Android/data（含 obb）下所有 APK 文件 */
    fun listAndroidDataApks(): List<CacheEntry> {
        if (!available()) return emptyList()
        val dataRoot = "/storage/emulated/0/Android/data"
        val obbRoot = "/storage/emulated/0/Android/obb"
        val script =
            "find $dataRoot $obbRoot -type f -iname \"*.apk\" -exec du -sk {} + 2>/dev/null"
        val out = exec(script) ?: return emptyList()
        return parseSizePath(out)
    }

    /** Android/data（含 obb）下大于 minSize 字节的文件 */
    fun listAndroidDataLarge(minSize: Long): List<CacheEntry> {
        if (!available()) return emptyList()
        val dataRoot = "/storage/emulated/0/Android/data"
        val obbRoot = "/storage/emulated/0/Android/obb"
        val minKb = (minSize / 1024).coerceAtLeast(1)
        val script =
            "find $dataRoot $obbRoot -type f -size +${minKb}k -exec du -sk {} + 2>/dev/null"
        val out = exec(script) ?: return emptyList()
        return parseSizePath(out)
    }

    /** Android/data（含 obb）下所有空目录 */
    fun listAndroidDataEmptyDirs(): List<String> {
        if (!available()) return emptyList()
        val dataRoot = "/storage/emulated/0/Android/data"
        val obbRoot = "/storage/emulated/0/Android/obb"
        val out = exec("find $dataRoot $obbRoot -type d -empty 2>/dev/null") ?: return emptyList()
        return out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    private fun parseSizePath(out: String): List<CacheEntry> {
        val result = ArrayList<CacheEntry>()
        for (line in out.lineSequence()) {
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) continue
            val kb = parts[0].toLongOrNull() ?: continue
            val path = parts[1]
            result.add(CacheEntry(path, kb * 1024, ""))
        }
        return result
    }
}
