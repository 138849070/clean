package com.clean.cleaner.ui

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.clean.cleaner.R
import com.clean.cleaner.util.StatusBarUtil

/** 工具箱：秒搜文件 / 安装包提取 / 吃掉内存 / 设置 / 五星好评 */
class ToolboxActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparent(this, lightIcons = !StatusBarUtil.isDarkMode(this))
        setContentView(R.layout.activity_toolbox)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.rowSearch).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<View>(R.id.rowApk).setOnClickListener {
            startActivity(Intent(this, ScanResultActivity::class.java).putExtra("type", "apk"))
        }
        findViewById<View>(R.id.rowMemory).setOnClickListener { cleanMemory() }
        findViewById<View>(R.id.rowSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.rowRate).setOnClickListener { showRate() }
    }

    private fun cleanMemory() {
        Thread {
            var killed = 0
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                @Suppress("DEPRECATION")
                val procs = am.runningAppProcesses ?: emptyList()
                val myPid = android.os.Process.myPid()
                for (p in procs) {
                    if (p.pid == myPid) continue
                    if (p.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                        try {
                            am.killBackgroundProcesses(p.processName)
                            killed++
                        } catch (ignored: Exception) {
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
            runOnUiThread {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val availMb = mi.availMem / 1024 / 1024
                val totalMb = mi.totalMem / 1024 / 1024
                AlertDialog.Builder(this)
                    .setTitle("内存清理完成")
                    .setMessage("已尝试清理后台进程 $killed 个。\n\n当前可用内存：$availMb MB / 共 $totalMb MB")
                    .setPositiveButton("好的", null)
                    .show()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun showRate() {
        AlertDialog.Builder(this)
            .setTitle("五星好评")
            .setMessage("如果 Clean 清理帮到了你，可以去 GitHub 点个 Star 支持一下。")
            .setPositiveButton("打开 GitHub") { _, _ ->
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/138849070/clean"))
                    )
                } catch (ignored: Exception) {
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
