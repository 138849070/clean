package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clean.cleaner.R
import com.clean.cleaner.scan.CleanEngine
import com.clean.cleaner.scan.ScanItem
import com.clean.cleaner.scan.StorageHelper
import com.clean.cleaner.scan.Walker
import com.clean.cleaner.util.SizeUtils
import java.io.File

@SuppressLint("SetTextI18n")
class FileBrowserActivity : AppCompatActivity() {

    private lateinit var tvPath: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var fileList: RecyclerView
    private lateinit var bottomBar: View
    private lateinit var tvSelected: TextView
    private lateinit var btnDelete: Button

    private val items = mutableListOf<ScanItem>()
    private lateinit var adapter: ScanResultAdapter
    private var currentDir: File = Walker.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)

        tvPath = findViewById(R.id.tvPath)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        fileList = findViewById(R.id.fileList)
        bottomBar = findViewById(R.id.bottomBar)
        tvSelected = findViewById(R.id.tvSelected)
        btnDelete = findViewById(R.id.btnDelete)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adapter = ScanResultAdapter(items) { count, size ->
            tvSelected.text = "已选 $count 项 · ${SizeUtils.format(size)}"
            btnDelete.isEnabled = count > 0
            btnSelectAll.text = if (adapter.isAllSelected()) "取消全选" else "全选"
        }
        adapter.onBeforeClick = { item ->
            // 无勾选时，单击目录进入；有勾选时单击为勾选
            if (adapter.getSelectedItems().isEmpty() && item.isDir) {
                enterDir(File(item.path))
                true
            } else false
        }
        fileList.layoutManager = LinearLayoutManager(this)
        fileList.adapter = adapter

        btnSelectAll.setOnClickListener {
            adapter.setAllSelected(!adapter.isAllSelected())
        }
        btnDelete.setOnClickListener { confirmDelete() }
        tvPath.setOnClickListener {
            // 点击路径区域返回上级
            goUp()
        }

        val start = intent.getStringExtra("path")?.let { File(it) }
        enterDir(start?.takeIf { it.isDirectory } ?: Walker.root)
    }

    private fun enterDir(dir: File) {
        if (!dir.isDirectory) return
        currentDir = dir
        tvPath.text = dir.absolutePath
        items.clear()
        val children = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
        for (f in children) {
            val isDir = f.isDirectory
            items.add(
                ScanItem(
                    path = f.absolutePath,
                    name = f.name,
                    size = if (isDir) 0 else f.length(),
                    isDir = isDir,
                    groupKey = "browse",
                    groupLabel = dir.name,
                    kind = if (isDir) "dir" else StorageHelper.categoryOf(f.name).let {
                        if (it == "other") "doc" else it
                    },
                    extra = if (isDir) "文件夹" else SizeUtils.format(f.length())
                )
            )
        }
        adapter.updateItems(items)
        bottomBar.visibility = View.GONE
    }

    private fun goUp() {
        val parent = currentDir.parentFile
        if (parent != null && parent.exists() && parent.absolutePath.startsWith(Walker.root.absolutePath)) {
            enterDir(parent)
        } else if (parent != null && parent.exists()) {
            enterDir(parent)
        } else {
            Toast.makeText(this, "已到根目录", Toast.LENGTH_SHORT).show()
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
            runOnUiThread {
                btnDelete.isEnabled = true
                Toast.makeText(this, "已删除 $count 项 · 释放 ${SizeUtils.format(freed)}", Toast.LENGTH_SHORT).show()
                enterDir(currentDir)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
