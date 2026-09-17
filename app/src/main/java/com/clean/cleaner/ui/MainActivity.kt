package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clean.cleaner.App
import com.clean.cleaner.R
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
    private lateinit var grid: LinearLayout

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

        findViewById<View>(R.id.btnScan).setOnClickListener {
            startActivity(Intent(this, CleanActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showAbout() }

        buildGrid()
        refreshHeader()
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionActivity.hasStorageAccess()) {
            startActivityForResult(Intent(this, PermissionActivity::class.java), 1001)
        }
        refreshHeader()
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

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Clean 清理")
            .setMessage("版本 1.1.0\n\n免费安卓存储清理工具，无会员、无广告、无网络请求。\n\n清理功能均在本机完成，不会上传任何数据。")
            .setPositiveButton("好的", null)
            .show()
    }
}
