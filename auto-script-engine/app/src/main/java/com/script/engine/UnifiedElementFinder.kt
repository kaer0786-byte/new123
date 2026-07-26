package com.script.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min

/**
 * 多模态统一查找器。
 * 查找优先级:AccessibilityNode ID → Node Text/Desc → 图像模板匹配 → OCR。
 * 所有命中最终统一返回屏幕百分比坐标(0f~1f)。
 */
class UnifiedElementFinder(private val service: AutoService) {

    companion object {
        private const val POLL_INTERVAL = 100L      // 轮询间隔
        private const val IMAGE_SIM_THRESHOLD = 0.88f // 图像相似度阈值
        private const val IMAGE_SEARCH_STRIDE = 12    // 模板匹配滑窗步长(px),越小越准越慢
        private const val IMAGE_SAMPLE_GRID = 8       // 模板采样网格 8x8=64 个采样点
    }

    private val ocrClient by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 在 timeout 内轮询查找元素,找到即返回,超时抛 ElementNotFoundException。
     * @param timeout 超时毫秒
     * @param condition 查找条件(可含多种候选方式)
     */
    suspend fun findElement(timeout: Long, condition: FindCondition): FoundElement {
        val deadline = SystemClock.uptimeMillis() + timeout
        var lastError = ""
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                findOnce(condition)?.let {
                    EngineLog.i("命中[${it.strategy}] → (${fmt(it.xPercent)}, ${fmt(it.yPercent)})")
                    return it
                }
            } catch (e: Exception) {
                lastError = e.message ?: ""
            }
            delay(POLL_INTERVAL)
        }
        EngineLog.e("查找超时 $condition ${if (lastError.isNotEmpty()) "($lastError)" else ""}")
        throw ElementNotFoundException(condition)
    }

    /** 仅判断元素是否存在(用于 if / while),不抛异常 */
    suspend fun exists(condition: FindCondition, timeout: Long = 0): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        do {
            if (runCatching { findOnce(condition) }.getOrNull() != null) return true
            if (timeout > 0) delay(POLL_INTERVAL)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    /** 单次查找:按优先级依次尝试 */
    private suspend fun findOnce(c: FindCondition): FoundElement? {
        // 1) 直接坐标(最高确定性,直接返回)
        if (c.x != null && c.y != null) {
            return FoundElement(c.x, c.y, null, "coordinate")
        }
        // 2) 节点 ID
        if (!c.id.isNullOrEmpty()) {
            byNodeId(c.id)?.let { return it }
        }
        // 3) 节点 Text / Desc
        if (!c.text.isNullOrEmpty() || !c.desc.isNullOrEmpty()) {
            byNodeText(c.text, c.desc)?.let { return it }
        }
        // 4) 图像模板匹配
        if (!c.image.isNullOrEmpty()) {
            byImage(c.image)?.let { return it }
        }
        // 5) OCR
        if (!c.ocr.isNullOrEmpty()) {
            byOcr(c.ocr)?.let { return it }
        }
        return null
    }

    // ======================================================================
    // 策略一:节点 ID
    // ======================================================================
    private fun byNodeId(id: String): FoundElement? {
        val root = service.rootNode ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: return null
        val node = nodes.firstOrNull { it.isVisibleToUser } ?: nodes.firstOrNull() ?: return null
        return node.toFound("id")
    }

    // ======================================================================
    // 策略二:节点 Text / Desc(包含匹配)
    // ======================================================================
    private fun byNodeText(text: String?, desc: String?): FoundElement? {
        val root = service.rootNode ?: return null
        // text 优先用系统 API
        if (!text.isNullOrEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            nodes?.firstOrNull { it.isVisibleToUser }?.let { return it.toFound("text") }
        }
        // desc 需手动遍历
        val target = desc ?: text ?: return null
        return traverseFind(root) { node ->
            val d = node.contentDescription?.toString() ?: ""
            val t = node.text?.toString() ?: ""
            node.isVisibleToUser && (d.contains(target) || t.contains(target))
        }?.toFound("desc")
    }

    /** 深度遍历查找满足谓词的可见节点 */
    private fun traverseFind(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            traverseFind(node.getChild(i), predicate)?.let { return it }
        }
        return null
    }

    /** 节点 → 百分比坐标(取可视 bounds 中心) */
    private fun AccessibilityNodeInfo.toFound(strategy: String): FoundElement {
        val r = Rect()
        getBoundsInScreen(r)
        val xp = r.exactCenterX() / service.screenWidth
        val yp = r.exactCenterY() / service.screenHeight
        return FoundElement(xp.coerceIn(0f, 1f), yp.coerceIn(0f, 1f), this, strategy)
    }

    // ======================================================================
    // 策略三:图像模板匹配(纯 Kotlin 区域像素相似度,无 OpenCV)
    // ======================================================================
    private suspend fun byImage(templatePath: String): FoundElement? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            EngineLog.e("图像匹配需 Android 11+(takeScreenshot)")
            return null
        }
        val template = runCatching { BitmapFactory.decodeFile(templatePath) }.getOrNull()
        if (template == null) {
            EngineLog.e("模板图片读取失败: $templatePath")
            return null
        }
        val screen = service.captureBitmap() ?: return null
        try {
            return matchTemplate(screen, template)
        } finally {
            template.recycle()
            screen.recycle()
        }
    }

    /**
     * 滑窗模板匹配:在屏幕上以固定步长滑动模板窗口,
     * 在每个位置用采样网格对比像素颜色距离,取相似度最高且超过阈值者。
     * 返回命中区域中心的百分比坐标。
     */
    private fun matchTemplate(screen: Bitmap, template: Bitmap): FoundElement? {
        val sw = screen.width; val sh = screen.height
        val tw = template.width; val th = template.height
        if (tw > sw || th > sh) return null

        // 预采样模板的网格颜色
        val gx = min(IMAGE_SAMPLE_GRID, tw)
        val gy = min(IMAGE_SAMPLE_GRID, th)
        val tplColors = IntArray(gx * gy)
        for (j in 0 until gy) for (i in 0 until gx) {
            val px = i * (tw - 1) / (gx - 1).coerceAtLeast(1)
            val py = j * (th - 1) / (gy - 1).coerceAtLeast(1)
            tplColors[j * gx + i] = template.getPixel(px, py)
        }

        var bestSim = 0f
        var bestX = -1; var bestY = -1
        var y = 0
        while (y + th <= sh) {
            var x = 0
            while (x + tw <= sw) {
                val sim = similarityAt(screen, x, y, tw, th, tplColors, gx, gy)
                if (sim > bestSim) { bestSim = sim; bestX = x; bestY = y }
                x += IMAGE_SEARCH_STRIDE
            }
            y += IMAGE_SEARCH_STRIDE
        }

        if (bestSim >= IMAGE_SIM_THRESHOLD && bestX >= 0) {
            val cx = (bestX + tw / 2f) / sw
            val cy = (bestY + th / 2f) / sh
            EngineLog.i("图像匹配相似度=${fmt(bestSim)}")
            return FoundElement(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f), null, "image")
        }
        return null
    }

    /** 计算屏幕某窗口与模板采样网格的颜色相似度(0f~1f) */
    private fun similarityAt(
        screen: Bitmap, ox: Int, oy: Int, tw: Int, th: Int,
        tplColors: IntArray, gx: Int, gy: Int
    ): Float {
        var totalDiff = 0.0
        val n = gx * gy
        for (j in 0 until gy) for (i in 0 until gx) {
            val px = ox + i * (tw - 1) / (gx - 1).coerceAtLeast(1)
            val py = oy + j * (th - 1) / (gy - 1).coerceAtLeast(1)
            val sc = screen.getPixel(px, py)
            val tc = tplColors[j * gx + i]
            val dr = abs(((sc shr 16) and 0xFF) - ((tc shr 16) and 0xFF))
            val dg = abs(((sc shr 8) and 0xFF) - ((tc shr 8) and 0xFF))
            val db = abs((sc and 0xFF) - (tc and 0xFF))
            totalDiff += (dr + dg + db) / 3.0
        }
        val avgDiff = totalDiff / n           // 0~255
        return (1.0 - avgDiff / 255.0).toFloat()
    }

    // ======================================================================
    // 策略四:OCR(Google ML Kit)
    // ======================================================================
    private suspend fun byOcr(targetText: String): FoundElement? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            EngineLog.e("OCR 需 Android 11+(takeScreenshot)")
            return null
        }
        val screen = service.captureBitmap() ?: return null
        try {
            val rect = recognizeAndLocate(screen, targetText) ?: return null
            val cx = rect.exactCenterX() / screen.width
            val cy = rect.exactCenterY() / screen.height
            return FoundElement(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f), null, "ocr")
        } finally {
            screen.recycle()
        }
    }

    /** 运行 OCR,返回第一个包含 targetText 的文本块 bounding box */
    private suspend fun recognizeAndLocate(bitmap: Bitmap, targetText: String): Rect? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            ocrClient.process(image)
                .addOnSuccessListener { visionText ->
                    var hit: Rect? = null
                    outer@ for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            if (line.text.contains(targetText)) {
                                hit = line.boundingBox
                                break@outer
                            }
                        }
                    }
                    if (cont.isActive) cont.resume(hit)
                }
                .addOnFailureListener { e ->
                    EngineLog.e("OCR 失败: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
        }

    /**
     * 供 setVar/ocr Action 使用:读取整屏文字(可选只读命中行),返回识别文本。
     */
    suspend fun ocrReadAll(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        val screen = service.captureBitmap() ?: return ""
        return try {
            suspendCancellableCoroutine { cont ->
                ocrClient.process(InputImage.fromBitmap(screen, 0))
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                    .addOnFailureListener { if (cont.isActive) cont.resume("") }
            }
        } finally {
            screen.recycle()
        }
    }

    private fun fmt(v: Float) = String.format("%.3f", v)
}
