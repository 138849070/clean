package com.clean.cleaner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.clean.cleaner.R
import com.clean.cleaner.scan.ScanItem
import com.clean.cleaner.util.SizeUtils

/**
 * 通用扫描结果列表：分组标题 + 可勾选项
 */
class ScanResultAdapter(
    private val items: MutableList<ScanItem>,
    private val onSelectionChanged: (Int, Long) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
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
                val (icon, color) = kindStyle(item.kind)
                h.tvIcon.text = icon
                h.tvIcon.setBackgroundColor(color)
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
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvPath: TextView = v.findViewById(R.id.tvPath)
        val tvSize: TextView = v.findViewById(R.id.tvSize)
    }

    private fun kindStyle(kind: String): Pair<String, Int> = when (kind) {
        "cache" -> "缓" to 0xFF2F7BF5.toInt()
        "log" -> "日" to 0xFF8A8F8D.toInt()
        "tmp" -> "临" to 0xFFFF9500.toInt()
        "thumb" -> "缩" to 0xFF0A84FF.toInt()
        "apk" -> "包" to 0xFFFF3B30.toInt()
        "empty" -> "空" to 0xFF8A8F8D.toInt()
        "residue" -> "残" to 0xFFFF9500.toInt()
        "img" -> "图" to 0xFF0A84FF.toInt()
        "video" -> "视" to 0xFFAF52DE.toInt()
        "audio" -> "音" to 0xFFFF9500.toInt()
        "doc" -> "文" to 0xFF32ADE6.toInt()
        else -> "件" to 0xFF8A8F8D.toInt()
    }
}
