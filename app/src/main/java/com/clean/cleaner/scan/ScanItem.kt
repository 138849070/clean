package com.clean.cleaner.scan

/** 单个扫描结果项 */
data class ScanItem(
    val path: String,
    val name: String,
    val size: Long,
    val isDir: Boolean = false,
    /** 分组 key，用于结果页分组 */
    val groupKey: String = "",
    /** 分组显示标题，如「缓存垃圾」「大文件」 */
    val groupLabel: String = "",
    /** 图标类别：img/video/audio/doc/apk/cache/log/tmp/thumb/residue/empty/other */
    val kind: String = "other",
    /** 附加说明（如应用名） */
    val extra: String = ""
)
