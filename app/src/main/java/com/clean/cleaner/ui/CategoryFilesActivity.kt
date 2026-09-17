package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
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
import com.clean.cleaner.scan.ScanItem
import com.clean.cleaner.scan.StorageHelper
import com.clean.cleaner.util.SizeUtils
import com.clean.cleaner.util.StatusBarUtil

@SuppressLint("SetTextI18n")
class CategoryFilesActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var fileList: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var bottomBar: View
    private lateinit var checkAll: CheckBox
    private lateinit var tvSelected: TextView
    private lateinit var btnDelete: Button

    private val items = mutableListOf<ScanItem>()
    private lateinit var adapter: ScanResultAdapter
    private var category: String = "image"
    private var scanThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_category_files)

        category = intent.getStringExtra("category") ?: "image"

        tvTitle = findViewById(R.id.tvTitle)
        fileList = findViewById(R.id.fileList)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        bottomBar = findViewById(R.id.bottomBar)
        checkAll = findViewById(R.id.checkAll)
        tvSelected = findViewById(R.id.tvSelected)
        btnDelete = findViewById(R.id.btnDelete)

        tvTitle.text = StorageHelper.categoryLabel(category)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adapter = ScanResultAdapter(items) { count, size ->
            tvSelected.text = "已选 $count 项 · ${SizeUtils.format(size)}"
            btnDelete.isEnabled = count > 0
        }
        fileList.layoutManager = LinearLayoutManager(this)
        fileList.adapter = adapter

        checkAll.setOnClickListener { adapter.setAllSelected(checkAll.isChecked) }
        btnDelete.setOnClickListener { confirmDelete() }

        scan()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanThread?.interrupt()
    }

    private fun scan() {
        loadingOverlay.visibility = View.VISIBLE
        bottomBar.visibility = View.GONE
        scanThread = Thread {
            val files = StorageHelper.filesOf(category) { }
                .sortedByDescending { it.size }
                .map {
                    it.copy(
                        groupKey = "cat",
                        groupLabel = StorageHelper.categoryLabel(category),
                        kind = category
                    )
                }
            handler.post {
                loadingOverlay.visibility = View.GONE
                if (files.isEmpty()) {
                    Toast.makeText(this, "未找到相关文件", Toast.LENGTH_SHORT).show()
                    finish()
                    return@post
                }
                adapter.updateItems(files)
                bottomBar.visibility = View.VISIBLE
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun confirmDelete() {
        val sel = adapter.getSelectedItems()
        if (sel.isEmpty()) return
        val total = sel.sumOf { it.size }
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("将删除选中的 ${sel.size} 项（${SizeUtils.format(total)}），此操作不可恢复。")
            .setPositiveButton("删除") { _, _ -> doDelete(sel) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doDelete(sel: List<ScanItem>) {
        btnDelete.isEnabled = false
        Thread {
            val paths = sel.map { it.path }
            val (count, freed) = CleanEngine.deleteAll(paths)
            App.addCleaned(freed)
            handler.post {
                btnDelete.isEnabled = true
                Toast.makeText(this, "已删除 $count 项 · 释放 ${SizeUtils.format(freed)}", Toast.LENGTH_SHORT).show()
                adapter.removePaths(paths.toHashSet())
                if (adapter.itemCount == 0) {
                    finish()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
