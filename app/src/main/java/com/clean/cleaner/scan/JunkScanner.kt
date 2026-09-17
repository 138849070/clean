package com.clean.cleaner.scan

import android.os.Environment
import com.clean.cleaner.util.SizeUtils
import java.io.File

/**
 * 垃圾扫描：缓存目录、临时文件、日志、缩略图、空文件、安装包。
 * 一次遍历完成统计，避免重复 IO。
 */
class JunkScanner(
    private val onProgress: (String) -> Unit,
    private val onFound: (Long) -> Unit = {}
) {

    val items = mutableListOf<ScanItem>()
    val apkItems = mutableListOf<ScanItem>()
    val emptyDirs = mutableListOf<ScanItem>()

    private val junkDirNames = setOf(
        "cache", "caches", ".cache", "tmp", "temp",
        "logs", "log", "thumbs", "thumbnails", ".thumbnails"
    )
    private val junkFileExts = setOf("tmp", "temp", "log", "bak", "old", "cache", "thumb", "thumbnail", "part", "apkcache")
    private val apkExt = "apk"

    private val scannedJunkDirs = HashSet<String>()
    private val junkSizes = HashMap<String, Long>()

    fun scan() {
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists()) return
        scanDir(root, inJunk = null)
        // 汇总目录型垃圾
        for ((path, size) in junkSizes) {
            val f = File(path)
            val label = junkLabel(f.name)
            items.add(
                ScanItem(
                    path = path,
                    name = f.name,
                    size = size,
                    isDir = true,
                    groupKey = label.first,
                    groupLabel = label.second,
                    kind = label.first,
                    extra = "${countChildren(f)} 项"
                )
            )
        }
        // 空文件夹（独立小遍历，只找无任何子项的目录）
        findEmptyDirs(root)
        // Shizuku：Android/data 下各应用的缓存目录（File API 读不到）
        if (ShizukuShell.available()) scanAndroidDataCaches()
    }

    /** 通过 Shizuku 扫描 Android/data 下各应用的 cache/code_cache 目录 */
    private fun scanAndroidDataCaches() {
        val script =
            "find /storage/emulated/0/Android/data -type d \\( -name cache -o -name code_cache \\) 2>/dev/null | " +
                "while read d; do du -sk \"\$d\" 2>/dev/null; done"
        val out = ShizukuShell.exec(script) ?: return
        for (line in out.lineSequence()) {
            if (Thread.currentThread().isInterrupted) return
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) continue
            val kb = parts[0].toLongOrNull() ?: continue
            val path = parts[1]
            val size = kb * 1024
            val name = path.substringAfterLast('/')
            val pkg = path.removePrefix("/storage/emulated/0/Android/data/").substringBefore('/')
            items.add(
                ScanItem(
                    path = path,
                    name = name,
                    size = size,
                    isDir = true,
                    groupKey = "cache",
                    groupLabel = "缓存垃圾",
                    kind = "cache",
                    extra = "$pkg · ${SizeUtils.format(size)}"
                )
            )
            onFound(size)
        }
    }

    private fun scanDir(dir: File, inJunk: String?) {
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (Thread.currentThread().isInterrupted) return
            onProgress(f.absolutePath)
            if (f.isDirectory) {
                val name = f.name.lowercase()
                if (inJunk == null && name in junkDirNames && scannedJunkDirs.add(f.absolutePath)) {
                    // 进入垃圾目录，累计其大小
                    var size = 0L
                    scanJunkTree(f) { size += it.length() }
                    junkSizes[f.absolutePath] = size
                } else {
                    scanDir(f, inJunk)
                }
            } else {
                if (inJunk != null) {
                    junkSizes[inJunk] = junkSizes.getOrDefault(inJunk, 0) + f.length()
                } else {
                    val ext = f.extension.lowercase()
                    val size = f.length()
                    when {
                        ext == apkExt && size > 0 -> apkItems.add(
                            ScanItem(f.absolutePath, f.name, size, kind = "apk",
                                groupLabel = "安装包", extra = SizeUtils.format(size))
                        )
                        ext in junkFileExts && size >= 0 -> {
                            val label = junkLabel(f.name)
                            items.add(
                                ScanItem(f.absolutePath, f.name, size,
                                    groupKey = label.first, groupLabel = label.second,
                                    kind = label.first, extra = SizeUtils.format(size))
                            )
                            onFound(size)
                        }
                    }
                }
            }
        }
    }

    private fun scanJunkTree(dir: File, acc: (File) -> Unit) {
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (Thread.currentThread().isInterrupted) return
            if (f.isDirectory) scanJunkTree(f, acc) else acc(f)
        }
    }

    private fun findEmptyDirs(dir: File) {
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (Thread.currentThread().isInterrupted) return
            if (f.isDirectory) {
                val children = f.listFiles()
                if (children == null || children.isEmpty()) {
                    emptyDirs.add(
                        ScanItem(f.absolutePath, f.name, 0, isDir = true,
                            groupKey = "empty", groupLabel = "空文件夹", kind = "empty",
                            extra = "0 B")
                    )
                } else {
                    findEmptyDirs(f)
                }
            }
        }
    }

    private fun countChildren(dir: File): Int {
        val list = dir.listFiles() ?: return 0
        var n = 0
        for (f in list) {
            n++
            if (f.isDirectory) n += countChildren(f)
        }
        return n
    }

    private fun junkLabel(name: String): Pair<String, String> {
        val n = name.lowercase()
        return when {
            n.contains("thumb") -> "thumb" to "缩略图缓存"
            n.contains("log") -> "log" to "日志文件"
            n.contains("tmp") || n.contains("temp") || n.contains("part") || n.contains("bak") || n.contains("old") -> "tmp" to "临时文件"
            else -> "cache" to "缓存垃圾"
        }
    }
}
