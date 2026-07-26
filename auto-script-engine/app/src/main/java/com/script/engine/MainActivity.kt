package com.script.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 主界面:纯代码 UI。
 * 提供权限引导(无障碍 / 悬浮窗 / 忽略电池优化)与"显示悬浮面板"入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var engine: ScriptEngine
    private var floating: FloatingController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = ScriptEngine(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "AutoScriptEngine 控制台"; textSize = 20f
        })
        root.addView(button("① 开启无障碍服务") { openAccessibilitySettings() })
        root.addView(button("② 授予悬浮窗权限") { requestOverlay() })
        root.addView(button("③ 忽略电池优化(保活)") { requestIgnoreBattery() })
        root.addView(button("④ 显示悬浮控制面板") { showPanel() })
        root.addView(TextView(this).apply {
            text = "\n脚本目录:/sdcard/Scripts/*.json\n模板图片:/sdcard/Scripts/templates/"
            textSize = 12f
        })
        setContentView(root)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 }
        setOnClickListener { onClick() }
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")
            ))
        }
    }

    private fun requestIgnoreBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                ))
            }
        }
    }

    private fun showPanel() {
        if (floating == null) floating = FloatingController(this, engine)
        floating?.show()
    }
}
