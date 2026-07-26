package com.script.engine

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局日志中心:内存环形缓冲 + 监听回调,供悬浮窗实时滚动显示。
 * 同时写入 logcat(TAG=AutoEngine)。
 */
object EngineLog {

    private const val TAG = "AutoEngine"
    private const val MAX = 300
    private val buffer = ArrayDeque<String>()
    private val listeners = mutableSetOf<(String) -> Unit>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun i(msg: String) = append("ℹ", msg, false)

    @Synchronized
    fun e(msg: String) = append("✗", msg, true)

    private fun append(prefix: String, msg: String, isError: Boolean) {
        val line = "${fmt.format(Date())} $prefix $msg"
        if (buffer.size >= MAX) buffer.removeFirst()
        buffer.addLast(line)
        if (isError) Log.e(TAG, msg) else Log.i(TAG, msg)
        // 回调放到主线程由监听方决定;这里直接同步通知
        listeners.toList().forEach { runCatching { it(line) } }
    }

    @Synchronized
    fun snapshot(): List<String> = buffer.toList()

    @Synchronized
    fun addListener(l: (String) -> Unit) { listeners.add(l) }

    @Synchronized
    fun removeListener(l: (String) -> Unit) { listeners.remove(l) }
}
