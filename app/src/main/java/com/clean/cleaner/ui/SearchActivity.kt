package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
import com.clean.cleaner.scan.Walker
import com.clean.cleaner.util.Settings
import com.clean.cleaner.util.SizeUtils
import com.clean.cleaner.util.StatusBarUtil

/** 秒搜文件：输入关键词实时全盘搜索，支持勾选删除 */
@SuppressLint("SetTextI18n")
class SearchActivity : AppCompatActivity() {

    private lateinit var resultList: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var tvSearching: TextView
    private lateinit var checkAll: CheckBox
    private lateinit var tvSelected: TextView
    private lateinit var btnClean: Button
    private lateinit var etSearch: EditText

    private val items = mutableListOf<ScanItem>()
    private lateinit var adapter: ScanResultAdapter
    private var searchThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_search)

        if (Settings.keepScreen(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        resultList = findViewById(R.id.resultList)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvSearching = findViewById(R.id.tvSearching)
        checkAll = findViewById(R.id.checkAll)
        tvSelected = findViewById(R.id.tvSelected)
        btnClean = findViewById(R.id.btnClean)
        etSearch = findViewById(R.id.etSearch)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adapter = ScanResultAdapter(items) { count, size ->
            tvSelected.text = "已选 $count 项 · ${SizeUtils.format(size)}"
            btnClean.isEnabled = count > 0
        }
        resultList.layoutManager = LinearLayoutManager(this)
        resultList.adapter = adapter

        checkAll.setOnClickListener { adapter.setAllSelected(checkAll.isChecked) }
        btnClean.setOnClickListener { confirmClean() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                doSearch(s?.toString() ?: "")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        searchThread?.interrupt()
    }

    private fun doSearch(q: String) {
        searchThread?.interrupt()
        val keyword = q.trim()
        if (keyword.isEmpty()) {
            loadingOverlay.visibility = View.GONE
            items.clear()
            adapter.notifyDataSetChanged()
            return
        }
        loadingOverlay.visibility = View.VISIBLE
        tvSearching.text = "搜索中…"
        searchThread = Thread {
            val out = ArrayList<ScanItem>()
            Walker.walk(Walker.root, onFile = { f ->
                if (!f.isDirectory && f.length() > 0 && f.name.contains(keyword, ignoreCase = true)) {
                    out.add(
                        ScanItem(
                            f.absolutePath, f.name, f.length(),
                            groupKey = "search", groupLabel = "搜索结果",
                            kind = StorageHelper.categoryOf(f.name).let {
                                if (it == "other" || it == "apk") "doc" else it
                            },
                            extra = SizeUtils.format(f.length())
                        )
                    )
                }
                if (out.size >= 300) return@walk false
                true
            })
            handler.post {
                if (Thread.currentThread().isInterrupted) return@post
                loadingOverlay.visibility = View.GONE
                adapter.updateItems(out)
                tvSearching.text = "找到 ${out.size} 个文件"
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun confirmClean() {
        val sel = adapter.getSelectedItems()
        if (sel.isEmpty()) return
        val total = sel.sumOf { it.size }
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("将删除选中的 ${sel.size} 项，共 ${SizeUtils.format(total)}。")
            .setPositiveButton("删除") { _, _ ->
                btnClean.isEnabled = false
                Thread {
                    val paths = sel.map { it.path }
                    val (count, freed) = CleanEngine.deleteAll(paths)
                    App.addCleaned(freed)
                    handler.post {
                        btnClean.isEnabled = true
                        Toast.makeText(this, "已删除 $count 项 · 释放 ${SizeUtils.format(freed)}", Toast.LENGTH_SHORT).show()
                        adapter.removePaths(paths.toHashSet())
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
