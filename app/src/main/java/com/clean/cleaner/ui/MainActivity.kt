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
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.scan.MainScan
import com.clean.cleaner.scan.MainScanResult
import com.clean.cleaner.scan.ShizukuShell
import com.clean.cleaner.scan.StorageHelper
import com.clean.cleaner.util.SizeUtils
import com.clean.cleaner.util.Settings
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
    private var hasShownPeriodic = false
    private var scanThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    /** type -> 该入口的统计标签 TextView */
    private val tagViews = HashMap<String, TextView>()

    private data class GridItem(val name: String, val emoji: String, val type: String, val tag: String? = null)

    private val gridItems = listOf(
        GridItem("备注文件", "📝", "note"),
        GridItem("定期清理", "🔄", "periodic"),
        GridItem("工具箱", "🧰", "toolbox"),
        GridItem("最新文件", "🕒", "recent"),
        GridItem("最旧文件", "⏳", "oldest"),
        GridItem("重复文件", "📑", "dup"),
        GridItem("空文件夹", "📁", "empty", "待扫描"),
        GridItem("缓存垃圾", "🧹", "junk", "待扫描"),
        GridItem("疑似缓存", "🗑️", "residue", "待扫描"),
        GridItem("安装包", "📦", "apk", "待扫描"),
        GridItem("大文件", "🗜️", "large", "待扫描"),
        GridItem("五星好评", "⭐", "rate")
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
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMoreMenu(it) }

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
            if (!PermissionActivity.hasStorageAccess() || !PermissionActivity.hasMediaAccess()) {
                startActivityForResult(Intent(this, PermissionActivity::class.java), 1001)
            }
        }
        refreshHeader()
        // 自动扫描：设置开启且本会话未扫描过
        if (App.lastScan == null && Settings.autoScan(this) && PermissionActivity.hasStorageAccess()) {
            startScan()
        }
        checkPeriodicClean()
        ensureDataAccess()
        ensureShizuku()
    }

    private var hasPromptedDataAccess = false
    private var hasPromptedShizuku = false

    /** 主动请求 Shizuku 授权：服务可用但未授权时弹出授权框 */
    private fun ensureShizuku() {
        if (hasPromptedShizuku) return
        if (!Settings.shizukuAccess(this)) return
        hasPromptedShizuku = true
        try {
            if (!ShizukuShell.serviceRunning()) {
                AlertDialog.Builder(this)
                    .setTitle("启用 Shizuku 可扫全 Android/data")
                    .setMessage("要完整扫描 Android/data（各应用的数据与缓存），需要 Shizuku。\n\n方法：安装 Shizuku 应用 → 在 Shizuku 中通过「无线调试」激活 → 回到本应用重新授权。")
                    .setPositiveButton("去了解") { _, _ ->
                        ShizukuShell.requestPermission(9091)
                    }
                    .setNegativeButton("暂不使用", null)
                    .show()
            } else if (!ShizukuShell.permissionGranted()) {
                ShizukuShell.requestPermission(9091)
            }
        } catch (ignored: Exception) {
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9091) {
            if (ShizukuShell.permissionGranted()) {
                android.widget.Toast.makeText(this, "Shizuku 已授权，重新扫描即可扫全 Android/data", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 首次提醒授权 Android/data（MT管理器同款方式，扫全需要） */
    private fun ensureDataAccess() {
        if (hasPromptedDataAccess) return
        if (Settings.safTreeUri(this) != null) return
        if (!PermissionActivity.hasStorageAccess() || !PermissionActivity.hasMediaAccess()) return
        hasPromptedDataAccess = true
        AlertDialog.Builder(this)
            .setTitle("授权访问 Android/data")
            .setMessage("要扫全 Android/data 目录（各应用的数据和缓存），需要一次系统文件夹授权（MT管理器同款方式，无需安装任何东西）。\n\n是否现在去设置页授权？")
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("跳过", null)
            .show()
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
        val showCleaned = Settings.showCleaned(this)
        tvTotalCleaned.visibility = if (showCleaned) View.VISIBLE else View.GONE
        tvSessionCleaned.visibility = if (showCleaned) View.VISIBLE else View.GONE
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
            "note" -> Intent(this, NoteActivity::class.java)
            "periodic" -> Intent(this, SettingsActivity::class.java)
            "toolbox" -> Intent(this, ToolboxActivity::class.java)
            "junk" -> Intent(this, CleanActivity::class.java)
            "apps" -> Intent(this, AppManagerActivity::class.java)
            "files" -> Intent(this, FileBrowserActivity::class.java)
            "search" -> Intent(this, SearchActivity::class.java)
            "rate" -> null
            else -> Intent(this, ScanResultActivity::class.java).putExtra("type", type)
        }
        if (intent != null) startActivity(intent)
        else if (type == "rate") showRate()
    }

    private fun showMoreMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("相似图片").setOnMenuItemClickListener {
            openFeature("sim"); true
        }
        menu.menu.add("空白文件").setOnMenuItemClickListener {
            openFeature("emptyfile"); true
        }
        menu.menu.add("应用管理").setOnMenuItemClickListener {
            openFeature("apps"); true
        }
        menu.menu.add("文件管理").setOnMenuItemClickListener {
            openFeature("files"); true
        }
        menu.menu.add("秒搜文件").setOnMenuItemClickListener {
            openFeature("search"); true
        }
        menu.menu.add("关于").setOnMenuItemClickListener {
            showAbout(); true
        }
        menu.show()
    }

    private fun showRate() {
        AlertDialog.Builder(this)
            .setTitle("五星好评")
            .setMessage("如果 Clean 清理帮到了你，可以去 GitHub 点个 Star 支持一下。")
            .setPositiveButton("打开 GitHub") { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/138849070/clean")
                        )
                    )
                } catch (ignored: Exception) {
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 定期清理提醒：超过设置周期后提示 */
    private fun checkPeriodicClean() {
        if (hasShownPeriodic) return
        if (!Settings.periodicClean(this)) return
        val last = App.lastCleanTime()
        if (last <= 0) return
        val days = Settings.periodicDays(this).toLong()
        val elapsed = System.currentTimeMillis() - last
        if (elapsed >= days * 86400000L) {
            hasShownPeriodic = true
            val d = (elapsed / 86400000L).toInt()
            AlertDialog.Builder(this)
                .setTitle("定期清理提醒")
                .setMessage("距上次清理已超过 $d 天，是否现在清理？")
                .setPositiveButton("去清理") { _, _ ->
                    startActivity(Intent(this, CleanActivity::class.java))
                }
                .setNegativeButton("稍后", null)
                .show()
        }
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
                MainScan(
                    context = this@MainActivity,
                    installedPackages = installed,
                    excludedPaths = Settings.excludeList(this@MainActivity).toSet(),
                    scanEmptyFiles = Settings.emptyFiles(this@MainActivity)
                ) {}.scan()
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

        // 统计行：MediaStore 全量 + File 遍历 + Shizuku Android/data，三者合并
        val baseFiles = if (r.mediaFiles > r.fileCount) r.mediaFiles else r.fileCount.toLong()
        val fileTotal = baseFiles + r.dataFiles
        val baseSize = if (r.mediaSize > r.totalSize) r.mediaSize else r.totalSize
        val sizeTotal = baseSize + r.dataSize
        tvStatTotal.text = SizeUtils.compact(sizeTotal)
        tvStatFolders.text = "${r.folderCount}个"
        tvStatFiles.text = "${fileTotal}个"

        tag(r, "junk")?.text = "大于${SizeUtils.compact(r.junkSize + r.dataCache)}"
        tag(r, "empty")?.text = "${r.emptyCount}个"
        tag(r, "residue")?.text = "${r.residueCount}个(${SizeUtils.compact(r.residueSize)})"
        tag(r, "apk")?.text = "${r.apkCount}个(${SizeUtils.compact(r.apkSize)})"
        tag(r, "large")?.text = "${r.largeCount}个(${SizeUtils.compact(r.largeSize)})"
        // 无统计标签的入口保持隐藏
        listOf("note", "periodic", "toolbox", "recent", "oldest", "dup", "rate").forEach {
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
            .setMessage("版本 1.4.0\n\n免费安卓存储清理工具，无会员、无广告、无网络请求。\n\n清理功能均在本机完成，不会上传任何数据。")
            .setPositiveButton("好的", null)
            .show()
    }
}
