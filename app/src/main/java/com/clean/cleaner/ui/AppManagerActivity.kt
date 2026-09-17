package com.clean.cleaner.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clean.cleaner.App
import com.clean.cleaner.R
import com.clean.cleaner.scan.CleanEngine
import com.clean.cleaner.scan.Walker
import com.clean.cleaner.util.SizeUtils
import com.clean.cleaner.util.StatusBarUtil
import java.io.File

@SuppressLint("SetTextI18n")
class AppManagerActivity : AppCompatActivity() {

    private data class AppInfo(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        val isSystem: Boolean,
        val externalSize: Long
    )

    private lateinit var appList: RecyclerView
    private lateinit var tvCount: TextView
    private val apps = ArrayList<AppInfo>()
    private val handler = Handler(Looper.getMainLooper())
    private var loadThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_app_manager)

        appList = findViewById(R.id.appList)
        tvCount = findViewById(R.id.tvCount)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        appList.layoutManager = LinearLayoutManager(this)
        appList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = apps.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(
                    layoutInflater.inflate(R.layout.item_app, parent, false)
                ) {}

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val app = apps[position]
                holder.itemView.findViewById<ImageView>(R.id.appIcon).setImageDrawable(app.icon)
                holder.itemView.findViewById<TextView>(R.id.tvName).text = app.label
                holder.itemView.findViewById<TextView>(R.id.tvInfo).text =
                    app.packageName + if (app.isSystem) " · 系统" else ""
                holder.itemView.findViewById<TextView>(R.id.tvCache).text =
                    if (app.externalSize > 0) "数据 ${SizeUtils.format(app.externalSize)}" else ""
                holder.itemView.setOnClickListener { showAppDialog(app) }
            }
        }

        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadThread?.interrupt()
    }

    private fun loadApps() {
        loadThread = Thread {
            val pm = packageManager
            @Suppress("DEPRECATION")
            val infos = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val root = Walker.root
            val list = ArrayList<AppInfo>()
            for (info in infos) {
                if (Thread.currentThread().isInterrupted) return@Thread
                val externalDir = File(root, "Android/data/${info.packageName}")
                val size = if (externalDir.exists()) Walker.dirSize(externalDir) else 0L
                list.add(
                    AppInfo(
                        label = info.loadLabel(pm)?.toString() ?: info.packageName,
                        packageName = info.packageName,
                        icon = info.loadIcon(pm),
                        isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        externalSize = size
                    )
                )
            }
            list.sortBy { it.label.lowercase() }
            handler.post {
                apps.clear()
                apps.addAll(list)
                tvCount.text = "${apps.size} 个应用"
                appList.adapter?.notifyDataSetChanged()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun showAppDialog(app: AppInfo) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(app.label)
            .setMessage("包名：${app.packageName}\n外部数据：${SizeUtils.format(app.externalSize)}\n\n外部数据包括 Android/data 下的缓存与文件，清理不会影响应用本身。")
            .setPositiveButton("清理外部数据") { _, _ ->
                cleanExternal(app)
            }
            .setNeutralButton("打开") { _, _ ->
                try {
                    startActivity(packageManager.getLaunchIntentForPackage(app.packageName))
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开该应用", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("卸载", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                startActivity(intent)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun cleanExternal(app: AppInfo) {
        Thread {
            val dir = File(Walker.root, "Android/data/${app.packageName}")
            val freed = if (dir.exists()) CleanEngine.delete(dir.absolutePath) else 0L
            App.addCleaned(freed)
            handler.post {
                Toast.makeText(this, "已释放 ${SizeUtils.format(freed)}", Toast.LENGTH_SHORT).show()
                loadApps()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
