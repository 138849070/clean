package com.clean.cleaner.scan

import android.graphics.BitmapFactory
import android.os.Environment
import com.clean.cleaner.util.SizeUtils
import java.io.File

/** 相似图片扫描：同尺寸 + 大小接近 的图片归为一组（只读图片头，不读像素） */
class SimilarImageScanner(
    private val minSize: Long = 50L * 1024,
    private val onProgress: (String) -> Unit
) {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic")

    fun scan(): List<ScanItem> {
        val groups = HashMap<String, MutableList<File>>()
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists()) return emptyList()
        Walker.walk(root, onFile = { f ->
            if (!f.isDirectory && f.length() >= minSize) {
                val ext = f.extension.lowercase()
                if (ext in imageExt) {
                    val dims = readDims(f)
                    if (dims != null) {
                        val (w, h) = dims
                        if (w > 0 && h > 0) {
                            // 尺寸完全一致 + 大小落在同一 2MB 桶内，视为相似
                            val bucket = f.length() / (2L * 1024 * 1024)
                            val key = "$ext|$w|$h|$bucket"
                            groups.getOrPut(key) { ArrayList() }.add(f)
                        }
                    }
                }
            }
            onProgress(f.absolutePath)
            true
        })
        val out = ArrayList<ScanItem>()
        var groupId = 0
        for ((key, list) in groups) {
            if (Thread.currentThread().isInterrupted) break
            if (list.size > 1) {
                val size = list[0].length()
                for (f in list) {
                    out.add(
                        ScanItem(f.absolutePath, f.name, size,
                            groupKey = "sim$groupId",
                            groupLabel = "相似图片 · ${list.size} 张 · ${SizeUtils.format(size * list.size)}",
                            kind = "img",
                            extra = SizeUtils.format(size))
                    )
                }
                groupId++
            }
        }
        return out
    }

    private fun readDims(f: File): Pair<Int, Int>? {
        return try {
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        } catch (e: Exception) {
            null
        }
    }
}
