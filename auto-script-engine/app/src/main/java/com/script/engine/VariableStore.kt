package com.script.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 变量存储与占位替换。
 * setVar("result", "xxx") 存值;文本中的 {{$result}} 会被替换为对应值。
 */
class VariableStore {

    private val vars = ConcurrentHashMap<String, String>()

    fun set(name: String, value: String) {
        vars[normalize(name)] = value
        EngineLog.i("变量 \$${normalize(name)} = $value")
    }

    fun get(name: String): String = vars[normalize(name)] ?: ""

    fun has(name: String): Boolean = vars.containsKey(normalize(name))

    /** 去掉可能带的 $ 前缀 */
    private fun normalize(name: String) = name.removePrefix("$")

    /**
     * 将文本中的 {{$var}} 占位替换为实际值。
     * 例如 "订单号:{{$result}}" → "订单号:A123"
     */
    fun resolve(input: String?): String {
        if (input.isNullOrEmpty()) return input ?: ""
        val regex = Regex("\\{\\{\\s*\\$?([a-zA-Z0-9_]+)\\s*}}")
        return regex.replace(input) { m -> get(m.groupValues[1]) }
    }

    fun clear() = vars.clear()
}
