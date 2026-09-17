package com.clean.cleaner.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.clean.cleaner.util.Settings

/**
 * SAF（Storage Access Framework）方式访问 Android/data。
 * 与 MT管理器同款：用户通过系统文件选择器授权一次后即可读取，无需 Shizuku/root。
 */
object SafScanner {
    data class Stats(val fileCount: Long, val totalSize: Long, val cacheSize: Long)

    fun scan(context: Context): Stats? {
        val uriStr = Settings.safTreeUri(context) ?: return null
        val uri = try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            return null
        }
        val root = try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            null
        } ?: return null

        var files = 0L
        var size = 0L
        var cache = 0L

        fun walk(d: DocumentFile, inCache: Boolean) {
            if (Thread.currentThread().isInterrupted) return
            val list = try {
                d.listFiles()
            } catch (e: Exception) {
                return
            }
            for (f in list) {
                if (Thread.currentThread().isInterrupted) return
                val fName = f.name?.lowercase() ?: continue
                if (f.isDirectory) {
                    walk(f, inCache || fName == "cache" || fName == "code_cache")
                } else {
                    files++
                    val len = try {
                        f.length()
                    } catch (e: Exception) {
                        0L
                    }
                    size += len
                    if (inCache) cache += len
                }
            }
        }

        try {
            walk(root, false)
        } catch (ignored: Exception) {
        }
        // 授权了但读不到任何内容（例如 DocumentsUI 屏蔽），视为无效授权
        if (files == 0L && size == 0L) return null
        return Stats(files, size, cache)
    }
}
