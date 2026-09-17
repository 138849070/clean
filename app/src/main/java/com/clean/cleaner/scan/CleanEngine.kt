package com.clean.cleaner.scan

import com.clean.cleaner.util.SizeUtils
import java.io.File

/** 删除引擎：删除单个文件或整目录，返回释放字节数（Android/data 受限目录走 Shizuku） */
object CleanEngine {

    /** 返回本次实际释放的字节数（受「不要清理」保护约束） */
    fun delete(path: String): Long {
        if (com.clean.cleaner.App.isProtected(path)) return 0
        val f = File(path)
        // 1) 普通 File 删除
        val size = if (f.isDirectory) Walker.dirSize(f) else f.length()
        if (f.exists()) {
            val ok = try {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            } catch (e: Exception) {
                false
            }
            if (ok) return size
        }
        // 2) File 删不掉（Android/data 等受限目录）→ Shizuku 删除
        if (ShizukuShell.available()) {
            val realSize = ShizukuShell.exec("du -sk \"$path\" 2>/dev/null | awk '{print \$1*1024}'")
                ?.trim()?.toLongOrNull() ?: size
            val out = ShizukuShell.exec("rm -rf \"$path\" 2>/dev/null; echo OK")
            return if (out?.contains("OK") == true) realSize else 0
        }
        return 0
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
