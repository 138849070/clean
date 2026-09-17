package com.clean.cleaner.scan

import android.os.Environment
import java.io.File

/** 通用目录遍历工具，带回调与取消 */
object Walker {
    fun walk(root: File, onFile: (File) -> Boolean, onDir: ((File) -> Boolean)? = null) {
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            if (Thread.currentThread().isInterrupted) return
            val dir = stack.removeLast()
            val list = dir.listFiles() ?: continue
            for (f in list) {
                if (Thread.currentThread().isInterrupted) return
                if (f.isDirectory) {
                    if (onDir?.invoke(f) != false) stack.addLast(f)
                } else {
                    if (!onFile(f)) return
                }
            }
        }
    }

    /** 计算目录累计大小 */
    fun dirSize(dir: File): Long {
        var total = 0L
        walk(dir, onFile = { total += it.length(); true })
        return total
    }

    /** 外部存储根目录 */
    val root: File = Environment.getExternalStorageDirectory()
}
