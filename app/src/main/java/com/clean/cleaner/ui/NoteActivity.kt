package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.util.StatusBarUtil
import org.json.JSONObject

/** 文件备注：给文件/文件夹添加备注，可标记「不要清理」 */
@SuppressLint("SetTextI18n")
class NoteActivity : AppCompatActivity() {

    private data class Note(val text: String, val protect: Boolean)

    private lateinit var noteList: LinearLayout
    private lateinit var tvEmpty: TextView
    private val notes = LinkedHashMap<String, Note>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_note)

        noteList = findViewById(R.id.noteList)
        tvEmpty = findViewById(R.id.tvEmpty)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAdd).setOnClickListener { showAddDialog() }

        loadNotes()
        render()
    }

    private fun prefs() = getSharedPreferences("clean", Context.MODE_PRIVATE)

    private fun loadNotes() {
        notes.clear()
        val raw = prefs().getString("notes", "") ?: ""
        if (raw.isEmpty()) return
        try {
            val obj = JSONObject(raw)
            val it = obj.keys()
            while (it.hasNext()) {
                val path = it.next()
                val n = obj.getJSONObject(path)
                notes[path] = Note(n.optString("text", ""), n.optBoolean("protect", false))
            }
        } catch (ignored: Exception) {
        }
    }

    private fun saveNotes() {
        val obj = JSONObject()
        for ((path, n) in notes) {
            obj.put(path, JSONObject().put("text", n.text).put("protect", n.protect))
        }
        prefs().edit().putString("notes", obj.toString()).apply()
    }

    private fun render() {
        noteList.removeAllViews()
        if (notes.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            return
        }
        tvEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        val entries = notes.toList()
        for ((path, note) in entries) {
            val row = inflater.inflate(R.layout.item_note, noteList, false)
            row.findViewById<TextView>(R.id.tvNotePath).text = path
            row.findViewById<TextView>(R.id.tvNoteText).text = note.text.ifEmpty { "（无备注文字）" }
            val sw = row.findViewById<Switch>(R.id.swProtect)
            sw.isChecked = note.protect
            sw.setOnCheckedChangeListener { _, v ->
                notes[path] = note.copy(protect = v)
                saveNotes()
                if (v) App.addProtected(path) else App.removeProtected(path)
            }
            row.findViewById<View>(R.id.btnDelNote).setOnClickListener {
                notes.remove(path)
                App.removeProtected(path)
                saveNotes()
                render()
                Toast.makeText(this, "已删除备注", Toast.LENGTH_SHORT).show()
            }
            noteList.addView(row)
        }
    }

    private fun showAddDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_add_note, null)
        val etPath = content.findViewById<EditText>(R.id.etPath)
        val etText = content.findViewById<EditText>(R.id.etText)
        val cbProtect = content.findViewById<CheckBox>(R.id.cbProtect)
        AlertDialog.Builder(this)
            .setTitle("添加备注")
            .setView(content)
            .setPositiveButton("保存") { _, _ ->
                val path = etPath.text.toString().trim()
                val text = etText.text.toString().trim()
                if (path.isEmpty()) {
                    Toast.makeText(this, "请输入文件路径", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                notes[path] = Note(text, cbProtect.isChecked)
                if (cbProtect.isChecked) App.addProtected(path)
                saveNotes()
                render()
                Toast.makeText(this, "已添加备注", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
