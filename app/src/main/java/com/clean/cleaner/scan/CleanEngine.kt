package com.clean.cleaner.scan

import com.clean.cleaner.util.SizeUtils
import java.io.File

/** 删除引擎：删除单个文件或整目录，返回释放字节数 */
object CleanEngine {

    /** 返回本次实际释放的字节数（受「不要清理」保护约束） */
    fun delete(path: String): Long {
        if (com.clean.cleaner.App.isProtected(path)) return 0
        val f = File(path)
        if (!f.exists()) return 0
        val size = if (f.isDirectory) Walker.dirSize(f) else f.length()
        return try {
            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
            if (ok) size else 0
        } catch (e: Exception) {
            0
        }
    }

    /** 批量删除，返回 [成功删除数, 释放字节数] */
    fun deleteAll(paths: List<String>): Pair<Int, Long> {
        var count = 0
        var freed = 0L
        for (p in paths) {
            if (Thread.currentThread().isInterrupted) break
            val r = delete(p)
            if (r > 0) {
                count++
                freed += r
            }
        }
        return count to freed
    }

    /** 判断文件/目录是否存在 */
    fun exists(path: String): Boolean = File(path).exists()
}
