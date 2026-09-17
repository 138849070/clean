package com.clean.cleaner.scan

import android.os.Environment
import com.clean.cleaner.util.SizeUtils
import java.io.File

/**
 * 深度清理：微信 / QQ 等应用的缓存与聊天大文件。
 * 只列出明确的缓存目录与大文件，不触碰应用私有数据库。
 */
class DeepCleanScanner(private val onProgress: (String) -> Unit) {

    data class Target(val packageName: String, val label: String, val dirs: List<String>)

    // dir 为相对外部存储根目录的路径；可匹配 data 目录下与 tencent 目录
    private val targets = listOf(
        Target("com.tencent.mm", "微信", listOf(
            "Android/data/com.tencent.mm/cache",
            "Android/data/com.tencent.mm/MicroMsg",
            "tencent/MicroMsg"
        )),
        Target("com.tencent.mobileqq", "QQ", listOf(
            "Android/data/com.tencent.mobileqq/cache",
            "Android/data/com.tencent.mobileqq/tencent/QQfile_recv",
            "tencent/QQfile_recv",
            "tencent/QQ_Images"
        ))
    )

    fun scan(): List<ScanItem> {
        val out = ArrayList<ScanItem>()
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists()) return out

        for (t in targets) {
            if (Thread.currentThread().isInterrupted) break
            for (rel in t.dirs) {
                val dir = File(root, rel)
                if (!dir.exists()) continue
                // 缓存目录：整目录清理
                if (dir.name.equals("cache", ignoreCase = true)) {
                    val size = Walker.dirSize(dir)
                    if (size > 0) {
                        out.add(
                            ScanItem(dir.absolutePath, "${t.label}缓存", size, isDir = true,
                                groupKey = "deep.${t.packageName}.cache",
                                groupLabel = "${t.label}缓存",
                                kind = "cache",
                                extra = SizeUtils.format(size))
                        )
                    }
                } else {
                    // 聊天文件目录：列出其中的大文件（>3MB）
                    walkBigFiles(dir, t.label, out)
                }
                onProgress(dir.absolutePath)
            }
        }
        return out
    }

    private fun walkBigFiles(dir: File, appLabel: String, out: MutableList<ScanItem>) {
        Walker.walk(dir, onFile = { f ->
            val size = f.length()
            if (size >= 3L * 1024 * 1024) {
                val kind = when (f.extension.lowercase()) {
                    in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic") -> "img"
                    in setOf("mp4", "mkv", "mov", "avi", "3gp", "flv", "webm") -> "video"
                    in setOf("mp3", "wav", "aac", "amr", "m4a", "ogg") -> "audio"
                    else -> "other"
                }
                out.add(
                    ScanItem(f.absolutePath, f.name, size,
                        groupKey = "deep.$appLabel.$kind",
                        groupLabel = "${appLabel}聊天${kindLabel(kind)}",
                        kind = kind,
                        extra = SizeUtils.format(size))
                )
            }
            true
        })
    }

    private fun kindLabel(kind: String): String = when (kind) {
        "img" -> "图片"
        "video" -> "视频"
        "audio" -> "语音"
        else -> "文件"
    }
}
