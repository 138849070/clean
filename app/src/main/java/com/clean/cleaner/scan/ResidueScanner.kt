package com.clean.cleaner.scan

import android.os.Environment
import com.clean.cleaner.util.SizeUtils
import java.io.File

/** 卸载残留扫描：Android/data、Android/obb 下不属于任何已安装应用的目录 */
class ResidueScanner(
    private val installedPackages: Set<String>,
    private val onProgress: (String) -> Unit
) {
    fun scan(): List<ScanItem> {
        val out = ArrayList<ScanItem>()
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists()) return out
        val dataDir = File(root, "Android/data")
        val obbDir = File(root, "Android/obb")
        scanBase(dataDir, out)
        scanBase(obbDir, out)
        return out
    }

    private fun scanBase(base: File, out: MutableList<ScanItem>) {
        if (!base.exists()) return
        val list = base.listFiles() ?: return
        for (f in list) {
            if (Thread.currentThread().isInterrupted) return
            onProgress(f.absolutePath)
            if (f.isDirectory) {
                if (f.name !in installedPackages) {
                    val size = Walker.dirSize(f)
                    if (size > 0 || f.listFiles()?.isNotEmpty() == true) {
                        out.add(
                            ScanItem(f.absolutePath, f.name, size, isDir = true,
                                groupKey = "residue", groupLabel = "卸载残留",
                                kind = "residue", extra = SizeUtils.format(size))
                        )
                    }
                }
            }
        }
    }
}
