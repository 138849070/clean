package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.scan.MainScan
import com.clean.cleaner.scan.MainScanResult
import com.clean.cleaner.scan.StorageHelper
import com.clean.cleaner.util.SizeUtils
import com.clean.cleaner.util.StatusBarUtil

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var headerCard: View
    private lateinit var tvPercent: TextView
    private lateinit var tvSpace: TextView
    private lateinit var tvTotalCleaned: TextView
    private lateinit var tvSessionCleaned: TextView
    private lateinit var tvHint: TextView
    private lateinit var btnScan: TextView
    private lateinit var tvStatTotal: TextView
    private lateinit var tvStatFolders: TextView
    private lateinit var tvStatFiles: TextView
    private lateinit var grid: LinearLayout

    private var hasCheckedPermission = false
    private var scanThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    /** type -> 该入口的统计标签 TextView */
    private val tagViews = HashMap<String, TextView>()

    private data class GridItem(val name: String, val emoji: String, val type: String, val tag: String? = null)

    private val gridItems = listOf(
        GridItem("缓存垃圾", "🧹", "junk", "待扫描"),
        GridItem("微信清理", "💬", "deep"),
        GridItem("大文件", "🗜️", "large", "待扫描"),
        GridItem("重复文件", "📑", "dup", "待扫描"),
        GridItem("相似图片", "🖼️", "sim"),
        GridItem("安装包", "📦", "apk", "待扫描"),
        GridItem("卸载残留", "🗑️", "residue"),
        GridItem("空文件夹", "📁", "empty", "待扫描"),
        GridItem("最新文件", "🕒", "recent"),
        GridItem("最旧文件", "⏳", "oldest"),
        GridItem("应用管理", "📱", "apps"),
        GridItem("文件管理", "🗂️", "files")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏透明、白色图标（顶部为蓝色背景）
        StatusBarUtil.transparent(this, lightIcons = false)
        setContentView(R.layout.activity_main)

        headerCard = findViewById(R.id.headerCard)
        tvPercent = findViewById(R.id.tvPercent)
        tvSpace = findViewById(R.id.tvSpace)
        tvTotalCleaned = findViewById(R.id.tvTotalCleaned)
        tvSessionCleaned = findViewById(R.id.tvSessionCleaned)
        tvHint = findViewById(R.id.tvHint)
        btnScan = findViewById(R.id.btnScan)
        tvStatTotal = findViewById(R.id.tvStatTotal)
        tvStatFolders = findViewById(R.id.tvStatFolders)
        tvStatFiles = findViewById(R.id.tvStatFiles)
        grid = findViewById(R.id.grid)

        // 顶部蓝卡延伸到状态栏后面：动态加状态栏高度 padding
        val sb = StatusBarUtil.statusBarHeight(this)
        if (sb > 0) {
            headerCard.setPadding(
                headerCard.paddingLeft,
                headerCard.paddingTop + sb,
                headerCard.paddingRight,
                headerCard.paddingBottom
            )
        }

        btnScan.setOnClickListener { startScan() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showAbout() }

        buildGrid()
        refreshHeader()
        showCrashLogIfAny()

        // 恢复上次扫描结果（若在本次会话内）
        App.lastScan?.let { showResult(it) }
    }

    override fun onResume() {
        super.onResume()
        if (!hasCheckedPermission) {
            hasCheckedPermission = true
            if (!PermissionActivity.hasStorageAccess()) {
                startActivityForResult(Intent(this, PermissionActivity::class.java), 1001)
            }
        }
        refreshHeader()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanThread?.interrupt()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            refreshHeader()
        }
    }

    private fun refreshHeader() {
        val total = StorageHelper.totalSpace()
        val free = StorageHelper.freeSpace()
        val percent = SizeUtils.formatPercent(free, total)
        tvPercent.text = "$percent%"
        tvSpace.text = "手机存储 ${SizeUtils.format(free)} / ${SizeUtils.format(total)}"
        tvTotalCleaned.text = "累计清理：${SizeUtils.format(App.totalCleaned())}"
        tvSessionCleaned.text = "本次清理：${SizeUtils.format(App.sessionCleaned)}"
    }

    private fun buildGrid() {
        grid.removeAllViews()
        tagViews.clear()
        val inflater = LayoutInflater.from(this)
        gridItems.chunked(3).forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (item in row) {
                val cell = inflater.inflate(R.layout.item_grid, rowLayout, false)
                cell.findViewById<TextView>(R.id.tvIcon).text = item.emoji
                cell.findViewById<TextView>(R.id.tvName).text = item.name
                val tag = cell.findViewById<TextView>(R.id.tvTag)
                if (item.tag != null) {
                    tag.text = item.tag
                    tag.visibility = View.VISIBLE
                }
                tagViews[item.type] = tag
                cell.setOnClickListener { openFeature(item.type) }
                rowLayout.addView(cell)
            }
            // 补齐空位，保持每行 3 个占位
            val missing = 3 - row.size
            for (i in 0 until missing) {
                rowLayout.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            grid.addView(rowLayout)
        }
    }

    private fun openFeature(type: String) {
        val intent = when (type) {
            "junk" -> Intent(this, CleanActivity::class.java)
            "apps" -> Intent(this, AppManagerActivity::class.java)
            "files" -> Intent(this, FileBrowserActivity::class.java)
            else -> Intent(this, ScanResultActivity::class.java).putExtra("type", type)
        }
        startActivity(intent)
    }

    /** 开始一键扫描：统计主界面所有入口的规模 */
    private fun startScan() {
        if (scanThread?.isAlive == true) return
        if (!PermissionActivity.hasStorageAccess()) {
            startActivity(Intent(this, PermissionActivity::class.java))
            return
        }
        btnScan.isEnabled = false
        btnScan.text = "扫描中…"
        tvHint.text = "正在扫描…"

        val installed = try {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        scanThread = Thread {
            val result = try {
                MainScan(installed) {}.scan()
            } catch (e: Exception) {
                null
            }
            handler.post {
                if (result != null) {
                    App.lastScan = result
                    showResult(result)
                } else {
                    btnScan.isEnabled = true
                    btnScan.text = "重新扫描"
                    tvHint.text = "扫描失败，请重试"
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /** 展示扫描结果：顶部提示、统计行、各入口标签 */
    private fun showResult(r: MainScanResult) {
        tvHint.text = "扫描完成！"
        btnScan.isEnabled = true
        btnScan.text = "重新扫描"

        tvStatTotal.text = SizeUtils.compact(r.totalSize)
        tvStatFolders.text = "${r.folderCount}个"
        tvStatFiles.text = "${r.fileCount}个"

        tag(r, "junk")?.text = SizeUtils.compact(r.junkSize)
        tag(r, "deep")?.text = SizeUtils.compact(r.deepSize)
        tag(r, "large")?.text = "${r.largeCount}个(${SizeUtils.compact(r.largeSize)})"
        tag(r, "dup")?.text = "${r.dupCount}个(${SizeUtils.compact(r.dupSize)})"
        tag(r, "sim")?.text = "${r.simCount}个(${SizeUtils.compact(r.simSize)})"
        tag(r, "apk")?.text = "${r.apkCount}个(${SizeUtils.compact(r.apkSize)})"
        tag(r, "residue")?.text = "${r.residueCount}个(${SizeUtils.compact(r.residueSize)})"
        tag(r, "empty")?.text = "${r.emptyCount}个"
        // 最新/最旧/应用管理/文件管理：无统计，保持隐藏
        listOf("recent", "oldest", "apps", "files").forEach {
            tagViews[it]?.visibility = View.GONE
        }
    }

    private fun tag(r: MainScanResult, type: String): TextView? {
        val tv = tagViews[type] ?: return null
        tv.visibility = View.VISIBLE
        return tv
    }

    /** 若上次闪退留有日志，弹窗展示，便于定位问题 */
    private fun showCrashLogIfAny() {
        try {
            val file = App.crashLogFile()
            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                AlertDialog.Builder(this)
                    .setTitle("上次运行出现异常")
                    .setMessage("崩溃信息已保存到 Download/clean_crash.txt，可反馈此信息：\n\n${content.take(1500)}")
                    .setPositiveButton("知道了", null)
                    .setNegativeButton("清除记录") { _, _ -> file.delete() }
                    .show()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Clean 清理")
            .setMessage("版本 1.2.0\n\n免费安卓存储清理工具，无会员、无广告、无网络请求。\n\n清理功能均在本机完成，不会上传任何数据。")
            .setPositiveButton("好的", null)
            .show()
    }
}
