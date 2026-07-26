package com.script.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * WorkManager 定时封装。
 *   - scheduleInterval:每隔固定毫秒周期执行(WorkManager 周期下限 15 分钟)
 *   - scheduleOnceAfter:延迟一次性执行(可用于"具体时刻"—传入距该时刻的毫秒)
 * 触发时由 ScriptWorker 拉起引擎运行指定脚本。
 */
object SchedulerManager {

    private const val UNIQUE_PERIODIC = "auto_engine_periodic"
    private const val UNIQUE_ONCE = "auto_engine_once"
    const val KEY_SCRIPT = "script_path"

    fun scheduleInterval(context: Context, scriptPath: String, intervalMs: Long) {
        val minutes = (intervalMs / 60000L).coerceAtLeast(15) // WorkManager 周期任务下限 15 分钟
        val req = PeriodicWorkRequestBuilder<ScriptWorker>(minutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_SCRIPT to scriptPath))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req
        )
        EngineLog.i("已登记周期任务:每 $minutes 分钟运行 $scriptPath")
    }

    fun scheduleOnceAfter(context: Context, scriptPath: String, delayMs: Long) {
        val req = OneTimeWorkRequestBuilder<ScriptWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_SCRIPT to scriptPath))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONCE, ExistingWorkPolicy.REPLACE, req
        )
        EngineLog.i("已登记定时任务:${delayMs}ms 后运行 $scriptPath")
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_ONCE)
    }
}

/**
 * 定时触发的 Worker:启动保活服务并运行脚本。
 * 注意:实际执行依赖无障碍服务已开启;未开启时记录日志并结束。
 */
class ScriptWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scriptPath = inputData.getString(SchedulerManager.KEY_SCRIPT)
            ?: return Result.failure()

        if (AutoService.instance == null) {
            EngineLog.e("定时触发但无障碍服务未开启,跳过本次")
            return Result.retry()
        }

        KeepAliveService.start(applicationContext)
        val engine = ScriptEngine(applicationContext)
        engine.start(scriptPath)
        EngineLog.i("定时任务已拉起脚本: $scriptPath")
        return Result.success()
    }
}
