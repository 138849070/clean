package com.clean.cleaner.scan

import android.os.Environment
import com.clean.cleaner.util.SizeUtils
import java.io.File

/** 重复文件扫描：按 大小+文件名 分组（快速模式，不读内容） */
class DuplicateScanner(
    private val minSize: Long = 100L * 1024,
    private val onProgress: (String) -> Unit
) {
    fun scan(): List<ScanItem> {
        val groups = HashMap<String, MutableList<File>>()
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists()) return emptyList()
        Walker.walk(root, onFile = { f ->
            if (!f.isDirectory) {
                val size = f.length()
                if (size >= minSize) {
                    val key = "$size|${f.name.lowercase()}"
                    groups.getOrPut(key) { ArrayList() }.add(f)
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
                            groupKey = "dup$groupId",
                            groupLabel = "重复文件 · ${list.size} 份 · ${SizeUtils.format(size)}",
                            kind = "other",
                            extra = SizeUtils.format(size))
                    )
                }
                groupId++
            }
        }
        return out
    }
}
