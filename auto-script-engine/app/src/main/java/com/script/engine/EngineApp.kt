package com.script.engine

import android.app.Application

/** Application 入口(可在此做全局初始化) */
class EngineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EngineLog.i("AutoScriptEngine 已启动")
    }
}
