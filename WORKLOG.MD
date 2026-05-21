# Homecam-TE v1.0.0 工作记录与技术文档

**最后更新**：2026-05-17
**版本**：1.2.1
**状态**：v1.2.1 — 新增跌倒、起身、玩手机三类事件解析与显示、报警设置新增对应开关

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

Homecam-TE 是 HomeCam 监控系统的 Android 显示终端（Terminal）。用于在局域网内接收多台 HomeCam 设备的 MJPEG 视频流、控制摄像头开关/镜头切换、显示事件日志并支持语音+振动报警。支持自适应网格布局，多设备页面可滚动。

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

- 端口：45678 (UDP)，单 socket 收发一体
- 广播地址：255.255.255.255 + 子网定向广播（如 192.168.50.255）
- 发送 5 次，间隔 150ms
- 收集窗口：3 秒
- 去重：按设备 ID
- 自回声过滤：忽略本机发出的广播回环

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
| 2~N 台 | 2 列 | 始终双列布局，奇数台时最后一张在左下角（右下角空位） |

布局算法：
- `columns = if (count == 1) 1 else 2`
- 奇数台时最后一行仅左列有卡片，右列空位 `Spacer(weight(1f))` 填充

### 9.2 卡片结构

```
+------------------------------------------+
|[名称/IP]                      [电源]       |
|   MJPEG 视频画面 / 离线占位图              |
|   (9:16 竖屏比例, FIT_CENTER)             |
|                               [镜头切换]   |
+------------------------------------------+
|  最新事件文本                [展开箭头 >]  |
+------------------------------------------+
```

- 画面比例 9:16（竖屏），`FIT_CENTER` 缩放（不足留黑边，不裁剪）
- 左上角悬浮显示设备名称（无名称显示 IP）
- 右上角电源开关按钮
- 左下角镜头切换按钮（仅多镜头设备可见）
- 底部状态栏只显示事件文本 + 展开箭头（不显示设备名称）
- 主页面支持纵向滚动（多设备时）

### 9.3 交互

| 操作 | 行为 |
|------|------|
| 点击画面 | 全屏预览 |
| 长按画面 | 弹出删除菜单（无拖拽时） |
| 点击展开箭头 | 底部弹窗显示最近 20 条事件 |
| 点击电源 | 切换摄像头电源 |
| 点击镜头切换 | 切换摄像头镜头（多镜头设备） |

### 9.4 拖拽排序（待启用）

编辑模式拖拽排序功能已实现代码但暂未启用，详见 [11.2 编辑模式拖拽排序（待完善）](#112-编辑模式拖拽排序待完善)。

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

### 11.2 编辑模式拖拽排序（待完善）

#### 功能说明

编辑模式下用户可通过长按 + 拖拽调整视频卡片在网格中的排列顺序。该功能代码已实现但因手势检测与 MJPEG 原生 View 的触摸事件冲突而暂未启用，编辑按钮已从 UI 隐藏。

#### 已实现的代码

**TEGridScreen.kt** — `CameraGrid` 函数中包含：

- **`order`** (`remember(cameraStates) { mutableStateOf(cameraStates.indices.toList()) }`) — 卡片显示顺序，随 `cameraStates` 变化自动重建
- **`draggedIndex`** / **`dragAccumulated`** — 拖拽过程中的状态追踪
- **`orderedStates`** (`order.filter { ... }.map { ... }`) — 按 order 排序后的卡片列表，含越界过滤
- **`moveItem(from, to)`** — 列表重排操作（`add(to, removeAt(from))`）
- **`isEditMode`** 参数 — 贯穿 TEGridScreen → CameraGrid → CameraCard

**CameraCard.kt** — 手势处理：

- **普通模式**：`detectTapGestures` — 点按全屏、长按弹出删除菜单
- **编辑模式**：`detectDragGesturesAfterLongPress` — 长按 + 拖拽触发重排
- **透明覆盖层**：编辑模式下，在 Card 上方叠加 `matchParentSize()` 的透明 Box，手势由覆盖层捕获，绕开内部 AndroidView 的事件消费
- 当前实现采用**单个 `pointerInput(isEditMode)` 块**，通过 `isEditMode` key 自动切换手势策略

#### 冲突根因

`MjpegView` 使用 `AndroidView` 包装原生 `ImageView`（`ImageView`）。Compose 中 Native View 在 `PointerEventPass.Main` 阶段优先消费触摸事件，导致即使手势处理器放在 Card 层级也收不到连续的拖动事件。

尝试过的方案：
1. ❌ `return@label` 语法 — Kotlin 不支持对此处嵌套 lambda 的标签引用
2. ❌ 将 `pointerInput` 从 Box 移到 Card 修饰器层级 — AndroidView 仍在事件链底层拦截
3. ❌ 透明覆盖层（当前方案）— `matchParentSize()` 覆盖层在 Z 轴最顶层仍无法捕获拖动事件

#### 未来修复方向

1. **替换手势 API**：尝试使用 `Modifier.draggable()` 或 `Modifier.scrollable()` 替代 `detectDragGesturesAfterLongPress`
2. **修改 MjpegView**：禁止 ImageView 消费触摸事件（`isClickable = false`、重写 `onTouchEvent` 返回 `false`）
3. **原生手势代理**：在 `AndroidView` 的 `update` 块中代理触摸事件到 Compose
4. **使用 Compose 的 `PointerEventPass.Initial`**：在自定义 `awaitEachGesture` 中指定 Initial pass，在事件到达 Native View 之前拦截
5. **改用按钮排序**：放弃拖拽，改为上移/下移按钮的交互方式

#### 启用方式

恢复编辑按钮后只需将 `isEditMode = false` 改为绑定状态变量即可启用。相关代码全部保留。

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

### v1.2.1 (2026-05-20)

#### 新增与优化

1. **新增事件类型** — 支持 `fall`（跌倒）、`get_up`（起身）、`phone`（玩手机）三类事件的解析与中文显示
2. **phone 事件置信度** — 解析服务端 `phone:XX` 格式，提取置信度并显示为"有人在玩手机（XX%）"
3. **事件颜色** — 新增事件颜色：`fall` 橙色、`get_up` 浅绿、`phone` 紫色
4. **报警设置** — 设置页面新增跌倒、起身、玩手机报警开关，并可持久化保存
5. **报警过滤** — 修复原有报警逻辑，按事件类型开关过滤（原来所有类型都会触发报警）

#### 版本号

- `versionName = "1.2.1"`

---

### v1.2.0 (2026-05-17)

#### 新增与优化

1. **设备编辑功能** — 长按卡片菜单新增"编辑设备"，可修改名称、IP 和端口；改 IP/端口自动重建连接，纯改名实时更新
2. **搜索反馈提示** — 发现完成通过 Snackbar 提示结果（"发现 X 台设备" 或 "未发现设备，请检查网络或手动添加"）

#### BUG 修复

1. **UDP 自动发现修复** — 原实现广播使用临时端口而监听在 45678，服务端回复到临时端口导致收不到响应；改为单 socket 绑定 45678 端口收发一体
2. **子网定向广播** — 除 255.255.255.255 外，额外计算子网广播地址（如 192.168.50.255）双路发送，提高跨网段发现率
3. **自回声过滤** — 过滤本机广播回环包，避免日志刷屏和误解析
4. **电源状态显示修复** — 关闭摄像头时正确显示"设备关闭"占位图，而非空白或 "设备离线"

#### 版本号

- `versionName = "1.2.0"`

---

### v1.1.0 (2026-05-17)

#### 新增与优化

1. **移除状态轮询** — 删除 5 秒定时轮询 `/api/status`，改为 MJPEG 帧驱动在线状态 + API 调用结果即时更新
2. **摄像头切换功能** — 全屏和卡片左下角新增镜头切换按钮，调用 `/api/camera/switch`
3. **电源开关刷新** — 关闭再打开电源后自动重启 MJPEG 流，视频恢复正常
4. **9:16 竖屏视频比例** — 卡片画面从 16:9 改为 9:16，`FIT_CENTER` 缩放（留黑边不裁剪）
5. **自适应网格优化** — 1 台单列，2 台及以上双列；奇数台时最后一张在左下角，右下角空位
6. **主页面滚动** — 添加 `verticalScroll + rememberScrollState`，多设备时可滚动
7. **卡片布局重构** — 左上角名称悬浮层（名称/IP）、右上角电源、左下角镜头切换
8. **状态栏简化** — 只显示事件文本 + 展开箭头，名称已移至视频画面覆盖层
9. **拖拽排序（代码已实现，暂未启用）** — 详见 [11.2 编辑模式拖拽排序（待完善）](#112-编辑模式拖拽排序待完善)
10. **Gson 解析修复** — `/api/cameras` 返回 `{"cameras":[...]}` 对象格式，修复原 `TypeToken<List>` 解析失败

#### BUG 修复

- 添加/删除摄像头闪退：`remember(order, cameraStates)` 中 `order` 索引越界，改为 `remember(cameraStates)` 自动同步
- `return@let` 编译错误：Kotlin 不支持参数名 lambda 标签引用，改为条件反转 `if (draggedIndex >= 0) { ... }` 避免 `return`
- 卡片左上角显示优化：只显示名称（无名称显示 IP），去掉 IP:端口 第二行

#### 版本号

- `versionName = "1.1.0"`

---

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

*文档结束 — Homecam-TE v1.2.0*
