# AutoScriptEngine —— 全能型 JSON 驱动自动化引擎

Kotlin + Coroutines + AccessibilityService 实现的通用自动化引擎。用户无需改代码,只需编写 JSON 指令清单,引擎即可执行点击、滑动、输入、OCR、图像匹配、循环、条件判断等操作。

> ⚠️ 用途声明:本项目是通用自动化 / RPA / 无障碍辅助框架(与 Auto.js、Appium 同类)。请仅用于你有权操作的设备与账号、自动化测试、无障碍辅助等合法场景,并遵守目标 App 的用户协议与当地法律。不含针对任何平台风控系统的对抗逻辑。

## 项目结构

```
app/
├── build.gradle.kts                     # 依赖:ML Kit OCR / Coroutines / WorkManager / Gson
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml              # 无障碍/悬浮窗/电池优化/前台服务权限
    ├── res/xml/accessibility_service_config.xml
    ├── res/values/strings.xml
    └── java/com/script/engine/
        ├── Models.kt                    # Action / FindCondition / FoundElement 数据模型
        ├── ActionParser.kt             # JSON → Action
        ├── UnifiedElementFinder.kt     # 【核心】ID→Text/Desc→图像→OCR 多模态查找
        ├── ActionExecutor.kt           # 【核心】执行点击/滑动/输入/循环/判断 + 重试 + 弹窗清扫
        ├── ScriptEngine.kt             # 主入口:调度、暂停/停止、断点续跑
        ├── AutoService.kt              # AccessibilityService:手势 + 截图
        ├── FloatingController.kt       # 悬浮控制面板(脚本选择/启停/日志)
        ├── SchedulerManager.kt         # WorkManager 定时封装 + ScriptWorker
        ├── KeepAliveService.kt         # 前台保活 + WakeLock
        ├── EngineApp.kt / MainActivity.kt
        └── EngineLog.kt / VariableStore.kt
scripts/
└── demo_wechat.json                    # 演示:打开微信→发现→朋友圈→循环滑动→截图
```

## 快速开始

1. Android Studio 打开本目录,连真机(Android 11+ 完整支持图像/OCR)构建安装。
2. 打开 App,依次点①②③授权,再点④显示悬浮面板。
3. 把脚本放到 `/sdcard/Scripts/*.json`,模板图片放 `/sdcard/Scripts/templates/`。
4. 悬浮面板选脚本 → 启动。

### 用 ADB 授权(免手动点设置)

```bash
# 1) 推送示例脚本到设备
adb shell mkdir -p /sdcard/Scripts/templates /sdcard/Scripts/shots
adb push scripts/demo_wechat.json /sdcard/Scripts/

# 2) 开启无障碍服务(授权本应用的 AutoService)
adb shell settings put secure enabled_accessibility_services \
  com.script.engine/com.script.engine.AutoService
adb shell settings put secure accessibility_enabled 1

# 3) 授予悬浮窗权限
adb shell appops set com.script.engine SYSTEM_ALERT_WINDOW allow

# 4) 忽略电池优化(保活)
adb shell dumpsys deviceidle whitelist +com.script.engine

# 关闭无障碍(恢复):
adb shell settings put secure enabled_accessibility_services ""
```

> 注:部分厂商 ROM 需在「设置-无障碍」里再手动确认一次;`takeScreenshot`(图像匹配/OCR/截图)要求 Android 11(API 30)以上。

## JSON 指令速查

统一坐标为屏幕百分比(0f~1f);查找优先级 `id → text/desc → image → ocr → coordinate`。

| type | 关键字段 | 说明 |
|------|---------|------|
| click / longClick | `condition`{id/text/desc/image/ocr/x,y} | 查找并点击,命中坐标加 ±5px 随机偏移 |
| swipe | `direction`(up/down/left/right) 或 `start`/`end`[x,y] + `duration` | 滑动 |
| input | `condition` + `text` + `clear` | 输入(支持 `{{$var}}` 占位) |
| wait | `ms`(固定) 或 `condition`+`timeout`(等元素出现) | 等待 |
| loop | `times` 或 `whileCondition` + `actions` | 循环 |
| if | `condition`(元素存在) 或 `varName`+`equals` + `thenActions`/`elseActions` | 判断分支 |
| screenshot | `path` | 截图存本地 |
| globalAction | `globalAction`(back/home/recents/notifications) | 全局动作 |
| setVar | `varName` + `text` | 存变量 |
| ocr | `varName` | OCR 读全屏文字存入变量 |
| launchApp | `packageName` | 启动指定应用 |

每个动作可配 `retry`(默认 2)、`retryInterval`、`timeout`;连续失败会触发一次全局弹窗清扫(点击含「关闭/跳过/取消/知道了」的控件)后重试。

### 变量与占位示例

```json
[
  { "type": "ocr", "varName": "code" },
  { "type": "input", "condition": { "id": "com.x:id/edit" }, "text": "验证码是 {{$code}}", "clear": true }
]
```

## 定时调度

```kotlin
// 每 30 分钟跑一次(WorkManager 周期下限 15 分钟)
SchedulerManager.scheduleInterval(context, "/sdcard/Scripts/demo_wechat.json", 30 * 60_000L)
// 延迟 60 秒后跑一次
SchedulerManager.scheduleOnceAfter(context, "/sdcard/Scripts/demo_wechat.json", 60_000L)
```

## 已知边界

- 图像匹配为纯 Kotlin 采样滑窗(`Bitmap.getPixel` 颜色距离),适合按钮/图标级定位;大范围高精度匹配建议后续接 OpenCV。
- `takeScreenshot` 有系统级频率限制,OCR/图像匹配循环间隔不宜过密。
- WorkManager 周期任务最小间隔 15 分钟(系统限制),更高频请用前台服务内自循环。
