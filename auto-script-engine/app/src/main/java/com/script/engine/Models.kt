package com.script.engine

/**
 * 查找条件:一个 condition 可包含多种候选定位方式,
 * UnifiedElementFinder 按 id → text/desc → image → ocr → coordinate 的优先级尝试。
 * 坐标 x/y 统一为屏幕百分比(0f~1f)。
 */
data class FindCondition(
    val id: String? = null,          // AccessibilityNode 的 viewIdResourceName
    val text: String? = null,        // 节点 text(包含匹配)
    val desc: String? = null,        // 节点 contentDescription
    val image: String? = null,       // 模板图片路径(/sdcard/Scripts/templates/xxx.png)
    val ocr: String? = null,         // 需要 OCR 识别命中的文字
    val x: Float? = null,            // 直接坐标(百分比)
    val y: Float? = null
)

/**
 * 通用 Action。用单一数据类 + type 字段承载所有动作类型,
 * 便于 Gson 直接解析 JSON;嵌套动作用 actions/thenActions/elseActions。
 */
data class Action(
    val type: String,                       // click/longClick/swipe/input/wait/loop/if/screenshot/globalAction/setVar/ocr/launchApp
    val condition: FindCondition? = null,   // 查找目标(click/longClick/input/wait/if 用)
    val text: String? = null,               // input 的文本 / setVar 的值(支持 {{$var}} 占位)
    val clear: Boolean = false,             // input 是否先清空原内容
    val direction: String? = null,          // swipe 方向 up/down/left/right
    val start: List<Float>? = null,         // swipe 起点 [x,y] 百分比
    val end: List<Float>? = null,           // swipe 终点 [x,y] 百分比
    val duration: Long = 300,               // swipe/手势时长(ms)
    val ms: Long? = null,                   // wait 固定等待毫秒
    val times: Int? = null,                 // loop 固定次数
    val whileCondition: FindCondition? = null, // loop 的 while 条件(元素存在则继续)
    val actions: List<Action>? = null,      // loop 子指令
    val thenActions: List<Action>? = null,  // if 为真执行
    val elseActions: List<Action>? = null,  // if 为假执行
    val varName: String? = null,            // setVar/ocr 存储的变量名;if 变量判断
    val equals: String? = null,             // if 变量等值判断
    val globalAction: String? = null,       // back/home/recents/notifications
    val packageName: String? = null,        // launchApp 目标包名
    val path: String? = null,               // screenshot 保存路径
    val timeout: Long = 8000,               // 查找超时(ms)
    val retry: Int = 2,                     // 失败重试次数
    val retryInterval: Long = 500           // 重试间隔(ms)
)

/** 查找结果:统一返回百分比坐标(0f~1f)+ 命中节点(若为节点查找)+ 命中策略名 */
data class FoundElement(
    val xPercent: Float,
    val yPercent: Float,
    val node: android.view.accessibility.AccessibilityNodeInfo? = null,
    val strategy: String = ""
)

/** 元素未找到异常(超时抛出) */
class ElementNotFoundException(condition: FindCondition) :
    Exception("未找到元素: $condition")

/** 引擎运行状态 */
enum class EngineState { IDLE, RUNNING, PAUSED, ERROR }
