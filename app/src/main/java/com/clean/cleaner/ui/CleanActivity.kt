package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
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
import com.clean.cleaner.scan.JunkScanner
import com.clean.cleaner.scan.ScanItem
import com.clean.cleaner.util.SizeUtils
import com.clean.cleaner.util.StatusBarUtil
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("SetTextI18n")
class CleanActivity : AppCompatActivity() {

    private lateinit var resultList: RecyclerView
    private lateinit var scanOverlay: View
    private lateinit var radarView: RadarView
    private lateinit var tvScanState: TextView
    private lateinit var tvScanFound: TextView
    private lateinit var tvScanPath: TextView
    private lateinit var bottomBar: View
    private lateinit var checkAll: CheckBox
    private lateinit var tvSelected: TextView
    private lateinit var btnClean: Button

    private val items = mutableListOf<ScanItem>()
    private lateinit var adapter: ScanResultAdapter
    private var scanThread: Thread? = null
    private val foundBytes = AtomicLong(0)
    private val handler = Handler(Looper.getMainLooper())
    private var lastPathUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_clean)

        if (com.clean.cleaner.util.Settings.keepScreen(this)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        resultList = findViewById(R.id.resultList)
        scanOverlay = findViewById(R.id.scanOverlay)
        radarView = findViewById(R.id.radarView)
        tvScanState = findViewById(R.id.tvScanState)
        tvScanFound = findViewById(R.id.tvScanFound)
        tvScanPath = findViewById(R.id.tvScanPath)
        bottomBar = findViewById(R.id.bottomBar)
        checkAll = findViewById(R.id.checkAll)
        tvSelected = findViewById(R.id.tvSelected)
        btnClean = findViewById(R.id.btnClean)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adapter = ScanResultAdapter(items) { count, size ->
            tvSelected.text = "已选 $count 项 · ${SizeUtils.format(size)}"
            btnClean.isEnabled = count > 0
        }
        resultList.layoutManager = LinearLayoutManager(this)
        resultList.adapter = adapter

        checkAll.setOnClickListener {
            adapter.setAllSelected(checkAll.isChecked)
        }

        btnClean.setOnClickListener {
            confirmClean()
        }

        startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanThread?.interrupt()
    }

    private fun startScan() {
        if (!PermissionActivity.hasStorageAccess()) {
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
            return
        }
        foundBytes.set(0)
        scanOverlay.visibility = View.VISIBLE
        resultList.visibility = View.GONE
        bottomBar.visibility = View.GONE
        tvScanState.text = "正在扫描…"
        tvScanFound.text = "已发现 0 B"
        radarView.start()

        scanThread = Thread {
            val scanner = JunkScanner(
                onProgress = { path ->
                    val now = System.currentTimeMillis()
                    if (now - lastPathUpdate > 120) {
                        lastPathUpdate = now
                        handler.post { tvScanPath.text = path }
                    }
                },
                onFound = { size ->
                    val total = foundBytes.addAndGet(size)
                    handler.post {
                        tvScanFound.text = "已发现 ${SizeUtils.format(total)}"
                    }
                }
            )
            scanner.scan()
            val all = ArrayList<ScanItem>()
            all.addAll(scanner.items)
            all.addAll(scanner.apkItems)
            all.addAll(scanner.emptyDirs)
            handler.post {
                radarView.stop()
                onScanDone(all)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun onScanDone(all: List<ScanItem>) {
        if (all.isEmpty()) {
            tvScanState.text = "扫描完成"
            tvScanFound.text = "未发现可清理的垃圾 ✨"
            radarView.stop()
            // 3 秒后返回
            handler.postDelayed({ finish() }, 2000)
            return
        }
        scanOverlay.visibility = View.GONE
        resultList.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        adapter.updateItems(all)
    }

    private fun confirmClean() {
        val sel = adapter.getSelectedItems()
        if (sel.isEmpty()) return
        val total = sel.sumOf { it.size }
        AlertDialog.Builder(this)
            .setTitle("确认清理")
            .setMessage("将删除选中的 ${sel.size} 项内容，共 ${SizeUtils.format(total)}。\n缓存类内容删除后应用会自动重新生成，不影响正常使用。")
            .setPositiveButton("清理") { _, _ -> doClean(sel) }
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
                Toast.makeText(this, "已清理 $count 项 · 释放 ${SizeUtils.format(freed)}", Toast.LENGTH_SHORT).show()
                adapter.removePaths(paths.toHashSet())
                if (adapter.itemCount == 0) {
                    tvScanState.text = "清理完成"
                    tvScanFound.text = "已释放 ${SizeUtils.format(freed)} ✨"
                    scanOverlay.visibility = View.VISIBLE
                    resultList.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                    handler.postDelayed({ finish() }, 1500)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
