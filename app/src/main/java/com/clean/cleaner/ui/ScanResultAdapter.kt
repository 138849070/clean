package com.clean.cleaner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.clean.cleaner.R
import com.clean.cleaner.scan.ScanItem
import com.clean.cleaner.util.SizeUtils
import java.util.concurrent.Executors

/**
 * 通用扫描结果列表：分组标题 + 可勾选项（图片/视频文件显示缩略图预览）
 */
class ScanResultAdapter(
    private val items: MutableList<ScanItem>,
    private val onSelectionChanged: (Int, Long) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    }

    private val thumbExecutor = Executors.newSingleThreadExecutor()
    private val thumbHandler = Handler(Looper.getMainLooper())
    private val thumbCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private sealed class Row {
        data class Header(val label: String, val total: Long) : Row()
        data class Item(val item: ScanItem) : Row()
    }

    private val rows = ArrayList<Row>()
    private val selected = HashSet<String>()
    var onItemClick: ((ScanItem) -> Unit)? = null
    /** 单击拦截：返回 true 则不切换勾选（用于目录进入等） */
    var onBeforeClick: ((ScanItem) -> Boolean)? = null

    init {
        rebuildRows()
    }

    private fun rebuildRows() {
        rows.clear()
        val groupTotal = HashMap<String, Long>()
        for (it in items) {
            groupTotal[it.groupKey] = groupTotal.getOrDefault(it.groupKey, 0) + it.size
        }
        val seen = HashSet<String>()
        for (it in items) {
            if (seen.add(it.groupKey)) {
                rows.add(Row.Header(it.groupLabel, groupTotal[it.groupKey] ?: 0))
            }
            rows.add(Row.Item(it))
        }
        // 清理已删除项的选择状态
        val paths = items.map { it.path }.toHashSet()
        selected.retainAll(paths)
    }

    fun updateItems(newItems: List<ScanItem>) {
        items.clear()
        items.addAll(newItems)
        rebuildRows()
        notifyDataSetChanged()
        notifySelection()
    }

    fun removePaths(paths: Set<String>) {
        items.removeAll { it.path in paths }
        rebuildRows()
        notifyDataSetChanged()
        notifySelection()
    }

    fun isAllSelected(): Boolean = items.isNotEmpty() && items.all { it.path in selected }

    fun setAllSelected(check: Boolean) {
        selected.clear()
        if (check) items.forEach { selected.add(it.path) }
        notifyDataSetChanged()
        notifySelection()
    }

    fun getSelectedItems(): List<ScanItem> = items.filter { it.path in selected }

    private fun notifySelection() {
        val sel = getSelectedItems()
        onSelectionChanged(sel.size, sel.sumOf { it.size })
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_group_header, parent, false))
        } else {
            ItemHolder(inflater.inflate(R.layout.item_scan_result, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> {
                (holder as HeaderHolder).tvLabel.text = "${row.label} · ${SizeUtils.format(row.total)}"
            }
            is Row.Item -> {
                val h = holder as ItemHolder
                val item = row.item
                h.check.isChecked = item.path in selected
                h.tvName.text = item.name
                h.tvPath.text = item.extra.ifEmpty { item.path }
                h.tvSize.text = SizeUtils.format(item.size)
                bindIcon(h, item)
                h.itemView.setOnClickListener {
                    if (onBeforeClick?.invoke(item) == true) return@setOnClickListener
                    val path = item.path
                    if (path in selected) selected.remove(path) else selected.add(path)
                    h.check.isChecked = path in selected
                    notifySelection()
                }
                h.check.setOnClickListener {
                    val path = item.path
                    if (h.check.isChecked) selected.add(path) else selected.remove(path)
                    notifySelection()
                }
                h.itemView.setOnLongClickListener {
                    onItemClick?.invoke(item)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    private class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvLabel: TextView = v.findViewById(R.id.tvLabel)
    }

    private class ItemHolder(v: View) : RecyclerView.ViewHolder(v) {
        val check: CheckBox = v.findViewById(R.id.check)
        val tvIcon: TextView = v.findViewById(R.id.tvIcon)
        val ivThumb: ImageView = v.findViewById(R.id.ivThumb)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvPath: TextView = v.findViewById(R.id.tvPath)
        val tvSize: TextView = v.findViewById(R.id.tvSize)
    }

    /** 绑定图标：图片文件加载缩略图预览，其余显示类型文字图标 */
    private fun bindIcon(h: ItemHolder, item: ScanItem) {
        h.ivThumb.visibility = View.GONE
        val (icon, color) = kindStyle(item.kind)
        h.tvIcon.text = icon
        h.tvIcon.setBackgroundColor(color)
        h.tvIcon.visibility = View.VISIBLE

        if (item.isDir) return
        val ext = item.path.substringAfterLast('.', "").lowercase()
        if (ext !in imageExts) return
        val tag = item.path
        h.ivThumb.setTag(tag)
        thumbCache.get(tag)?.let { bmp ->
            h.ivThumb.setImageBitmap(bmp)
            h.ivThumb.visibility = View.VISIBLE
            h.tvIcon.visibility = View.GONE
            return
        }
        thumbExecutor.execute {
            val bmp = decodeThumb(tag)
            if (bmp != null) {
                thumbCache.put(tag, bmp)
                thumbHandler.post {
                    if (h.ivThumb.getTag() == tag) {
                        h.ivThumb.setImageBitmap(bmp)
                        h.ivThumb.visibility = View.VISIBLE
                        h.tvIcon.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun decodeThumb(path: String): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > 220 || opts.outHeight / sample > 220) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Throwable) {
            null
        }
    }

    private fun kindStyle(kind: String): Pair<String, Int> = when (kind) {
        "cache" -> "缓" to 0xFF2F7BF5.toInt()
        "log" -> "日" to 0xFF8A8F8D.toInt()
        "tmp" -> "临" to 0xFFFF9500.toInt()
        "thumb" -> "缩" to 0xFF0A84FF.toInt()
        "apk" -> "APK" to 0xFFFF3B30.toInt()
        "empty" -> "空" to 0xFF8A8F8D.toInt()
        "residue" -> "残" to 0xFFFF9500.toInt()
        "suspect" -> "疑" to 0xFFAF52DE.toInt()
        "large" -> "大" to 0xFFFF3B30.toInt()
        "dup" -> "重" to 0xFFFF9500.toInt()
        "sim" -> "似" to 0xFF0A84FF.toInt()
        "img" -> "图" to 0xFF0A84FF.toInt()
        "video" -> "视" to 0xFFAF52DE.toInt()
        "audio" -> "音" to 0xFFFF9500.toInt()
        "doc" -> "文" to 0xFF32ADE6.toInt()
        else -> "件" to 0xFF8A8F8D.toInt()
    }
}
