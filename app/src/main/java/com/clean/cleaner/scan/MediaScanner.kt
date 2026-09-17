package com.clean.cleaner.scan

import android.content.Context
import android.provider.MediaStore

/**
 * 基于 MediaStore 的全量文件统计。
 * 系统媒体库索引了所有图片/视频/音频/文档/下载文件，
 * 一条查询即可拿到全量数据，比 File 遍历更完整。
 */
object MediaScanner {
    data class Result(
        val fileCount: Long = 0,
        val totalSize: Long = 0,
        val imageCount: Long = 0,
        val videoCount: Long = 0,
        val audioCount: Long = 0,
        val docCount: Long = 0,
        val apkCount: Long = 0
    )

    fun scan(context: Context): Result {
        var fileCount = 0L
        var totalSize = 0L
        var imageCount = 0L
        var videoCount = 0L
        var audioCount = 0L
        var docCount = 0L
        var apkCount = 0L
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                while (c.moveToNext()) {                    fileCount++
                    totalSize += c.getLong(sizeIdx)
                    val mime = c.getString(mimeIdx) ?: continue
                    when {
                        mime.startsWith("image/") -> imageCount++
                        mime.startsWith("video/") -> videoCount++
                        mime.startsWith("audio/") -> audioCount++
                        mime == "application/vnd.android.package-archive" -> apkCount++
                        mime.startsWith("text/") ||
                            mime.contains("pdf") ||
                            mime.contains("msword") ||
                            mime.contains("spreadsheet") ||
                            mime.contains("presentation") ||
                            mime.contains("epub") -> docCount++
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return Result(fileCount, totalSize, imageCount, videoCount, audioCount, docCount, apkCount)
    }
}
