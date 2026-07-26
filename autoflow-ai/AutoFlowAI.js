/**
 * ============================================================================
 *  AutoFlow AI —— 基于 Auto.js 的 AI 自动化脚本管理器
 * ----------------------------------------------------------------------------
 *  功能总览:
 *    1. 启动自检:无障碍服务权限 + 悬浮窗权限,缺失时引导开启
 *    2. 主界面(ui.layout):标题栏 + 脚本列表(点击运行 / 长按菜单)+ 底部三按钮
 *    3. 本地脚本存储:/sdcard/AutoFlow/scripts/*.js,列表动态读取
 *    4. AI 生成脚本:输入任务描述 → 调用 Chat API → 保存为 .js → 刷新列表
 *    5. AI 实时模拟(核心):循环截图 → 视觉大模型 → 返回操作 JSON → 无障碍执行
 *    6. 所有网络请求走子线程(threads),不阻塞 UI
 *    7. 完整异常处理:无网络 / 权限不足 / AI 返回格式错误 / 单步超时
 *
 *  运行环境:Auto.js Pro 或支持 ui 的 Rhino 版 Auto.js
 *  作者:AutoFlow AI
 * ============================================================================
 */

"ui";

// ---------------------------------------------------------------------------
// 必要的 Android 类导入
// ---------------------------------------------------------------------------
importClass(android.content.Intent);
importClass(android.net.Uri);
importClass(android.provider.Settings);

// ===========================================================================
//  模块一:全局配置
// ===========================================================================
var CONFIG = {
    // 脚本存储根目录
    ROOT_DIR: "/sdcard/AutoFlow/",
    SCRIPTS_DIR: "/sdcard/AutoFlow/scripts/",
    CONFIG_FILE: "/sdcard/AutoFlow/config.json",

    // AI 接口配置(首次运行请在「设置」中填写,或直接改这里)
    // 兼容 OpenAI Chat Completions 协议;可换成任意兼容该协议的自定义网关
    AI_BASE_URL: "https://api.openai.com/v1",
    AI_API_KEY: "",                 // sk-xxxx
    AI_TEXT_MODEL: "gpt-4o-mini",   // 用于「AI 生成脚本」的文本模型
    AI_VISION_MODEL: "gpt-4o",      // 用于「AI 实时模拟」的多模态模型

    // 实时模拟参数
    LOOP_INTERVAL: 1000,            // 每次截图间隔(ms)
    STEP_TIMEOUT: 10000,            // 单步网络请求超时(ms)
    MAX_STEPS: 200,                 // 循环最大步数(兜底,防失控)
    CLICK_RANDOM_OFFSET: 8          // 坐标点击随机偏移半径(px),常规拟真
};

// 读取持久化配置(覆盖默认值)
function loadConfig() {
    try {
        if (files.exists(CONFIG.CONFIG_FILE)) {
            var saved = JSON.parse(files.read(CONFIG.CONFIG_FILE));
            Object.keys(saved).forEach(function (k) { CONFIG[k] = saved[k]; });
        }
    } catch (e) {
        log("读取配置失败:" + e);
    }
}

// 持久化可变配置项
function saveConfig() {
    try {
        files.ensureDir(CONFIG.CONFIG_FILE);
        var out = {
            AI_BASE_URL: CONFIG.AI_BASE_URL,
            AI_API_KEY: CONFIG.AI_API_KEY,
            AI_TEXT_MODEL: CONFIG.AI_TEXT_MODEL,
            AI_VISION_MODEL: CONFIG.AI_VISION_MODEL,
            LOOP_INTERVAL: CONFIG.LOOP_INTERVAL,
            STEP_TIMEOUT: CONFIG.STEP_TIMEOUT
        };
        files.write(CONFIG.CONFIG_FILE, JSON.stringify(out, null, 2));
    } catch (e) {
        log("保存配置失败:" + e);
    }
}

// ===========================================================================
//  模块二:权限自检与引导
// ===========================================================================

/**
 * 检查并引导开启无障碍服务。
 * @return {boolean} 是否已具备无障碍权限
 */
function ensureAccessibility() {
    if (auto.service != null) return true;
    toast("请开启无障碍服务");
    try {
        // auto() 会跳转无障碍设置页并阻塞等待,超时抛异常
        auto.waitFor();
        return auto.service != null;
    } catch (e) {
        // 手动跳转设置页兜底
        var intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return false;
    }
}

/**
 * 检查并引导开启悬浮窗权限。
 * @return {boolean} 是否已具备悬浮窗权限
 */
function ensureFloatyPermission() {
    if (floaty.checkPermission && floaty.checkPermission()) return true;
    // 老版本无 checkPermission,直接尝试请求
    if (floaty.requestPermission) {
        toast("请授予悬浮窗权限");
        floaty.requestPermission();
        return false;
    }
    return true;
}

/** 启动阶段的一次性权限自检(异步弹窗引导,不阻塞 UI 构建) */
function startupPermissionCheck() {
    threads.start(function () {
        if (auto.service == null) {
            var goOpen = confirmSync("需要无障碍权限", "AutoFlow AI 需要无障碍服务来执行自动化操作,现在去开启?");
            if (goOpen) ensureAccessibility();
        }
        if (!(floaty.checkPermission && floaty.checkPermission())) {
            var goFloaty = confirmSync("需要悬浮窗权限", "实时模拟需要悬浮窗显示日志,现在去开启?");
            if (goFloaty) ensureFloatyPermission();
        }
    });
}

/** 在子线程里安全地弹确认框(dialogs 在非 UI 线程需同步方式) */
function confirmSync(title, content) {
    return dialogs.build({
        title: title,
        content: content,
        positive: "去开启",
        negative: "稍后"
    }).show() ? true : false;
}

// ===========================================================================
//  模块三:脚本文件存储
// ===========================================================================

/** 确保存储目录存在 */
function ensureDirs() {
    files.ensureDir(CONFIG.SCRIPTS_DIR);
}

/**
 * 读取脚本目录下所有 .js 文件。
 * @return {Array<{name:string, path:string, size:number, mtime:number}>}
 */
function listScripts() {
    ensureDirs();
    var result = [];
    try {
        var names = files.listDir(CONFIG.SCRIPTS_DIR, function (name) {
            return name.endsWith(".js");
        });
        names.forEach(function (name) {
            var path = files.join(CONFIG.SCRIPTS_DIR, name);
            var f = new java.io.File(path);
            result.push({
                name: name,
                path: path,
                size: f.length(),
                mtime: f.lastModified()
            });
        });
        // 按修改时间倒序
        result.sort(function (a, b) { return b.mtime - a.mtime; });
    } catch (e) {
        log("读取脚本列表失败:" + e);
    }
    return result;
}

/**
 * 保存脚本内容为文件。
 * @param {string} baseName 文件名(不含扩展名)
 * @param {string} code 脚本内容
 * @return {string} 完整路径
 */
function saveScript(baseName, code) {
    ensureDirs();
    // 清洗文件名中的非法字符
    var safe = String(baseName).replace(/[\\/:*?"<>|]/g, "_").trim() || "script";
    var path = files.join(CONFIG.SCRIPTS_DIR, safe + ".js");
    // 同名则加时间戳
    if (files.exists(path)) {
        path = files.join(CONFIG.SCRIPTS_DIR, safe + "_" + Date.now() + ".js");
    }
    files.write(path, code);
    return path;
}

/** 删除脚本 */
function deleteScript(path) {
    try {
        files.remove(path);
        return true;
    } catch (e) {
        log("删除失败:" + e);
        return false;
    }
}

// ===========================================================================
//  模块四:AI 接口封装(均在子线程调用)
// ===========================================================================

/** 简单的网络可用性判断 */
function isNetworkAvailable() {
    try {
        importClass(android.net.ConnectivityManager);
        var cm = context.getSystemService(context.CONNECTIVITY_SERVICE);
        var info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    } catch (e) {
        return true; // 判断失败时不阻断,交给请求超时处理
    }
}

/**
 * 从 AI 返回文本中剥离 ```代码围栏,提取纯代码/纯 JSON。
 * @param {string} text
 * @return {string}
 */
function stripCodeFence(text) {
    if (!text) return "";
    var m = text.match(/```(?:javascript|js|json)?\s*([\s\S]*?)```/i);
    return (m ? m[1] : text).trim();
}

/**
 * 调用 Chat Completions(纯文本),用于「AI 生成脚本」。
 * @param {string} taskDesc 用户任务描述
 * @param {number} timeout 超时(ms)
 * @return {{ok:boolean, code?:string, error?:string}}
 */
function aiGenerateScript(taskDesc, timeout) {
    if (!CONFIG.AI_API_KEY) return { ok: false, error: "未配置 API Key,请到「设置」填写" };
    if (!isNetworkAvailable()) return { ok: false, error: "无网络连接" };

    var systemPrompt =
        "你是 Auto.js(Rhino 版)自动化脚本专家。根据用户的任务描述,生成一段可直接运行的 Auto.js 代码。" +
        "要求:1) 只输出代码,不要解释;2) 使用无障碍 API(如 text()/desc()/click()/swipe());" +
        "3) 关键步骤加中文注释;4) 加入必要的 waitFor 与异常保护;5) 不要包含 \"ui\"; 指令。";

    var body = {
        model: CONFIG.AI_TEXT_MODEL,
        messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: taskDesc }
        ],
        temperature: 0.2
    };

    try {
        var res = http.postJson(CONFIG.AI_BASE_URL + "/chat/completions", body, {
            headers: {
                "Authorization": "Bearer " + CONFIG.AI_API_KEY,
                "Content-Type": "application/json"
            },
            timeout: timeout || CONFIG.STEP_TIMEOUT
        });
        if (res.statusCode != 200) {
            return { ok: false, error: "HTTP " + res.statusCode + ":" + res.body.string() };
        }
        var data = res.body.json();
        var content = data.choices[0].message.content;
        var code = stripCodeFence(content);
        if (!code) return { ok: false, error: "AI 返回空内容" };
        return { ok: true, code: code };
    } catch (e) {
        return { ok: false, error: "请求异常:" + e };
    }
}

/**
 * 调用多模态模型,根据截图 + 任务描述决策下一步操作,用于「AI 实时模拟」。
 * 要求模型返回严格 JSON:
 *   { "action": "click|swipe|input|wait|done", "params": {...}, "reason": "..." }
 * @param {string} base64Img 截图 base64
 * @param {string} taskDesc 任务描述
 * @param {Array} history 已执行操作简史
 * @param {number} timeout 超时(ms)
 * @return {{ok:boolean, action?:object, error?:string}}
 */
function aiDecideAction(base64Img, taskDesc, history, timeout) {
    if (!CONFIG.AI_API_KEY) return { ok: false, error: "未配置 API Key" };
    if (!isNetworkAvailable()) return { ok: false, error: "无网络连接" };

    var systemPrompt =
        "你是手机自动化视觉代理。根据当前屏幕截图和任务目标,决定下一步操作。" +
        "必须只输出一个 JSON 对象,不要任何多余文字,格式:" +
        '{"action":"click|swipe|input|wait|done","params":{},"reason":"简述"}。' +
        "action 说明:" +
        "click 用 params.text(优先,按控件文本点击)或 params.x/params.y(坐标);" +
        "swipe 用 params.x1,y1,x2,y2,duration;" +
        "input 用 params.text(要输入的文字),可选 params.target(目标控件文本);" +
        "wait 用 params.ms;" +
        "done 表示任务已完成。坐标基于截图像素。";

    var userContent = [
        { type: "text", text: "任务目标:" + taskDesc + "\n已执行操作:" + JSON.stringify(history.slice(-5)) },
        { type: "image_url", image_url: { url: "data:image/png;base64," + base64Img } }
    ];

    var body = {
        model: CONFIG.AI_VISION_MODEL,
        messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: userContent }
        ],
        temperature: 0,
        max_tokens: 300
    };

    try {
        var res = http.postJson(CONFIG.AI_BASE_URL + "/chat/completions", body, {
            headers: {
                "Authorization": "Bearer " + CONFIG.AI_API_KEY,
                "Content-Type": "application/json"
            },
            timeout: timeout || CONFIG.STEP_TIMEOUT
        });
        if (res.statusCode != 200) {
            return { ok: false, error: "HTTP " + res.statusCode };
        }
        var data = res.body.json();
        var content = data.choices[0].message.content;
        var jsonText = stripCodeFence(content);
        var action;
        try {
            action = JSON.parse(jsonText);
        } catch (parseErr) {
            return { ok: false, error: "AI 返回非法 JSON:" + content };
        }
        if (!action || !action.action) {
            return { ok: false, error: "AI 返回缺少 action 字段" };
        }
        return { ok: true, action: action };
    } catch (e) {
        return { ok: false, error: "请求异常:" + e };
    }
}

// ===========================================================================
//  模块五:无障碍操作执行器
// ===========================================================================

/** 带随机偏移的坐标点击(常规拟真,避免每次点同一像素) */
function humanClick(x, y) {
    var r = CONFIG.CLICK_RANDOM_OFFSET;
    var dx = Math.round((Math.random() * 2 - 1) * r);
    var dy = Math.round((Math.random() * 2 - 1) * r);
    return click(x + dx, y + dy);
}

/**
 * 执行一个 AI 决策的操作。
 * @param {object} action { action, params }
 * @param {function} onLog 日志回调
 * @return {boolean} 是否为「任务完成」
 */
function executeAction(action, onLog) {
    var p = action.params || {};
    switch (action.action) {
        case "click":
            if (p.text) {
                onLog("点击控件:" + p.text);
                var node = text(p.text).findOnce() || desc(p.text).findOnce();
                if (node) {
                    // 优先点击可点击父节点,否则点中心坐标
                    if (!clickNode(node)) {
                        var b = node.bounds();
                        humanClick(b.centerX(), b.centerY());
                    }
                } else {
                    onLog("未找到控件「" + p.text + "」,跳过");
                }
            } else if (p.x != null && p.y != null) {
                onLog("点击坐标:(" + p.x + "," + p.y + ")");
                humanClick(p.x, p.y);
            } else {
                onLog("click 缺少参数,跳过");
            }
            break;

        case "swipe":
            onLog("滑动:(" + p.x1 + "," + p.y1 + ")→(" + p.x2 + "," + p.y2 + ")");
            swipe(p.x1, p.y1, p.x2, p.y2, p.duration || 400);
            break;

        case "input":
            onLog("输入文字:" + p.text);
            var target = p.target ? (text(p.target).findOnce() || desc(p.target).findOnce()) : null;
            if (target) {
                target.setText(p.text);
            } else {
                // 无指定目标时,尝试聚焦第一个可编辑框
                var edit = className("android.widget.EditText").findOnce();
                if (edit) edit.setText(p.text);
                else setText(p.text);
            }
            break;

        case "wait":
            onLog("等待 " + (p.ms || 1000) + "ms");
            sleep(p.ms || 1000);
            break;

        case "done":
            onLog("✅ AI 判定任务完成");
            return true;

        default:
            onLog("未知操作类型:" + action.action + ",跳过");
    }
    return false;
}

/** 尝试点击节点自身或其可点击祖先 */
function clickNode(node) {
    var n = node;
    while (n != null) {
        if (n.isClickable()) return n.click();
        n = n.parent();
    }
    return false;
}

// ===========================================================================
//  模块六:AI 实时模拟(核心循环)
// ===========================================================================

var Simulator = {
    running: false,
    thread: null,
    floatyWin: null,

    /** 启动实时模拟 */
    start: function (taskDesc) {
        if (this.running) { toast("已在运行中"); return; }
        if (!ensureAccessibility()) { toast("缺少无障碍权限"); return; }

        // 申请截图权限(必须在 UI 线程或主线程发起)
        if (!requestScreenCapture(false)) {
            toast("截图权限被拒绝");
            return;
        }

        this.running = true;
        this.showFloaty();
        var self = this;

        // 核心循环放子线程,避免阻塞 UI
        this.thread = threads.start(function () {
            var history = [];
            var steps = 0;
            self.appendLog("▶ 任务开始:" + taskDesc);

            while (self.running && steps < CONFIG.MAX_STEPS) {
                steps++;
                try {
                    // 1) 截图
                    var img = captureScreen();
                    if (!img) { self.appendLog("截图失败,重试"); sleep(CONFIG.LOOP_INTERVAL); continue; }
                    var base64 = images.toBase64(img, "png", 60);
                    img.recycle();

                    // 2) 交给 AI 决策(带超时)
                    self.appendLog("[" + steps + "] 分析屏幕中…");
                    var res = aiDecideAction(base64, taskDesc, history, CONFIG.STEP_TIMEOUT);

                    if (!res.ok) {
                        // AI 返回格式错误 / 网络异常 → 记录并跳过本步
                        self.appendLog("⚠ " + res.error);
                        sleep(CONFIG.LOOP_INTERVAL);
                        continue;
                    }

                    // 3) 执行操作
                    var action = res.action;
                    self.appendLog("动作:" + action.action + (action.reason ? "(" + action.reason + ")" : ""));
                    var done = executeAction(action, function (msg) { self.appendLog("  " + msg); });
                    history.push({ action: action.action, params: action.params });

                    if (done) {
                        self.appendLog("🎉 全部完成");
                        break;
                    }
                } catch (e) {
                    self.appendLog("✗ 步骤异常:" + e);
                }
                sleep(CONFIG.LOOP_INTERVAL);
            }

            if (steps >= CONFIG.MAX_STEPS) self.appendLog("已达最大步数,自动停止");
            self.stop();
        });
    },

    /** 停止实时模拟 */
    stop: function () {
        if (!this.running) return;
        this.running = false;
        try { if (this.thread) this.thread.interrupt(); } catch (e) {}
        this.thread = null;
        this.appendLog("⏹ 已停止");
        // 延迟关闭悬浮窗,让用户看到最后日志
        var self = this;
        setTimeout(function () { self.hideFloaty(); }, 1500);
    },

    /** 显示悬浮日志窗 */
    showFloaty: function () {
        if (this.floatyWin) return;
        try {
            var w = floaty.rawWindow(
                <vertical bg="#cc000000" padding="8" w="260">
                    <horizontal>
                        <text text="AutoFlow 实时模拟" textColor="#ffffff" textSize="13sp" layout_weight="1"/>
                        <text id="stopBtn" text="停止" textColor="#ff5252" textSize="13sp" padding="4 0"/>
                    </horizontal>
                    <ScrollView h="180">
                        <text id="logText" textColor="#00e676" textSize="11sp" text=""/>
                    </ScrollView>
                </vertical>
            );
            w.setPosition(20, 120);
            var self = this;
            w.stopBtn.click(function () { self.stop(); });
            this.floatyWin = w;
        } catch (e) {
            log("悬浮窗创建失败(检查悬浮窗权限):" + e);
        }
    },

    hideFloaty: function () {
        if (this.floatyWin) {
            try { this.floatyWin.close(); } catch (e) {}
            this.floatyWin = null;
        }
    },

    _logBuf: [],
    /** 追加日志(线程安全地更新悬浮窗 UI) */
    appendLog: function (msg) {
        log(msg);
        this._logBuf.push(msg);
        if (this._logBuf.length > 30) this._logBuf.shift();
        var self = this;
        if (this.floatyWin) {
            ui.run(function () {
                try { self.floatyWin.logText.setText(self._logBuf.join("\n")); } catch (e) {}
            });
        }
    }
};

// ===========================================================================
//  模块七:主界面(ui.layout)
// ===========================================================================

ui.layout(
    <vertical h="*">
        {/* 顶部标题栏(主页面与设置页共用,标题动态切换) */}
        <appbar>
            <toolbar id="toolbar" title="AutoFlow AI" subtitle="AI 自动化脚本管理器"/>
        </appbar>

        {/* 页面容器:主页面与设置页为同层 frame,切换 visibility 实现二级页面 */}
        <frame layout_weight="1">

            {/* ============ 主页面 ============ */}
            <vertical id="mainPage" h="*">
                <frame layout_weight="1">
                    <list id="scriptList">
                        <horizontal padding="16 12" gravity="center_vertical" bg="?selectableItemBackground">
                            <vertical layout_weight="1">
                                <text text="{{name}}" textColor="#212121" textSize="15sp"/>
                                <text text="{{sizeText}}" textColor="#9e9e9e" textSize="12sp" marginTop="2"/>
                            </vertical>
                            <text text="▶ 运行" textColor="#2a78d6" textSize="14sp" padding="8 4"/>
                        </horizontal>
                    </list>
                    {/* 空状态 */}
                    <vertical id="emptyView" gravity="center" visibility="gone">
                        <text text="还没有脚本" textColor="#9e9e9e" textSize="16sp"/>
                        <text text="点击下方「AI 生成」或「添加脚本」开始" textColor="#bdbdbd" textSize="13sp" marginTop="6"/>
                    </vertical>
                </frame>

                {/* 底部按钮 */}
                <horizontal bg="#f5f6f8" padding="8">
                    <button id="btnAdd" text="添加脚本" style="Widget.AppCompat.Button.Borderless.Colored" layout_weight="1"/>
                    <button id="btnAI" text="AI 生成" style="Widget.AppCompat.Button.Borderless.Colored" layout_weight="1"/>
                    <button id="btnSim" text="AI 实时模拟" style="Widget.AppCompat.Button.Borderless.Colored" layout_weight="1"/>
                    <button id="btnSettings" text="设置" style="Widget.AppCompat.Button.Borderless.Colored" layout_weight="1"/>
                </horizontal>
            </vertical>

            {/* ============ 设置二级页面 ============ */}
            <vertical id="settingsPage" h="*" bg="#ffffff" visibility="gone">
                <ScrollView layout_weight="1">
                    <vertical padding="16">
                        <text text="AI 接口配置" textColor="#2a78d6" textSize="13sp" textStyle="bold"/>

                        <text text="接口地址(Base URL)" textColor="#5f6672" textSize="12sp" marginTop="16"/>
                        <input id="setBaseUrl" hint="https://api.openai.com/v1" textSize="14sp" singleLine="true"/>

                        <text text="API Key" textColor="#5f6672" textSize="12sp" marginTop="12"/>
                        <input id="setApiKey" hint="sk-..." textSize="14sp" singleLine="true" password="true"/>
                        <checkbox id="setShowKey" text="显示密钥" textSize="12sp" marginTop="4"/>

                        <text text="文本模型(用于 AI 生成脚本)" textColor="#5f6672" textSize="12sp" marginTop="12"/>
                        <input id="setTextModel" hint="gpt-4o-mini" textSize="14sp" singleLine="true"/>

                        <text text="视觉模型(用于 AI 实时模拟)" textColor="#5f6672" textSize="12sp" marginTop="12"/>
                        <input id="setVisionModel" hint="gpt-4o" textSize="14sp" singleLine="true"/>

                        <text text="实时模拟参数" textColor="#2a78d6" textSize="13sp" textStyle="bold" marginTop="24"/>

                        <text text="截图循环间隔(毫秒)" textColor="#5f6672" textSize="12sp" marginTop="16"/>
                        <input id="setLoopInterval" hint="1000" textSize="14sp" singleLine="true" inputType="number"/>

                        <text text="单步网络超时(毫秒)" textColor="#5f6672" textSize="12sp" marginTop="12"/>
                        <input id="setStepTimeout" hint="10000" textSize="14sp" singleLine="true" inputType="number"/>

                        <text id="setHint" text="" textColor="#e34948" textSize="12sp" marginTop="12"/>
                    </vertical>
                </ScrollView>

                {/* 设置页底部操作栏 */}
                <horizontal bg="#f5f6f8" padding="8">
                    <button id="btnCancelSettings" text="返回" style="Widget.AppCompat.Button.Borderless" layout_weight="1"/>
                    <button id="btnSaveSettings" text="保存" style="Widget.AppCompat.Button.Borderless.Colored" layout_weight="1"/>
                </horizontal>
            </vertical>

        </frame>
    </vertical>
);

// ---------------------------------------------------------------------------
// 列表数据渲染
// ---------------------------------------------------------------------------
function refreshList() {
    var scripts = listScripts();
    scripts.forEach(function (s) {
        s.sizeText = (s.size / 1024).toFixed(1) + " KB · " + formatTime(s.mtime);
    });
    ui.scriptList.setDataSource(scripts);
    ui.emptyView.attr("visibility", scripts.length ? "gone" : "visible");
}

function formatTime(ms) {
    var d = new Date(ms);
    function p(n) { return n < 10 ? "0" + n : n; }
    return (d.getMonth() + 1) + "/" + d.getDate() + " " + p(d.getHours()) + ":" + p(d.getMinutes());
}

// ---------------------------------------------------------------------------
// 列表交互:点击运行 / 长按菜单
// ---------------------------------------------------------------------------
ui.scriptList.on("item_click", function (item) {
    runScript(item);
});

ui.scriptList.on("item_long_click", function (e, item) {
    var actions = ["运行", "查看代码", "重命名", "删除"];
    dialogs.select("操作:" + item.name, actions).then(function (idx) {
        switch (idx) {
            case 0: runScript(item); break;
            case 1: viewCode(item); break;
            case 2: renameScript(item); break;
            case 3: confirmDelete(item); break;
        }
    });
    return true; // 消费长按事件
});

/** 运行脚本 */
function runScript(item) {
    if (!ensureAccessibility()) { toast("请先开启无障碍权限"); return; }
    try {
        engines.execScriptFile(item.path);
        toast("已启动:" + item.name);
    } catch (e) {
        dialogs.alert("运行失败", String(e));
    }
}

/** 查看代码 */
function viewCode(item) {
    try {
        var code = files.read(item.path);
        dialogs.build({
            title: item.name,
            content: code.length > 4000 ? code.substring(0, 4000) + "\n…(已截断)" : code,
            positive: "关闭"
        }).show();
    } catch (e) {
        toast("读取失败:" + e);
    }
}

/** 重命名 */
function renameScript(item) {
    dialogs.rawInput("新文件名(不含 .js)", item.name.replace(/\.js$/, "")).then(function (name) {
        if (!name) return;
        var code = files.read(item.path);
        deleteScript(item.path);
        saveScript(name, code);
        refreshList();
        toast("已重命名");
    });
}

/** 删除确认 */
function confirmDelete(item) {
    dialogs.build({
        title: "删除脚本",
        content: "确定删除「" + item.name + "」吗?此操作不可恢复。",
        positive: "删除",
        negative: "取消"
    }).on("positive", function () {
        if (deleteScript(item.path)) { refreshList(); toast("已删除"); }
    }).show();
}

// ---------------------------------------------------------------------------
// 底部按钮:添加脚本
// ---------------------------------------------------------------------------
ui.btnAdd.click(function () {
    dialogs.rawInput("脚本文件名(不含 .js)", "新脚本").then(function (name) {
        if (!name) return;
        var template =
            "// " + name + "\n" +
            "// 由 AutoFlow AI 创建于 " + new Date().toLocaleString() + "\n\n" +
            "toast(\"Hello from " + name + "\");\n";
        var path = saveScript(name, template);
        refreshList();
        toast("已创建:" + files.getName(path));
    });
});

// ---------------------------------------------------------------------------
// 底部按钮:AI 生成脚本
// ---------------------------------------------------------------------------
ui.btnAI.click(function () {
    dialogs.rawInput("描述你想要的自动化任务", "").then(function (desc) {
        if (!desc || !desc.trim()) return;

        var progress = dialogs.build({
            title: "AI 生成中",
            content: "正在请求模型,请稍候…",
            cancelable: false
        }).show();

        // 网络请求走子线程,完成后回到 UI 线程更新
        threads.start(function () {
            var result = aiGenerateScript(desc.trim(), CONFIG.STEP_TIMEOUT * 3);
            ui.run(function () {
                progress.dismiss();
                if (!result.ok) {
                    dialogs.alert("生成失败", result.error);
                    return;
                }
                // 让用户确认文件名后保存
                dialogs.rawInput("保存为(不含 .js)", "ai_" + Date.now()).then(function (name) {
                    if (!name) return;
                    var path = saveScript(name, result.code);
                    refreshList();
                    toast("已生成并保存:" + files.getName(path));
                });
            });
        });
    });
});

// ---------------------------------------------------------------------------
// 底部按钮:AI 实时模拟
// ---------------------------------------------------------------------------
ui.btnSim.click(function () {
    if (Simulator.running) {
        Simulator.stop();
        ui.btnSim.setText("AI 实时模拟");
        return;
    }
    dialogs.rawInput("描述要 AI 实时执行的任务目标", "").then(function (desc) {
        if (!desc || !desc.trim()) return;
        if (!CONFIG.AI_API_KEY) { dialogs.alert("提示", "请先到「设置」填写 API Key"); return; }
        // 请求截图权限需在前台发起
        Simulator.start(desc.trim());
        ui.btnSim.setText("停止模拟");
    });
});

// ---------------------------------------------------------------------------
// 底部按钮:设置
// ---------------------------------------------------------------------------
ui.btnSettings.click(function () {
    showSettings();
});

// 设置页内:显示/隐藏密钥
ui.setShowKey.on("check", function (checked) {
    ui.setApiKey.setInputType(checked
        ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    // 保持光标在末尾
    ui.setApiKey.setSelection(ui.setApiKey.getText().length());
});

// 设置页:返回(不保存)
ui.btnCancelSettings.click(function () {
    hideSettings();
});

// 设置页:保存
ui.btnSaveSettings.click(function () {
    var baseUrl = ui.setBaseUrl.getText().toString().trim();
    var key = ui.setApiKey.getText().toString().trim();
    var textModel = ui.setTextModel.getText().toString().trim();
    var visionModel = ui.setVisionModel.getText().toString().trim();
    var loopInterval = parseInt(ui.setLoopInterval.getText().toString(), 10);
    var stepTimeout = parseInt(ui.setStepTimeout.getText().toString(), 10);

    // 基本校验
    if (baseUrl && !/^https?:\/\//i.test(baseUrl)) {
        ui.setHint.setText("接口地址需以 http:// 或 https:// 开头");
        return;
    }
    if (loopInterval && (isNaN(loopInterval) || loopInterval < 200)) {
        ui.setHint.setText("循环间隔不能小于 200ms");
        return;
    }
    if (stepTimeout && (isNaN(stepTimeout) || stepTimeout < 1000)) {
        ui.setHint.setText("单步超时不能小于 1000ms");
        return;
    }
    ui.setHint.setText("");

    // 写回 CONFIG(空值保留原默认)
    if (baseUrl) CONFIG.AI_BASE_URL = baseUrl;
    CONFIG.AI_API_KEY = key;
    if (textModel) CONFIG.AI_TEXT_MODEL = textModel;
    if (visionModel) CONFIG.AI_VISION_MODEL = visionModel;
    if (!isNaN(loopInterval) && loopInterval) CONFIG.LOOP_INTERVAL = loopInterval;
    if (!isNaN(stepTimeout) && stepTimeout) CONFIG.STEP_TIMEOUT = stepTimeout;

    saveConfig();
    toast("设置已保存");
    hideSettings();
});

/** 打开设置二级页面:回填当前配置并切换可见性 */
function showSettings() {
    ui.setBaseUrl.setText(CONFIG.AI_BASE_URL || "");
    ui.setApiKey.setText(CONFIG.AI_API_KEY || "");
    ui.setTextModel.setText(CONFIG.AI_TEXT_MODEL || "");
    ui.setVisionModel.setText(CONFIG.AI_VISION_MODEL || "");
    ui.setLoopInterval.setText(String(CONFIG.LOOP_INTERVAL));
    ui.setStepTimeout.setText(String(CONFIG.STEP_TIMEOUT));
    ui.setShowKey.setChecked(false);
    ui.setHint.setText("");

    ui.mainPage.attr("visibility", "gone");
    ui.settingsPage.attr("visibility", "visible");
    ui.toolbar.attr("title", "设置");
    ui.toolbar.attr("subtitle", "AI 接口与实时模拟参数");
    settingsVisible = true;
}

/** 返回主页面 */
function hideSettings() {
    ui.settingsPage.attr("visibility", "gone");
    ui.mainPage.attr("visibility", "visible");
    ui.toolbar.attr("title", "AutoFlow AI");
    ui.toolbar.attr("subtitle", "AI 自动化脚本管理器");
    settingsVisible = false;
}

// ===========================================================================
//  模块八:启动流程
// ===========================================================================
var settingsVisible = false; // 当前是否停留在设置二级页面

// 硬件返回键:在设置页时返回主页面,而不是退出应用
ui.emitter.on("back_pressed", function (e) {
    if (settingsVisible) {
        hideSettings();
        e.consumed = true;
    }
});

(function main() {
    loadConfig();      // 读取持久化配置
    ensureDirs();      // 建目录
    refreshList();     // 渲染列表
    startupPermissionCheck(); // 权限自检引导(异步)

    // 首次运行且未配置 Key 时给个提示
    if (!CONFIG.AI_API_KEY) {
        toast("提示:AI 功能需先到「设置」填写 API Key");
    }
})();

// 页面销毁时确保停止模拟、释放悬浮窗
ui.emitter.on("resume", function () {
    // 从后台返回时刷新列表(可能有新脚本)
    refreshList();
});
events.on("exit", function () {
    Simulator.stop();
});
