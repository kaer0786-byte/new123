package com.script.engine

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.File

/**
 * 悬浮控制面板(纯代码构建 UI,便于单模块交付)。
 * 包含:脚本选择下拉框、启动/暂停/停止、状态显示、实时滚动日志。
 *
 * 用法:
 *   val fc = FloatingController(context, engine)
 *   fc.show()   // 需已授予悬浮窗权限
 *   fc.hide()
 */
class FloatingController(
    private val context: Context,
    private val engine: ScriptEngine
) {

    companion object {
        const val SCRIPTS_DIR = "/sdcard/Scripts/"
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var statusText: TextView? = null
    private var logText: TextView? = null
    private var logScroll: ScrollView? = null
    private var spinner: Spinner? = null

    private val logListener: (String) -> Unit = { line -> appendLog(line) }

    fun show() {
        if (root != null) return
        if (!hasOverlayPermission()) { EngineLog.e("无悬浮窗权限,无法显示面板"); return }

        root = buildView()
        val params = WindowManager.LayoutParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20; y = 120
        }
        wm.addView(root, params)

        // 拖动
        enableDrag(root!!, params)

        // 引擎状态回调
        engine.onStateChanged = { s -> main.post { statusText?.text = "状态:${stateLabel(s)}" } }
        EngineLog.addListener(logListener)
        refreshScripts()
    }

    fun hide() {
        EngineLog.removeListener(logListener)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    // ----------------------------------------------------------------------
    // UI 构建
    // ----------------------------------------------------------------------
    private fun buildView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE202124"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        // 标题
        container.addView(TextView(context).apply {
            text = "AutoScriptEngine"
            setTextColor(Color.WHITE); textSize = 14f
        })

        // 脚本下拉框
        spinner = Spinner(context)
        container.addView(spinner)

        // 状态
        statusText = TextView(context).apply {
            text = "状态:空闲"; setTextColor(Color.parseColor("#8AB4F8")); textSize = 12f
        }
        container.addView(statusText)

        // 按钮行
        val btnRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(makeButton("启动") { onStart() })
        btnRow.addView(makeButton("暂停") { onPauseToggle() })
        btnRow.addView(makeButton("停止") { engine.stop() })
        container.addView(btnRow)

        // 日志滚动区
        logScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160))
        }
        logText = TextView(context).apply {
            setTextColor(Color.parseColor("#00E676")); textSize = 10f
            text = EngineLog.snapshot().joinToString("\n")
        }
        logScroll!!.addView(logText)
        container.addView(logScroll)

        return container
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label; textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

    // ----------------------------------------------------------------------
    // 交互
    // ----------------------------------------------------------------------
    private fun onStart() {
        val name = spinner?.selectedItem?.toString()
        if (name.isNullOrEmpty()) { EngineLog.e("请先选择脚本"); return }
        KeepAliveService.start(context)
        engine.start(SCRIPTS_DIR + name)
    }

    private fun onPauseToggle() {
        when (engine.state) {
            EngineState.RUNNING -> engine.pause()
            EngineState.PAUSED -> engine.resumeFromPause()
            else -> {}
        }
    }

    private fun refreshScripts() {
        val dir = File(SCRIPTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles { f -> f.name.endsWith(".json") }?.map { it.name } ?: emptyList()
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, files)
        spinner?.adapter = adapter
    }

    private fun appendLog(line: String) {
        main.post {
            logText?.append("\n$line")
            logScroll?.post { logScroll?.fullScroll(View.FOCUS_DOWN) }
        }
    }

    // ----------------------------------------------------------------------
    // 工具
    // ----------------------------------------------------------------------
    private fun enableDrag(view: View, params: WindowManager.LayoutParams) {
        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y; touchX = e.rawX; touchY = e.rawY; true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (e.rawX - touchX).toInt()
                    params.y = initY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(view, params); true
                }
                else -> false
            }
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun stateLabel(s: EngineState) = when (s) {
        EngineState.IDLE -> "空闲"
        EngineState.RUNNING -> "运行中"
        EngineState.PAUSED -> "已暂停"
        EngineState.ERROR -> "报错"
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
