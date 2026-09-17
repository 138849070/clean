package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.scan.CleanEngine
import com.clean.cleaner.scan.DeepCleanScanner
import com.clean.cleaner.scan.DuplicateScanner
import com.clean.cleaner.scan.LargeFileScanner
import com.clean.cleaner.scan.ResidueScanner
import com.clean.cleaner.scan.ScanItem
import com.clean.cleaner.scan.SimilarImageScanner
import com.clean.cleaner.scan.StorageHelper
import com.clean.cleaner.scan.Walker
import com.clean.cleaner.util.SizeUtils
import com.clean.cleaner.util.StatusBarUtil

@SuppressLint("SetTextI18n")
class ScanResultActivity : AppCompatActivity() {

    private lateinit var resultList: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var tvLoading: TextView
    private lateinit var bottomBar: View
    private lateinit var checkAll: CheckBox
    private lateinit var tvSelected: TextView
    private lateinit var btnClean: Button

    private val items = mutableListOf<ScanItem>()
    private lateinit var adapter: ScanResultAdapter
    private var scanThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastPathUpdate = 0L

    private var type: String = "large"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_scan_result)

        type = intent.getStringExtra("type") ?: "large"

        resultList = findViewById(R.id.resultList)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvLoading = findViewById(R.id.tvLoading)
        bottomBar = findViewById(R.id.bottomBar)
        checkAll = findViewById(R.id.checkAll)
        tvSelected = findViewById(R.id.tvSelected)
        btnClean = findViewById(R.id.btnClean)

        findViewById<TextView>(R.id.tvTitle).text = titleOf(type)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adapter = ScanResultAdapter(items) { count, size ->
            tvSelected.text = "已选 $count 项 · ${SizeUtils.format(size)}"
            btnClean.isEnabled = count > 0
        }
        resultList.layoutManager = LinearLayoutManager(this)
        resultList.adapter = adapter

        checkAll.setOnClickListener { adapter.setAllSelected(checkAll.isChecked) }
        btnClean.setOnClickListener { confirmClean() }

        startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanThread?.interrupt()
    }

    private fun titleOf(t: String): String = when (t) {
        "large" -> "大文件"
        "dup" -> "重复文件"
        "sim" -> "相似图片"
        "apk" -> "安装包清理"
        "residue" -> "卸载残留"
        "empty" -> "空文件夹"
        "deep" -> "微信/QQ 深度清理"
        "recent" -> "最新文件"
        "oldest" -> "最旧文件"
        else -> "扫描结果"
    }

    private fun startScan() {
        if (!PermissionActivity.hasStorageAccess()) {
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
            return
        }
        loadingOverlay.visibility = View.VISIBLE
        resultList.visibility = View.GONE
        bottomBar.visibility = View.GONE
        tvLoading.text = "正在扫描…"

        scanThread = Thread {
            val result: List<ScanItem> = try {
                when (type) {
                    "large" -> LargeFileScanner(onProgress = ::throttlePath).scan()
                    "dup" -> DuplicateScanner(onProgress = ::throttlePath).scan()
                    "sim" -> SimilarImageScanner(onProgress = ::throttlePath).scan()
                    "residue" -> ResidueScanner(installedPackages(), ::throttlePath).scan()
                    "deep" -> DeepCleanScanner(::throttlePath).scan()
                    "apk" -> scanApks()
                    "empty" -> scanEmptyDirs()
                    "recent" -> scanByTime(true)
                    "oldest" -> scanByTime(false)
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
            handler.post {
                onScanDone(result)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun throttlePath(path: String) {
        val now = System.currentTimeMillis()
        if (now - lastPathUpdate > 150) {
            lastPathUpdate = now
            handler.post { tvLoading.text = "正在扫描…\n$path" }
        }
    }

    private fun installedPackages(): Set<String> {
        val pm = packageManager
        return try {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun scanApks(): List<ScanItem> {
        val out = ArrayList<ScanItem>()
        Walker.walk(Walker.root, onFile = { f ->
            if (!f.isDirectory && f.extension.equals("apk", true) && f.length() > 100 * 1024) {
                out.add(
                    ScanItem(f.absolutePath, f.name, f.length(),
                        kind = "apk", groupKey = "apk", groupLabel = "安装包",
                        extra = SizeUtils.format(f.length()))
                )
            }
            true
        })
        out.sortByDescending { it.size }
        return out
    }

    private fun scanEmptyDirs(): List<ScanItem> {
        val out = ArrayList<ScanItem>()
        Walker.walk(Walker.root, onDir = { dir ->
            val children = dir.listFiles()
            if (children == null || children.isEmpty()) {
                out.add(
                    ScanItem(dir.absolutePath, dir.name, 0, isDir = true,
                        groupKey = "empty", groupLabel = "空文件夹", kind = "empty", extra = "0 B")
                )
                false
            } else true
        }, onFile = { true })
        return out
    }

    private val timeFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

    /** 按修改时间列出最新/最旧的文件 */
    private fun scanByTime(recent: Boolean): List<ScanItem> {
        val files = ArrayList<java.io.File>()
        Walker.walk(Walker.root, onFile = { f ->
            if (!f.isDirectory && f.length() > 100 * 1024) {
                files.add(f)
            }
            true
        })
        if (recent) files.sortByDescending { it.lastModified() }
        else files.sortBy { it.lastModified() }
        return files.take(100).map { f ->
            ScanItem(
                f.absolutePath, f.name, f.length(),
                groupKey = "time",
                groupLabel = if (recent) "最新文件" else "最旧文件",
                kind = StorageHelper.categoryOf(f.name).let { if (it == "other") "doc" else it },
                extra = "修改于 ${timeFormat.format(java.util.Date(f.lastModified()))}"
            )
        }
    }

    private fun onScanDone(result: List<ScanItem>) {
        if (result.isEmpty()) {
            loadingOverlay.visibility = View.VISIBLE
            tvLoading.text = "扫描完成\n未发现相关内容 ✨"
            resultList.visibility = View.GONE
            return
        }
        loadingOverlay.visibility = View.GONE
        resultList.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        adapter.updateItems(result)
    }

    private fun confirmClean() {
        val sel = adapter.getSelectedItems()
        if (sel.isEmpty()) return
        val total = sel.sumOf { it.size }
        val tip = if (type == "empty" || type == "residue") {
            "将删除选中的 ${sel.size} 个目录，共 ${SizeUtils.format(total)}。"
        } else {
            "将删除选中的 ${sel.size} 项，共 ${SizeUtils.format(total)}。"
        }
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage(tip)
            .setPositiveButton("删除") { _, _ -> doClean(sel) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doClean(sel: List<ScanItem>) {
        btnClean.isEnabled = false
        Thread {
            val paths = sel.map { it.path }
            val (count, freed) = CleanEngine.deleteAll(paths)
            App.addCleaned(freed)
            handler.post {
                btnClean.isEnabled = true
                Toast.makeText(this, "已删除 $count 项 · 释放 ${SizeUtils.format(freed)}", Toast.LENGTH_SHORT).show()
                adapter.removePaths(paths.toHashSet())
                if (adapter.itemCount == 0) {
                    loadingOverlay.visibility = View.VISIBLE
                    tvLoading.text = "清理完成\n已释放 ${SizeUtils.format(freed)} ✨"
                    resultList.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
