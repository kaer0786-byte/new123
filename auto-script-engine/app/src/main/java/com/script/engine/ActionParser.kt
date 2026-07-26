package com.script.engine

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * JSON → Action 对象。
 * 脚本文件格式:顶层为 { "name": "...", "actions": [ {Action}, ... ] }
 * 也兼容顶层直接是 [ {Action}, ... ] 数组。
 */
object ActionParser {

    private val gson: Gson = GsonBuilder().setLenient().create()

    data class Script(val name: String = "", val actions: List<Action> = emptyList())

    /** 从文件解析 */
    fun parseFile(path: String): Script {
        val text = File(path).readText()
        return parseString(text)
    }

    /** 从字符串解析,自动兼容对象/数组两种顶层结构 */
    fun parseString(json: String): Script {
        val trimmed = json.trim()
        return if (trimmed.startsWith("[")) {
            val type = object : TypeToken<List<Action>>() {}.type
            Script(name = "unnamed", actions = gson.fromJson(trimmed, type))
        } else {
            gson.fromJson(trimmed, Script::class.java) ?: Script()
        }
    }
}
