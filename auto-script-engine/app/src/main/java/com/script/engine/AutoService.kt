package com.script.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 无障碍服务实现。对外暴露:
 *   - 当前窗口根节点 rootNode
 *   - 屏幕尺寸 screenWidth/screenHeight
 *   - 挂起式手势 tap/longTap/swipeGesture
 *   - 挂起式截图 captureBitmap(API 30+)
 *   - 全局动作 performGlobal
 */
class AutoService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoService? = null
            private set
    }

    var screenWidth = 1080
    var screenHeight = 1920

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshScreenSize()
        EngineLog.i("无障碍服务已连接 ${screenWidth}x${screenHeight}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 引擎按需主动读取根节点,无需逐事件处理 */ }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    private fun refreshScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
    }

    val rootNode get() = rootInActiveWindow

    // ----------------------------------------------------------------------
    // 手势(百分比 → 像素在调用方换算后传入)
    // ----------------------------------------------------------------------

    suspend fun tap(x: Float, y: Float, holdMs: Long = 60): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, holdMs)
    }

    suspend fun longTap(x: Float, y: Float, holdMs: Long = 700): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, holdMs)
    }

    suspend fun swipeGesture(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatch(path, durationMs)
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
            val desc = GestureDescription.Builder().addStroke(stroke).build()
            val ok = dispatchGesture(desc, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }

    fun performGlobal(action: String): Boolean {
        val id = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        return performGlobalAction(id)
    }

    // ----------------------------------------------------------------------
    // 截图(图像模板匹配 / OCR 依赖此方法,API 30+)
    // ----------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureBitmap(): Bitmap? = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    result.hardwareBuffer.close()
                    // HARDWARE 位图不支持 getPixel,复制为 ARGB_8888
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    if (cont.isActive) cont.resume(soft)
                } catch (e: Exception) {
                    EngineLog.e("截图转换失败: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }

            override fun onFailure(errorCode: Int) {
                EngineLog.e("截图失败 code=$errorCode")
                if (cont.isActive) cont.resume(null)
            }
        })
    }
}
