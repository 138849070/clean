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
        "logs", "log", "thumbs", "thumbnails", ".thumbnails",
        // 应用自定义缓存目录（扩展规则，对齐主流应用行为）
        "imagecache", "imageloader", "webcache", "webviewcache",
        "videocache", "video_cache", "netcache", "network_cache",
        "downloadcache", "download_cache", "adcache", "banner", "splash"
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

    /** 通过 Shizuku 扫描 Android/data 下各应用的缓存目录（含自定义缓存目录） */
    private fun scanAndroidDataCaches() {
        val caches = ShizukuShell.listAndroidDataCaches()
        for (entry in caches) {
            if (Thread.currentThread().isInterrupted) return
            val name = entry.path.substringAfterLast('/')
            items.add(
                ScanItem(
                    path = entry.path,
                    name = name,
                    size = entry.size,
                    isDir = true,
                    groupKey = "cache",
                    groupLabel = "缓存垃圾",
                    kind = "cache",
                    extra = "${entry.pkg} · ${SizeUtils.format(entry.size)}"
                )
            )
            onFound(entry.size)
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
