package com.script.engine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主入口:加载并调度执行脚本。
 * - 顺序执行顶层 actions;每步前检查暂停/停止;
 * - 断点续跑:SharedPreferences 记录当前索引,异常/重启后 resume() 可从断点继续。
 */
class ScriptEngine(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("engine_state", Context.MODE_PRIVATE)
    private val vars = VariableStore()

    @Volatile var state: EngineState = EngineState.IDLE
        private set

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var onStateChanged: ((EngineState) -> Unit)? = null

    private fun setState(s: EngineState) {
        state = s
        EngineLog.i("引擎状态: $s")
        onStateChanged?.invoke(s)
    }

    /** 从头运行脚本文件 */
    fun start(scriptPath: String) = launchRun(scriptPath, fromIndex = 0)

    /** 从断点继续(读取上次记录的索引) */
    fun resume(scriptPath: String) {
        val idx = prefs.getInt(keyOf(scriptPath), 0)
        EngineLog.i("断点续跑,从第 $idx 步开始")
        launchRun(scriptPath, fromIndex = idx)
    }

    fun pause() { if (state == EngineState.RUNNING) setState(EngineState.PAUSED) }
    fun resumeFromPause() { if (state == EngineState.PAUSED) setState(EngineState.RUNNING) }

    fun stop() {
        job?.cancel()
        job = null
        setState(EngineState.IDLE)
    }

    private fun launchRun(scriptPath: String, fromIndex: Int) {
        if (state == EngineState.RUNNING) { EngineLog.e("已有脚本在运行"); return }
        val service = AutoService.instance
        if (service == null) { EngineLog.e("无障碍服务未开启,无法运行"); return }

        val finder = UnifiedElementFinder(service)
        val executor = ActionExecutor(service, finder, vars)

        job = scope.launch {
            try {
                val script = ActionParser.parseFile(scriptPath)
                EngineLog.i("加载脚本「${script.name}」,共 ${script.actions.size} 步")
                setState(EngineState.RUNNING)

                for (i in fromIndex until script.actions.size) {
                    // 暂停等待
                    while (state == EngineState.PAUSED) delay(200)
                    if (state == EngineState.IDLE) { EngineLog.i("已停止"); return@launch }

                    saveCheckpoint(scriptPath, i)
                    val action = script.actions[i]
                    EngineLog.i("▶ [${i + 1}/${script.actions.size}] ${action.type}")
                    executor.execute(action)
                }

                clearCheckpoint(scriptPath)
                EngineLog.i("✅ 脚本执行完成")
                setState(EngineState.IDLE)
            } catch (e: Exception) {
                EngineLog.e("引擎异常终止: ${e.message}")
                setState(EngineState.ERROR)
            }
        }
    }

    private fun saveCheckpoint(path: String, index: Int) =
        prefs.edit().putInt(keyOf(path), index).apply()

    private fun clearCheckpoint(path: String) =
        prefs.edit().remove(keyOf(path)).apply()

    private fun keyOf(path: String) = "ckpt_" + path.hashCode()
}
