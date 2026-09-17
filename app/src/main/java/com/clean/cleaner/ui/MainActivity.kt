package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clean.cleaner.R
import com.clean.cleaner.scan.ScanItem
import com.clean.cleaner.scan.StorageHelper
import com.clean.cleaner.util.SizeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var ringView: RingView
    private lateinit var tvUsed: TextView
    private lateinit var tvPercent: TextView
    private lateinit var tvSpaceInfo: TextView
    private lateinit var categoryGrid: LinearLayout
    private lateinit var featureList: LinearLayout

    private data class CategoryInfo(val key: String, val emoji: String)
    private data class FeatureInfo(val name: String, val desc: String, val emoji: String, val type: String)

    private val categories = listOf(
        CategoryInfo("image", "🖼️"),
        CategoryInfo("video", "🎬"),
        CategoryInfo("audio", "🎵"),
        CategoryInfo("doc", "📄"),
        CategoryInfo("apk", "📦"),
        CategoryInfo("other", "📁")
    )

    private val features = listOf(
        FeatureInfo("垃圾清理", "缓存 · 日志 · 临时文件 · 缩略图", "🧹", "junk"),
        FeatureInfo("微信/QQ 深度清理", "聊天图片 · 视频 · 语音 · 缓存", "💬", "deep"),
        FeatureInfo("大文件", "找出占用空间最大的文件", "🗜️", "large"),
        FeatureInfo("重复文件", "清理重复占用空间的文件", "📑", "dup"),
        FeatureInfo("相似图片", "找出内容相似的图片", "🖼️", "sim"),
        FeatureInfo("安装包清理", "清理残留的 APK 安装包", "📦", "apk"),
        FeatureInfo("卸载残留", "卸载应用后遗留的数据", "🗑️", "residue"),
        FeatureInfo("空文件夹", "清理空的文件夹", "📁", "empty"),
        FeatureInfo("应用管理", "查看应用缓存并清理", "📱", "apps"),
        FeatureInfo("文件浏览器", "浏览并管理所有文件", "🗂️", "files")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ringView = findViewById(R.id.ringView)
        tvUsed = findViewById(R.id.tvUsed)
        tvPercent = findViewById(R.id.tvPercent)
        tvSpaceInfo = findViewById(R.id.tvSpaceInfo)
        categoryGrid = findViewById(R.id.categoryGrid)
        featureList = findViewById(R.id.featureList)

        findViewById<View>(R.id.btnScan).setOnClickListener {
            startActivity(Intent(this, CleanActivity::class.java))
        }

        buildFeatureList()
        refreshStorage()
        loadCategories()
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionActivity.hasStorageAccess()) {
            startActivityForResult(Intent(this, PermissionActivity::class.java), 1001)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (!PermissionActivity.hasStorageAccess()) {
                // 仍未授权，展示权限页引导（不阻塞主页浏览）
            } else {
                refreshStorage()
                loadCategories()
            }
        }
    }

    private fun refreshStorage() {
        val total = StorageHelper.totalSpace()
        val free = StorageHelper.freeSpace()
        val used = StorageHelper.usedSpace()
        tvUsed.text = SizeUtils.format(used)
        tvPercent.text = "已用 ${SizeUtils.formatPercent(used, total)}%"
        tvSpaceInfo.text = "可用 ${SizeUtils.format(free)} · 共 ${SizeUtils.format(total)}"
        ringView.progress = used.toFloat() / total.toFloat()
    }

    private fun loadCategories() {
        categoryGrid.removeAllViews()
        if (!PermissionActivity.hasStorageAccess()) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                StorageHelper.categorize { }
            }
            renderCategories(result)
        }
    }

    private fun renderCategories(sizes: Map<String, Long>) {
        categoryGrid.removeAllViews()
        val total = sizes.values.sum()
        val rows = categories.chunked(3)
        val inflater = LayoutInflater.from(this)
        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val lp = (layoutParams as LinearLayout.LayoutParams)
                lp.bottomMargin = dp(8)
            }
            for (c in row) {
                val item = inflater.inflate(R.layout.item_category, rowLayout, false)
                item.findViewById<TextView>(R.id.tvIcon).text = c.emoji
                item.findViewById<TextView>(R.id.tvName).text = StorageHelper.categoryLabel(c.key)
                val size = sizes[c.key] ?: 0L
                item.findViewById<TextView>(R.id.tvSize).text = SizeUtils.format(size)
                item.setOnClickListener {
                    val intent = Intent(this, CategoryFilesActivity::class.java)
                    intent.putExtra("category", c.key)
                    startActivity(intent)
                }
                rowLayout.addView(item)
            }
            categoryGrid.addView(rowLayout)
        }
    }

    private fun buildFeatureList() {
        val inflater = LayoutInflater.from(this)
        for (f in features) {
            val item = inflater.inflate(R.layout.item_feature, featureList, false)
            item.findViewById<TextView>(R.id.tvIcon).text = f.emoji
            item.findViewById<TextView>(R.id.tvName).text = f.name
            item.findViewById<TextView>(R.id.tvDesc).text = f.desc
            item.setOnClickListener { openFeature(f.type) }
            featureList.addView(item)
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
