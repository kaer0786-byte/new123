package com.script.engine

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * 通用指令执行器:解析并执行单个 Action。
 * 包含随机偏移点击、变量替换、每步重试、失败时全局弹窗清扫。
 */
class ActionExecutor(
    private val service: AutoService,
    private val finder: UnifiedElementFinder,
    private val vars: VariableStore
) {

    companion object {
        private const val RANDOM_OFFSET_PX = 5      // 点击随机偏移 ±5px(常规拟真)
        private const val POPUP_KEYWORDS = "关闭|跳过|取消|知道了|我知道了|好的|以后再说|下次"
    }

    /**
     * 执行一个 Action(带重试与容灾)。
     * 流程:尝试 → 失败重试 retry 次 → 仍失败则全局弹窗清扫后再试一次 → 再失败跳过并记录。
     */
    suspend fun execute(action: Action) {
        var attempt = 0
        while (attempt <= action.retry) {
            try {
                runAction(action)
                return
            } catch (e: Exception) {
                attempt++
                EngineLog.e("[${action.type}] 第 $attempt 次失败: ${e.message}")
                if (attempt <= action.retry) delay(action.retryInterval)
            }
        }
        // 连续失败 → 全局弹窗清扫后再试一次
        EngineLog.i("触发全局弹窗清扫后重试…")
        cleanupPopups()
        try {
            runAction(action)
        } catch (e: Exception) {
            EngineLog.e("[${action.type}] 清扫后仍失败,跳过。原因: ${e.message}")
        }
    }

    /** 执行不带重试的单次动作(内部子指令递归也走 execute 以复用重试) */
    private suspend fun runAction(action: Action) {
        when (action.type.lowercase()) {
            "click" -> doClick(action, longPress = false)
            "longclick" -> doClick(action, longPress = true)
            "swipe" -> doSwipe(action)
            "input" -> doInput(action)
            "wait" -> doWait(action)
            "loop" -> doLoop(action)
            "if" -> doIf(action)
            "screenshot" -> doScreenshot(action)
            "globalaction" -> doGlobal(action)
            "setvar" -> doSetVar(action)
            "ocr" -> doOcrRead(action)
            "launchapp" -> doLaunchApp(action)
            else -> EngineLog.e("未知动作类型: ${action.type}")
        }
    }

    // ----------------------------------------------------------------------
    // click / longClick
    // ----------------------------------------------------------------------
    private suspend fun doClick(action: Action, longPress: Boolean) {
        val cond = action.condition ?: throw IllegalArgumentException("click 缺少 condition")
        val found = finder.findElement(action.timeout, resolveCondition(cond))
        // 若命中的是可点击节点,优先用节点 ACTION_CLICK(更稳);否则坐标手势
        val node = found.node
        if (!longPress && node != null && clickNode(node)) {
            EngineLog.i("节点点击成功")
            return
        }
        val (px, py) = toPixelWithJitter(found.xPercent, found.yPercent)
        val ok = if (longPress) service.longTap(px, py) else service.tap(px, py)
        if (!ok) throw RuntimeException("手势派发失败")
        EngineLog.i("${if (longPress) "长按" else "点击"} 像素($px,$py)")
    }

    /** 点击节点自身或最近的可点击祖先 */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    // ----------------------------------------------------------------------
    // swipe
    // ----------------------------------------------------------------------
    private suspend fun doSwipe(action: Action) {
        val w = service.screenWidth; val h = service.screenHeight
        val (x1p, y1p, x2p, y2p) = when {
            action.start != null && action.end != null && action.start.size >= 2 && action.end.size >= 2 ->
                Quad(action.start[0], action.start[1], action.end[0], action.end[1])
            action.direction != null -> directionToPoints(action.direction)
            else -> throw IllegalArgumentException("swipe 需 direction 或 start/end")
        }
        val ok = service.swipeGesture(x1p * w, y1p * h, x2p * w, y2p * h, action.duration)
        if (!ok) throw RuntimeException("滑动手势失败")
        EngineLog.i("滑动 ${action.direction ?: "$x1p,$y1p→$x2p,$y2p"}")
    }

    /** 方向 → 起终点百分比(屏幕中部滑动) */
    private fun directionToPoints(dir: String): Quad = when (dir.lowercase()) {
        "up" -> Quad(0.5f, 0.75f, 0.5f, 0.25f)
        "down" -> Quad(0.5f, 0.25f, 0.5f, 0.75f)
        "left" -> Quad(0.75f, 0.5f, 0.25f, 0.5f)
        "right" -> Quad(0.25f, 0.5f, 0.75f, 0.5f)
        else -> throw IllegalArgumentException("未知方向: $dir")
    }

    // ----------------------------------------------------------------------
    // input
    // ----------------------------------------------------------------------
    private suspend fun doInput(action: Action) {
        val cond = action.condition ?: throw IllegalArgumentException("input 缺少 condition")
        val found = finder.findElement(action.timeout, resolveCondition(cond))
        val node = found.node ?: findEditableAt() ?: throw RuntimeException("未找到可输入控件")
        val textValue = vars.resolve(action.text)
        val finalText = if (action.clear) textValue else (node.text?.toString() ?: "") + textValue
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) throw RuntimeException("设置文本失败")
        EngineLog.i("输入文本: $finalText")
    }

    private fun findEditableAt(): AccessibilityNodeInfo? {
        val root = service.rootNode ?: return null
        return firstEditable(root)
    }

    private fun firstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) firstEditable(node.getChild(i))?.let { return it }
        return null
    }

    // ----------------------------------------------------------------------
    // wait(固定等待 / 条件等待)
    // ----------------------------------------------------------------------
    private suspend fun doWait(action: Action) {
        if (action.condition != null) {
            EngineLog.i("等待元素出现(≤${action.timeout}ms)")
            val ok = finder.exists(resolveCondition(action.condition), action.timeout)
            if (!ok) throw RuntimeException("等待元素超时")
        } else {
            val ms = action.ms ?: 1000
            EngineLog.i("固定等待 ${ms}ms")
            delay(ms)
        }
    }

    // ----------------------------------------------------------------------
    // loop(times 或 while 条件)
    // ----------------------------------------------------------------------
    private suspend fun doLoop(action: Action) {
        val children = action.actions ?: emptyList()
        if (action.times != null) {
            for (i in 0 until action.times) {
                EngineLog.i("循环 ${i + 1}/${action.times}")
                for (child in children) execute(child)
            }
        } else if (action.whileCondition != null) {
            var guard = 0
            while (finder.exists(resolveCondition(action.whileCondition)) && guard < 1000) {
                guard++
                EngineLog.i("while 循环第 $guard 次")
                for (child in children) execute(child)
            }
        } else {
            EngineLog.e("loop 需 times 或 whileCondition")
        }
    }

    // ----------------------------------------------------------------------
    // if / else(元素存在 或 变量等值)
    // ----------------------------------------------------------------------
    private suspend fun doIf(action: Action) {
        val truth = when {
            action.varName != null -> vars.get(action.varName) == (action.equals ?: "")
            action.condition != null -> finder.exists(resolveCondition(action.condition), action.timeout)
            else -> false
        }
        EngineLog.i("if 判定 = $truth")
        val branch = if (truth) action.thenActions else action.elseActions
        branch?.forEach { execute(it) }
    }

    // ----------------------------------------------------------------------
    // screenshot(保存到本地供调试)
    // ----------------------------------------------------------------------
    private suspend fun doScreenshot(action: Action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { EngineLog.e("截图需 Android 11+"); return }
        val bmp = service.captureBitmap() ?: throw RuntimeException("截图失败")
        val path = action.path ?: "/sdcard/Scripts/shots/shot_${System.currentTimeMillis()}.png"
        try {
            File(path).parentFile?.mkdirs()
            FileOutputStream(path).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            EngineLog.i("截图已保存: $path")
        } finally {
            bmp.recycle()
        }
    }

    // ----------------------------------------------------------------------
    // globalAction / setVar / ocr / launchApp
    // ----------------------------------------------------------------------
    private fun doGlobal(action: Action) {
        val a = action.globalAction ?: throw IllegalArgumentException("globalAction 缺少动作名")
        if (!service.performGlobal(a)) throw RuntimeException("全局动作失败: $a")
        EngineLog.i("全局动作: $a")
    }

    private fun doSetVar(action: Action) {
        val name = action.varName ?: throw IllegalArgumentException("setVar 缺少 varName")
        vars.set(name, vars.resolve(action.text))
    }

    /** OCR 读屏并存入变量(供后续 {{$var}} 使用) */
    private suspend fun doOcrRead(action: Action) {
        val name = action.varName ?: throw IllegalArgumentException("ocr 缺少 varName")
        val text = finder.ocrReadAll()
        vars.set(name, text)
        EngineLog.i("OCR 读屏 ${text.length} 字,已存入 \$$name")
    }

    private suspend fun doLaunchApp(action: Action) {
        val pkg = action.packageName ?: throw IllegalArgumentException("launchApp 缺少 packageName")
        val intent = service.packageManager.getLaunchIntentForPackage(pkg)
            ?: throw RuntimeException("未安装或无法启动: $pkg")
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        EngineLog.i("启动应用: $pkg")
        delay(1500)
    }

    // ----------------------------------------------------------------------
    // 全局弹窗清扫:点击当前窗口内含"关闭/跳过/取消/知道了"语义的控件
    // ----------------------------------------------------------------------
    private fun cleanupPopups() {
        val root = service.rootNode ?: return
        val regex = Regex(POPUP_KEYWORDS)
        val hits = mutableListOf<AccessibilityNodeInfo>()
        collectPopupNodes(root, regex, hits)
        hits.forEach { node ->
            var n: AccessibilityNodeInfo? = node
            while (n != null) {
                if (n.isClickable) { n.performAction(AccessibilityNodeInfo.ACTION_CLICK); break }
                n = n.parent
            }
        }
        if (hits.isNotEmpty()) EngineLog.i("弹窗清扫点击了 ${hits.size} 个控件")
    }

    private fun collectPopupNodes(
        node: AccessibilityNodeInfo?, regex: Regex, out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        val label = (node.text?.toString() ?: "") + (node.contentDescription?.toString() ?: "")
        if (label.isNotEmpty() && regex.containsMatchIn(label)) out.add(node)
        for (i in 0 until node.childCount) collectPopupNodes(node.getChild(i), regex, out)
    }

    // ----------------------------------------------------------------------
    // 工具:百分比→像素(带随机偏移);变量替换条件里的坐标/文本
    // ----------------------------------------------------------------------
    private fun toPixelWithJitter(xp: Float, yp: Float): Pair<Float, Float> {
        val jitterX = Random.nextInt(-RANDOM_OFFSET_PX, RANDOM_OFFSET_PX + 1)
        val jitterY = Random.nextInt(-RANDOM_OFFSET_PX, RANDOM_OFFSET_PX + 1)
        val px = (xp * service.screenWidth + jitterX).coerceIn(0f, service.screenWidth - 1f)
        val py = (yp * service.screenHeight + jitterY).coerceIn(0f, service.screenHeight - 1f)
        return px to py
    }

    /** 对查找条件中的文本类字段做变量替换(如 {{$result}}) */
    private fun resolveCondition(c: FindCondition): FindCondition = c.copy(
        text = c.text?.let { vars.resolve(it) },
        desc = c.desc?.let { vars.resolve(it) },
        ocr = c.ocr?.let { vars.resolve(it) }
    )

    /** data class 自动生成 component1..4,供 val (x1,y1,x2,y2) = ... 解构 */
    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
}
