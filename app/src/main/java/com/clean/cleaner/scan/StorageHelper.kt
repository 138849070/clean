package com.clean.cleaner.scan

import android.os.Environment
import android.os.StatFs
import com.clean.cleaner.util.SizeUtils
import java.io.File

object StorageHelper {

    val root: File = Environment.getExternalStorageDirectory()

    fun totalSpace(): Long = StatFs(Environment.getDataDirectory().path).totalBytes

    fun freeSpace(): Long = StatFs(Environment.getDataDirectory().path).availableBytes

    fun usedSpace(): Long = totalSpace() - freeSpace()

    val categories = listOf("image", "video", "audio", "doc", "apk", "other")

    fun categoryLabel(key: String): String = when (key) {
        "image" -> "图片"
        "video" -> "视频"
        "audio" -> "音频"
        "doc" -> "文档"
        "apk" -> "安装包"
        "other" -> "其他"
        else -> key
    }

    fun categoryColor(key: String): Int = when (key) {
        "image" -> 0xFF0A84FF.toInt()
        "video" -> 0xFFAF52DE.toInt()
        "audio" -> 0xFFFF9500.toInt()
        "doc" -> 0xFF32ADE6.toInt()
        "apk" -> 0xFFFF3B30.toInt()
        else -> 0xFF8A8F8D.toInt()
    }

    private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
    private val videoExt = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "rmvb", "rm")
    private val audioExt = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "amr", "mid", "ape")
    private val docExt = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", "md", "epub", "csv")

    fun categoryOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in imageExt -> "image"
            ext in videoExt -> "video"
            ext in audioExt -> "audio"
            ext in docExt -> "doc"
            ext == "apk" -> "apk"
            else -> "other"
        }
    }

    /**
     * 分类统计内部存储各类型占用。
     * progress: (当前正在扫描的路径, 已统计字节) -> Unit
     */
    fun categorize(onProgress: (String) -> Unit): Map<String, Long> {
        val result = mutableMapOf(
            "image" to 0L, "video" to 0L, "audio" to 0L, "doc" to 0L, "apk" to 0L, "other" to 0L
        )
        val rootFile = root
        if (!rootFile.exists()) return result
        var count = 0
        fun walk(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (Thread.currentThread().isInterrupted) return
                if (f.isDirectory) {
                    walk(f)
                } else {
                    val size = f.length()
                    if (size > 0) {
                        val key = categoryOf(f.name)
                        result[key] = result.getOrDefault(key, 0) + size
                    }
                }
                if (++count % 200 == 0) {
                    onProgress(dir.absolutePath)
                }
            }
        }
        walk(rootFile)
        return result
    }

    /** 获取指定类别下的文件列表（用于分类详情页） */
    fun filesOf(category: String, onProgress: (String) -> Unit): List<ScanItem> {
        val out = mutableListOf<ScanItem>()
        val rootFile = root
        if (!rootFile.exists()) return out
        fun walk(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (Thread.currentThread().isInterrupted) return
                if (f.isDirectory) {
                    walk(f)
                } else {
                    val size = f.length()
                    if (size > 0 && categoryOf(f.name) == category) {
                        out.add(ScanItem(f.absolutePath, f.name, size, kind = "other", extra = SizeUtils.format(size)))
                    }
                }
            }
        }
        walk(rootFile)
        return out
    }
}
