package com.clean.cleaner.scan

import android.os.Environment
import com.clean.cleaner.util.Settings
import java.io.File

/** 主界面一键扫描的汇总结果 */
data class MainScanResult(
    val junkSize: Long = 0,
    val deepSize: Long = 0,
    val largeCount: Int = 0,
    val largeSize: Long = 0,
    val dupCount: Int = 0,
    val dupSize: Long = 0,
    val simCount: Int = 0,
    val simSize: Long = 0,
    val apkCount: Int = 0,
    val apkSize: Long = 0,
    val residueCount: Int = 0,
    val residueSize: Long = 0,
    val emptyCount: Int = 0,
    val emptyFileCount: Int = 0,
    val folderCount: Int = 0,
    val fileCount: Int = 0,
    val totalSize: Long = 0,
    /** MediaStore 全量统计（更全） */
    val mediaFiles: Long = 0,
    val mediaSize: Long = 0,
    val mediaImage: Long = 0,
    val mediaVideo: Long = 0,
    val mediaAudio: Long = 0,
    val mediaDoc: Long = 0,
    /** Shizuku：Android/data 受限目录全量统计 */
    val dataFiles: Long = 0,
    val dataSize: Long = 0,
    val dataCache: Long = 0
)

/** 主界面全局扫描：MediaStore 全量统计 + 一次文件遍历统计明细 */
class MainScan(
    private val context: android.content.Context,
    private val installedPackages: Set<String>,
    private val excludedPaths: Set<String> = emptySet(),
    private val scanEmptyFiles: Boolean = false,
    private val onProgress: (String) -> Unit = {}
) {

    private val junkDirNames = setOf(
        "cache", "caches", ".cache", "tmp", "temp",
        "logs", "log", "thumbs", "thumbnails", ".thumbnails"
    )
    private val junkFileExts = setOf(
        "tmp", "temp", "log", "bak", "old", "cache", "thumb", "thumbnail", "part", "apkcache"
    )
    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val deepPrefixes = listOf(
        "/Android/data/com.tencent.mm/",
        "/tencent/MicroMsg/",
        "/Android/data/com.tencent.mobileqq/",
        "/tencent/QQfile_recv/",
        "/tencent/QQ_Images/"
    )

    private var junkSize = 0L
    private var deepSize = 0L
    private var largeCount = 0
    private var largeSize = 0L
    private var apkCount = 0
    private var apkSize = 0L
    private var residueCount = 0
    private var residueSize = 0L
    private var emptyCount = 0
    private var emptyFileCount = 0
    private var folderCount = 0
    private var fileCount = 0
    private var totalSize = 0L

    private val dupCounts = HashMap<String, Int>()
    private val dupSizes = HashMap<String, Long>()
    private val simCounts = HashMap<String, Int>()
    private val simSizes = HashMap<String, Long>()

    private lateinit var root: File

    fun scan(): MainScanResult {
        root = Environment.getExternalStorageDirectory()
        // 1) MediaStore 全量统计
        val media = MediaScanner.scan(context)
        // 2) 文件遍历统计明细
        if (root.exists()) walkDir(root, inJunk = false, inResidue = false)
        // 3) SAF 授权通道：Android/data 受限目录全量统计（MT管理器同款，无需 Shizuku）
        var dataFiles = 0L
        var dataSize = 0L
        var dataCache = 0L
        if (Settings.safTreeUri(context) != null) {
            try {
                SafScanner.scan(context)?.let {
                    dataFiles = it.fileCount
                    dataSize = it.totalSize
                    dataCache = it.cacheSize
                }
            } catch (ignored: Exception) {
            }
        }
        return MainScanResult(
            junkSize = junkSize,
            deepSize = deepSize,
            largeCount = largeCount, largeSize = largeSize,
            dupCount = dupCounts.count { it.value >= 2 },
            dupSize = dupSizes.filterKeys { (dupCounts[it] ?: 0) >= 2 }.values.sum(),
            simCount = simCounts.count { it.value >= 2 },
            simSize = simSizes.filterKeys { (simCounts[it] ?: 0) >= 2 }.values.sum(),
            apkCount = apkCount, apkSize = apkSize,
            residueCount = residueCount, residueSize = residueSize,
            emptyCount = emptyCount, emptyFileCount = emptyFileCount,
            folderCount = folderCount, fileCount = fileCount,
            totalSize = totalSize,
            mediaFiles = media.fileCount, mediaSize = media.totalSize,
            mediaImage = media.imageCount, mediaVideo = media.videoCount,
            mediaAudio = media.audioCount, mediaDoc = media.docCount,
            dataFiles = dataFiles, dataSize = dataSize, dataCache = dataCache
        )
    }

    private fun walkDir(dir: File, inJunk: Boolean, inResidue: Boolean) {
        if (Thread.currentThread().isInterrupted) return
        val list = dir.listFiles() ?: return
        val parentPath = dir.absolutePath
        val isAndroidData = parentPath == "${root.absolutePath}/Android/data" ||
            parentPath == "${root.absolutePath}/Android/obb"
        for (f in list) {
            if (Thread.currentThread().isInterrupted) return
            // 排除用户指定的文件夹
            if (excludedPaths.isNotEmpty()) {
                val rel = f.absolutePath.removePrefix(root.absolutePath).trimStart('/')
                if (excludedPaths.any { rel == it || rel.startsWith("$it/") }) continue
            }
            onProgress(f.absolutePath)
            if (f.isDirectory) {
                folderCount++
                val children = f.listFiles()
                // null = 权限受限（如 Android/data 子目录），跳过但不计入空文件夹
                if (children == null) continue
                if (children.isEmpty()) {
                    emptyCount++
                    continue
                }
                val name = f.name.lowercase()
                val newJunk = inJunk || name in junkDirNames
                val isResiduePkg = !inResidue && isAndroidData && f.name !in installedPackages
                if (isResiduePkg) residueCount++
                walkDir(f, newJunk, inResidue || isResiduePkg)
            } else {
                fileCount++
                val size = f.length()
                totalSize += size
                if (size == 0L && scanEmptyFiles) emptyFileCount++
                if (inResidue) {
                    residueSize += size
                    continue
                }
                if (inJunk) {
                    junkSize += size
                    continue
                }
                val path = f.absolutePath
                if (deepPrefixes.any { path.contains(it) }) deepSize += size
                val ext = f.extension.lowercase()
                if (size > 0) {
                    if (ext == "apk") {
                        apkCount++
                        apkSize += size
                    }
                    if (size > 20L * 1024 * 1024) {
                        largeCount++
                        largeSize += size
                    }
                    if (ext in junkFileExts) junkSize += size
                    val dupKey = "${f.name}|$size"
                    dupCounts[dupKey] = (dupCounts[dupKey] ?: 0) + 1
                    dupSizes[dupKey] = (dupSizes[dupKey] ?: 0) + size
                    if (ext in imageExts && size >= 1024) {
                        val simKey = size.toString()
                        simCounts[simKey] = (simCounts[simKey] ?: 0) + 1
                        simSizes[simKey] = (simSizes[simKey] ?: 0) + size
                    }
                }
            }
        }
    }
}
