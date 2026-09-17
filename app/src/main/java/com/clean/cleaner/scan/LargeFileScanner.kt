package com.clean.cleaner.scan

import android.os.Environment
import com.clean.cleaner.util.SizeUtils
import java.io.File

/** 大文件扫描：> minSize 的文件，按大小降序 */
class LargeFileScanner(
    private val minSize: Long = 20L * 1024 * 1024,
    private val maxCount: Int = 500,
    private val onProgress: (String) -> Unit
) {
    private val files = ArrayList<ScanItem>()

    fun scan(): List<ScanItem> {
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists()) return files
        Walker.walk(root, onFile = { f ->
            if (!f.isDirectory) {
                val size = f.length()
                if (size >= minSize) {
                    val cat = StorageHelper.categoryOf(f.name)
                    files.add(
                        ScanItem(f.absolutePath, f.name, size,
                            kind = if (cat == "other") "large" else cat,
                            groupKey = "large",
                            groupLabel = "大文件",
                            extra = SizeUtils.format(size))
                    )
                }
            }
            onProgress(f.absolutePath)
            true
        })
        files.sortByDescending { it.size }
        return if (files.size > maxCount) files.subList(0, maxCount) else files
    }
}
