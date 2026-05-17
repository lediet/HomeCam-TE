# Homecam-TE v1.0.0 工作记录与技术文档

**最后更新**：2026-05-17
**版本**：1.0.0
**状态**：首个可用版本，支持多设备 MJPEG 显示、事件轮询、语音+振动报警、UDP 自动发现

---

## 目录

1. [项目概述](#1-项目概述)
2. [架构设计](#2-架构设计)
3. [模块详细文档](#3-模块详细文档)
4. [数据流与调用链](#4-数据流与调用链)
5. [API 集成说明](#5-api-集成说明)
6. [UDP 自动发现协议](#6-udp-自动发现协议)
7. [报警系统](#7-报警系统)
8. [数据模型](#8-数据模型)
9. [UI 布局规则](#9-ui-布局规则)
10. [构建与部署](#10-构建与部署)
11. [已知问题与待修复](#11-已知问题与待修复)
12. [代码风格与约定](#12-代码风格与约定)
13. [版本记录](#13-版本记录)

---

## 1. 项目概述

### 1.1 定位

Homecam-TE 是 HomeCam 监控系统的 Android 显示终端（Terminal）。用于在局域网内接收多台 HomeCam 设备的 MJPEG 视频流、控制摄像头开关/镜头切换、显示事件日志并支持语音+振动报警。最多支持 4 台设备同时显示。

### 1.2 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 1.9.22 |
| UI | Jetpack Compose + Material3 | BOM 2024.02.00 |
| 最低 SDK | Android 8.0 (API 26) | - |
| 目标 SDK | Android 14 (API 34) | - |
| 网络 | OkHttp | 4.12.0 |
| Compose 编译器 | Kotlin Compiler | 1.5.10 |
| 图片加载 | Coil | 2.5.0 |
| 数据库 | Room | 2.6.1 |
| 注解处理 | KSP | 1.9.22-1.0.17 |
| JSON 序列化 | Gson | 2.10.1 |
| 导航 | Navigation Compose | 2.7.7 |
| 协程 | Kotlinx Coroutines | 1.7.3 |
| 构建工具 | Gradle + AGP | 8.5 + 8.2.2 |

### 1.3 目录结构

```
app/src/main/java/com/homecam/te/
├── HomeCamApp.kt              # Application 类，初始化 Room 数据库
├── model/
│   └── Models.kt              # 所有数据模型定义
├── data/
│   ├── CameraDatabase.kt      # Room Entity + DAO + Database
│   └── CameraRepository.kt    # 摄像头连接管理器（每设备一个客户端）
├── network/
│   ├── ApiClient.kt           # REST API 客户端
│   ├── MjpegClient.kt         # MJPEG 流解析器
│   ├── EventPoller.kt         # 事件轮询（5 秒间隔）
│   └── DiscoveryService.kt    # UDP 自动发现服务
├── service/
│   └── AlertManager.kt        # 报警管理器（TTS + 振动）
└── ui/
    ├── MainActivity.kt        # 主 Activity + NavHost
    ├── MainViewModel.kt       # 中央 ViewModel
    ├── TEGridScreen.kt        # 自适应网格主屏幕
    ├── CameraCard.kt          # 摄像头卡片组件
    ├── MjpegView.kt           # MJPEG 画面渲染（AndroidView）
    ├── EventSheet.kt          # 底部事件列表弹窗
    ├── AddCameraDialog.kt     # 手动添加摄像头对话框
    ├── SettingsScreen.kt      # 设置页面
    └── theme/Theme.kt         # Material3 主题

app/src/main/res/
├── drawable/                  # 启动图标矢量图
├── mipmap-anydpi-v26/         # 自适应启动图标
└── values/                    # 字符串/主题
```

---

## 2. 架构设计

### 2.1 分层架构

```
┌────────────────────────────────────────────────────────────────┐
│                       UI 层 (Jetpack Compose)                 │
│  TEGridScreen (自适应网格)                                     │
│  ├── CameraCard (摄像头卡片)                                   │
│  │   ├── MjpegView (MJPEG 流显示)                             │
│  │   └── StatusBar (最新事件 + 展开)                           │
│  ├── EventSheet (底部事件列表)                                 │
│  ├── FullscreenCamera (全屏预览)                               │
│  SettingsScreen (设置页)                                       │
│  AddCameraDialog (添加对话框)                                  │
├────────────────────────────────────────────────────────────────┤
│                   业务逻辑层                                    │
│  MainViewModel (中央协调器)                                    │
│  ├── CameraRepository (摄像头数据源管理)                       │
│  ├── DiscoveryService (UDP 发现)                               │
│  └── AlertManager (报警管理)                                   │
├────────────────────────────────────────────────────────────────┤
│                    网络层 (OkHttp + Coroutines)                │
│  ├── ApiClient (REST API)                                     │
│  ├── MjpegClient (MJPEG 流解析)                                │
│  └── EventPoller (事件轮询)                                    │
├────────────────────────────────────────────────────────────────┤
│                     数据层                                     │
│  Room DB (CameraDevice 持久化)                                 │
└────────────────────────────────────────────────────────────────┘
```

### 2.2 关键设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 状态管理 | StateFlow + ViewModel | 响应式驱动 UI，生命周期感知 |
| MJPEG 渲染 | AndroidView 包裹 ImageView | 避免 Compose 每帧重组，降低 CPU 占用 |
| 帧传递 | 每设备一个 StateFlow | Compose 原生响应式消费，解耦采集与渲染 |
| 网络客户端 | 每设备独立 OkHttp 实例 | 互不阻塞，独立管理连接生命周期 |
| 事件轮询 | 5 秒定时 + 时间戳比较 | 简单可靠，增量更新 |
| 设备发现 | UDP 广播 3 秒收集窗口 | 简单无状态，无需注册中心 |
| 报警触发 | StateFlow collect 监听 | 响应事件流变更，自动触发 |

### 2.3 连接生命周期

```
启动 / 从 Room 加载设备
  → CameraRepository.connectDevice()
    → 创建 ApiClient (REST)
    → 创建 MjpegClient (MJPEG 流) → launch coroutine
    → 创建 EventPoller (5 秒事件轮询) → start()
    → 创建 StatusJob (5 秒状态/摄像头列表轮询) → launch
    → 初始化 CameraState 并 emit 到 UI

disconnectDevice() / removeDevice()
  → MjpegClient.stop() + EventPoller.stop() + job.cancel()
  → 从 cameraStates map 移除
  → 从 Room 删除（removeDevice 时）
```

---

## 3. 模块详细文档

### 3.1 CameraRepository — 连接管理器

**文件**: `data/CameraRepository.kt`

核心职责：管理每台 HomeCam 设备的完整连接生命周期。

#### 成员

| 成员 | 类型 | 说明 |
|------|------|------|
| `camerasFlow` | `StateFlow<Map<String, CameraState>>` | 所有摄像头状态的可观察 map |
| `connections` | `Map<String, CameraConnection>` | 每设备的连接对象（ApiClient + MjpegClient + EventPoller + Job） |
| `frameFlows` | `Map<String, MutableStateFlow<ByteArray?>>` | 每设备的 MJPEG 帧流 |

#### 关键方法

- `connectDevice(device)` — 建立连接，启动所有客户端
- `disconnectDevice(id)` — 断开连接，清理所有资源
- `getFrameFlow(deviceId)` — 获取可观察的帧流（供 MjpegView 消费）
- `getApiClient(deviceId)` — 获取 API 客户端（供控制操作：开关/切换）

### 3.2 MjpegClient — MJPEG 流解析

**文件**: `network/MjpegClient.kt`

#### 架构

```
OkHttp GET /video
  → multipart/x-mixed-replace 流
  → 逐字节读取到缓冲区
  → 按 boundary 分割帧
  → 解析 Content-Length 头部
  → 提取 JPEG 二进制块
  → onFrame(ByteArray) 回调
```

#### 重连策略

- 连接断开后自动重试
- 重试间隔递增：1s, 2s, 3s... 最多 10s
- 通过 `AtomicBoolean` 标志控制启停

#### 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 连接超时 | 10s | - |
| 读取超时 | 0（无超时） | 流式读取不超时 |
| 读取缓冲区 | 64KB | 每次 read() 大小 |
| 最大累积缓冲 | 2MB | 防内存溢出 |

### 3.3 ApiClient — REST API 客户端

**文件**: `network/ApiClient.kt`

封装 HomeCam 服务器所有 REST API 调用。

| 方法 | API 端点 | 返回类型 |
|------|----------|----------|
| `getStatus()` | GET /api/status | `Result<StatusResponse>` |
| `getEvents()` | GET /api/events | `Result<List<EventResponseItem>>` |
| `getCameras()` | GET /api/cameras | `Result<List<CameraInfo>>` |
| `switchCamera(cameraId, logicalId)` | GET /api/camera/switch | `Result<CameraSwitchResponse>` |
| `setPower(on)` | GET /api/camera/power | `Result<CameraPowerResponse>` |
| `getVideoUrl()` | GET /video (返回 URL) | `String` |

所有网络操作在 `Dispatchers.IO` 执行，返回 `Result<T>` 便于调用方处理成功/失败。

### 3.4 EventPoller — 事件轮询

**文件**: `network/EventPoller.kt`

- 每 5 秒调用 `ApiClient.getEvents()`
- 记录上次轮询时间戳，仅报告新事件
- 新事件通过 `onNewEvents` 回调通知
- 事件文本格式化（中文本地化）：
  - `enter` → `有{label}进入了`
  - `leave` → `有{label}离开了`
  - `cry` → `检测到婴儿哭声`
  - `sleep` → `宝宝睡着了`
  - `wake_up` → `宝宝睡醒了`

### 3.5 DiscoveryService — UDP 自动发现

**文件**: `network/DiscoveryService.kt`

#### 协议

| 方向 | 内容 | 端口 |
|------|------|------|
| TE → HomeCam | `HOMECAM_DISCOVER` | 45678 (UDP 广播) |
| HomeCam → TE | `HOMECAM_RESPONSE|name|ip|port|id` | 45678 (UDP 单播) |

#### 流程

1. 发送 3 次广播（间隔 200ms）
2. 监听 3 秒内的回复
3. 去重（按设备 ID）
4. 每个设备通过 `onDeviceFound` 回调通知
5. 完成后调用 `onDiscoveryComplete`

### 3.6 AlertManager — 报警管理

**文件**: `service/AlertManager.kt`

#### TTS 语音

- 使用 Android `TextToSpeech` API
- 语言：中文（`Locale.CHINESE`）
- 每次播报覆盖前一次（`QUEUE_FLUSH`）

#### 振动

| 事件类型 | 振动时长 |
|----------|----------|
| enter | 200ms |
| leave | 200ms |
| cry | 1000ms |
| sleep | 100ms |
| wake_up | 100ms |

#### 配置项

所有报警可通过设置页独立开关：
- 总开关
- 按事件类型（进入/离开/哭声/睡眠）
- 振动开关
- 语音开关

### 3.7 MjpegView — MJPEG 渲染组件

**文件**: `ui/MjpegView.kt`

- 使用 `AndroidView` 包裹原生 `ImageView`
- 通过 `StateFlow<ByteArray?>` 接收 JPEG 帧
- `LaunchedEffect` 收集帧流
- 后台线程 `BitmapFactory.decodeByteArray()` 解码
- 主线程 `ImageView.setImageBitmap()` 显示
- 避免 Compose 重组开销

---

## 4. 数据流与调用链

### 4.1 实时视频流路径

```
MjpegClient (OkHttp IO 线程)
  → 解析 multipart/x-mixed-replace
  → onFrame(jpegData) 回调
    → CameraRepository.frameFlows[deviceId].value = jpegData
      → StateFlow<ByteArray?> 更新
        → MjpegView.LaunchedEffect 接收
          → Dispatchers.Default: BitmapFactory.decodeByteArray()
            → Dispatchers.Main: ImageView.setImageBitmap()
```

### 4.2 事件轮询路径

```
EventPoller (Dispatchers.IO)
  → 每 5 秒 GET /api/events
  → 过滤 timestamp > lastEventTime
  → onNewEvents(list) 回调
    → CameraRepository 更新 CameraState.events + latestEvent
      → camerasFlow emit
        → TEGridScreen/CameraCard recompose
        → AlertManager.onEvent() → TTS + Vibrator
```

### 4.3 摄像头控制路径

```
CameraCard 长按 → 控制菜单
  → ViewModel.setPower(deviceId, on)
    → CameraRepository.getApiClient(deviceId).setPower(on)
      → GET /api/camera/power?action=on|off
        → HomeCam 摄像头开关
```

### 4.4 设备发现路径

```
用户点击搜索按钮
  → MainViewModel.startDiscovery()
    → DiscoveryService.start()
      → UDP 广播 3 次
      → 监听 3 秒
        → onDeviceFound(CameraDevice)
          → CameraRepository.saveDevice(Room)
          → CameraRepository.connectDevice()
            → 启动 MJPEG + 轮询
              → UI 自动更新
```

---

## 5. API 集成说明

### 5.1 视频流消费

TE 端不直接使用 `GET /video` 的 URL，而是通过 `MjpegClient` 在后台建立独立 HTTP 连接，解析 MJPEG 多部分流。每台设备一个独立连接。

### 5.2 事件同步机制

TE 端通过轮询替代长连接：
- 间隔：5 秒
- 增量：记录 `lastEventTime`，仅拉取更新的条目
- 容量：内存保留最多 20 条最近事件

### 5.3 摄像头控制

通过 REST API 控制 HomeCam 端：
- 电源开关：`GET /api/camera/power?action=on|off`
- 镜头切换：`GET /api/camera/switch?cameraId=X&logicalCameraId=Y`

---

## 6. UDP 自动发现协议

已实现的协议与服务器端 `v1.4.0` 中 CamWebServer 的 UDP 监听完全兼容。

### 协议格式

```
请求: HOMECAM_DISCOVER
回应: HOMECAM_RESPONSE|{设备名}|{IP}|{端口}|{设备ID}
```

### 字段说明

| 字段 | 说明 |
|------|------|
| 设备名 | HomeCam-{ANDROID_ID 后 6 位} |
| IP | 设备局域网 IP |
| 端口 | Web 服务端口（默认 8080） |
| 设备 ID | Settings.Secure.ANDROID_ID |

### TE 端实现

- 端口：45678 (UDP)
- 广播地址：255.255.255.255
- 发送 3 次，间隔 200ms
- 收集窗口：3 秒
- 去重：按设备 ID

---

## 7. 报警系统

### 7.1 触发流程

```
EventPoller 发现新事件
  → CameraRepository 更新 CameraState.latestEvent
    → camerasFlow emit
      → MainViewModel 监听
        → AlertManager.onEvent(type, label)
          → 检查各开关
            → TTS.speak(text)
            → Vibrator.vibrate(effect)
```

### 7.2 报警类型映射

| 事件类型 | 语音内容 | 振动 |
|----------|----------|------|
| enter | `有xxx进入了` | 短振 200ms |
| leave | `有xxx离开了` | 短振 200ms |
| cry | `检测到婴儿哭声` | 连续振 1000ms |
| sleep | `宝宝睡着了` | 短振 100ms |
| wake_up | `宝宝睡醒了` | 短振 100ms |

---

## 8. 数据模型

### 8.1 CameraDevice（Room 持久化）

```kotlin
@Entity(tableName = "camera_devices")
data class CameraDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val isAutoDiscovered: Boolean = false,
    val lastSeen: Long = 0L
)
```

### 8.2 CameraState（运行时状态）

```kotlin
data class CameraState(
    val device: CameraDevice,
    val isOnline: Boolean = false,
    val isPoweredOn: Boolean = false,
    val latestEvent: String? = null,
    val latestEventTime: Long = 0L,
    val latestEventLabel: String = "",
    val events: List<EventItem> = emptyList(),
    val availableCameras: List<CameraInfo> = emptyList(),
    val currentCameraId: String = ""
)
```

### 8.3 EventItem（事件条目）

```kotlin
data class EventItem(
    val type: String,
    val time: Long,
    val label: String = "",
    val displayText: String = ""
)
```

### 8.4 Room 数据库

- 数据库名：`homecam-te.db`
- 表：`camera_devices`
- 版本：1
- `exportSchema = false`

---

## 9. UI 布局规则

### 9.1 自适应网格

| 设备数 | 列数 | 布局 |
|--------|------|------|
| 1 台 | 1 列 | 单卡片占满宽度 |
| 2 台 | 1 列 | 上下各一张（竖屏为主） |
| 3 台 | 2 列 | 前 2 张并排，第 3 张跨列 |
| 4 台 | 2 列 | 2x2 网格 |

### 9.2 卡片结构

```
+------------------------------------------+
|                                          |
|   MJPEG 视频画面 / 离线占位图              |
|   (16:9 比例)                             |
|                                          |
+------------------------------------------+
|  ● 设备名  最新事件文本   [展开箭头 >]    |
+------------------------------------------+
```

### 9.3 交互

| 操作 | 行为 |
|------|------|
| 点击画面 | 全屏预览 |
| 长按画面 | 弹出控制菜单（预留） |
| 点击展开箭头 | 底部弹窗显示最近 20 条事件 |

### 9.4 全屏模式

- 点击卡片画面进入全屏
- 全屏隐藏状态栏，画面撑满
- 左上角关闭按钮

---

## 10. 构建与部署

### 10.1 环境要求

- Android Studio Hedgehog (2023.1.1+) 或 IntelliJ IDEA
- JDK 17
- Android SDK 34
- Gradle 8.5（项目已配置 wrapper properties）

### 10.2 构建步骤

```bash
# 生成 Gradle Wrapper（如本地有 Gradle）
gradle wrapper --gradle-version 8.5

# 或直接通过 Android Studio 打开项目，IDE 会自动下载

# Debug APK
./gradlew assembleDebug

# APK 输出
app/build/outputs/apk/debug/app-debug.apk
```

### 10.3 Gradle Wrapper 首次生成

项目已包含 `gradle-wrapper.properties`，但需要 `gradlew` 脚本和 `gradle-wrapper.jar`。可通过以下方式获取：
1. Android Studio 打开项目 → IDE 自动生成
2. 本地安装 Gradle 8.5 后运行 `gradle wrapper --gradle-version 8.5`

### 10.4 权限清单

| 权限 | 用途 |
|------|------|
| INTERNET | HTTP 访问 HomeCam 设备 |
| ACCESS_NETWORK_STATE | 检测网络连接 |
| ACCESS_WIFI_STATE | 获取 WiFi 状态 |
| CHANGE_WIFI_MULTICAST_STATE | UDP 广播发现 |
| VIBRATE | 振动报警 |
| POST_NOTIFICATIONS | 通知栏权限 (Android 13+) |

### 10.5 安装与运行

```bash
adb install app-debug.apk
```

首次使用：
1. 启动应用，授予网络权限
2. 点击右上角 `+` 手动添加摄像头 IP
3. 或点击搜索图标自动发现局域网内的 HomeCam 设备
4. 设备连接成功自动显示视频画面

---

## 11. 已知问题与待修复

### 11.1 功能缺陷 (P1)

| 位置 | 问题 | 修复方案 |
|------|------|----------|
| `MainViewModel` | 报警重复触发 — `cameraStates` 每次 emit 都会重新检查所有设备的 `latestEvent`，状态变更（如 isOnline）时可能重复报警 | 添加事件时间戳去重标志 |
| `CameraRepository` | `updateCameraState` 空安全 — 如果设备 ID 尚未在 map 中，更新静默跳过 | 改用 `getOrPut` 模式 |
| `MjpegClient` | `job` 变量 unused — `start()` 在 `withContext` 中运行，`job?.cancel()` 无法打断 `connectAndRead()` 的阻塞 read | 使用 `interrupt()` 或关闭 socket |
| 未连接时 UI | 设备离线状态显示已实现，但无重连按钮 | 添加手动重连 UI |

### 11.2 功能缺失 (P2)

| 功能 | 说明 |
|------|------|
| 长按控制菜单 | CameraCard 已预留 `onLongPress` 回调，但未实现菜单 UI |
| 设备删除 | 已实现 repository 方法，但 UI 层未添加删除入口 |
| 设备离线自动重连 | 目前仅在 MJPEG 流断开时标记离线，没有主动重连机制 |
| 设置持久化 | AlertSettings 当前在内存中，未持久化到 SharedPreferences |
| 通知栏前台服务 | 目前未使用前台服务保活 |

### 11.3 性能优化 (P2)

| 位置 | 问题 |
|------|------|
| `MjpegView` | `BitmapFactory.decodeByteArray` 每帧分配新 Bitmap，GC 压力大 |
| `MjpegClient` | 字节数组拼接使用 `data + copyOf`，频繁分配内存 |
| UI | 4 台同时渲染时，4 个独立的 LaunchedEffect 在各自协程中解码 |

---

## 12. 代码风格与约定

### 12.1 命名约定

- 类：PascalCase (`CameraRepository`, `MjpegClient`)
- 方法/变量：camelCase (`connectDevice`, `getFrameFlow`)
- 常量：UPPER_SNAKE_CASE (`DEFAULT_BUFFER_SIZE`)
- 包：全小写 (`com.homecam.te.network`)

### 12.2 协程使用

- 网络操作：`Dispatchers.IO`（ApiClient 内部已指定）
- MJPEG 帧解码：`Dispatchers.Default`（MjpegView 内部）
- UI 更新：`Dispatchers.Main`（Compose 自动）
- ViewModel 作用域：`viewModelScope`
- Repository 作用域：`CoroutineScope(SupervisorJob() + Dispatchers.Default)`

### 12.3 资源管理

- Bitmap：每帧解码后由 GC 回收
- OkHttp Response：自动关闭（use try-with-resources）
- Room：通过 Application 单例管理生命周期
- AlertManager：ViewModel.onCleared() 中 release()
- DiscoveryService：viewModelScope 管理

---

## 13. 版本记录

### v1.0.0 (2026-05-17)

#### 新增功能

1. **多设备 MJPEG 监控** — 支持 1~4 台 HomeCam 设备同时显示
   - 自适应网格布局（1/2/3/4 台自动调整）
   - 每设备独立 MJPEG 连接互不阻塞
   - 离线/关闭占位图显示

2. **设备管理**
   - 手动添加（IP + 端口 + 名称）
   - UDP 自动发现（HOMECAM_DISCOVER 协议）
   - Room 数据库持久化设备列表
   - 启动自动恢复连接

3. **事件监控**
   - 每 5 秒轮询 `/api/events`
   - 增量更新（时间戳比较）
   - 底部弹窗显示最近 20 条事件
   - 支持 enter/leave/cry/sleep/wake_up 事件类型

4. **摄像头控制**
   - 点击画面全屏预览
   - REST API 支持电源开关和镜头切换（UI 入口预留）

5. **报警系统**
   - TTS 语音播报（中文）
   - 振动报警（不同时长）
   - 按事件类型独立开关
   - 振动/语音独立开关

6. **技术架构**
   - Jetpack Compose + Material3 纯声明式 UI
   - StateFlow 响应式状态管理
   - AndroidView 高性能 MJPEG 渲染
   - 自动重连机制（递增间隔）

#### 技术栈

| 组件 | 技术选型 |
|------|----------|
| UI | Jetpack Compose + Material3 |
| 网络 | OkHttp 4.12.0 |
| MJPEG 解码 | 自定义 multipart/x-mixed-replace 解析器 |
| 数据库 | Room 2.6.1 + KSP |
| 状态管理 | ViewModel + StateFlow |
| JSON | Gson 2.10.1 |
| 语音 | Android TTS (TextToSpeech) |
| 振动 | Vibrator / VibratorManager |
| 发现 | UDP DatagramSocket |

---

*文档结束 — Homecam-TE v1.0.0*
