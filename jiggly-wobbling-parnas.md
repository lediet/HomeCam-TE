# Homecam-TE 开发方案

## 一、概述

Homecam-TE 是 HomeCam 监控系统的 Android 显示终端（Terminal）。
用于在局域网内接收多台 HomeCam 设备的 MJPEG 视频流、控制摄像头开关/镜头切换、显示事件日志并支持语音+振动报警。最多支持4台同时显示。

## 二、技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 网络 | OkHttp + Coroutines |
| MJPEG 解码 | 自定义解析 + Bitmap |
| 语音报警 | Android TTS |
| 振动报警 | Vibrator / VibratorManager |
| UDP 发现 | DatagramSocket |
| 图片加载 | Coil |
| 数据库 | Room (存储设备列表) |


## 三、应用架构

```
+------------------------------------------------------------------+
|                        UI 层 (Jetpack Compose)                   |
|  TEGridScreen (主屏幕布局)                            |
|  +-- CameraCard (单个摄像头卡片)                    |
|  |   +-- MjpegView (MJPEG 流显示)                 |
|  |   +-- StatusBar (最新事件 + 展开按钮)           |
|  |   +-- EventSheet (底部展开事件列表)        |
|  SettingsScreen (设置页)                                 |
|  AddCameraDialog (添加摄像头对话框)               |
+---------------------+--------------------------------------------+
|                      |                                             |
|  业务逻辑层                                |
|  CameraRepository (摄像头数据源管理)               |
|  DiscoveryService (UDP 发现服务)                       |
|  AlertManager (报警管理器)                             |
|  EventPoller (事件轮询)                               |
+---------------------+--------------------------------------------+
|                      |                                             |
|  网络层 (OkHttp)                                |
|  MjpegClient (MJPEG 流解析)                              |
|  ApiClient (REST API)                                     |
+---------------------+--------------------------------------------+
|                      |                                             |
|  数据层                                |
|  Room DB (设备列表持久化)                              |
+---------------------+--------------------------------------------+
```

## 四、主页面布局设计

### 4.1 布局规则

使用 LazyVerticalGrid 实现自适应网格：

| 设备数 | 列数 | 布局 |
|--------|------|------|
| 1 台 | 1 列 | 单卡片占满宽度 |
| 2 台 | 1 列 | 上下各一张（竖屏为主） |
| 3 台 | 2 列 | 前2张并排，第3张跨列 |
| 4 台 | 2 列 | 2x2 网格 |

### 4.2 卡片结构 (CameraCard)

每个设备卡片包含三个区域：

```
+------------------------------------------+
|                                          |
|          MJPEG 视频画面                    |
|          (占大部分区域)                    |
|                                          |
|                                          |
+------------------------------------------+
| 最新事件: 有person进入了  [展开箭头 >]    |
+------------------------------------------+
```

- 状态栏始终显示最新1条事件，带时间戳
- 点击展开箭头 → ModalBottomSheet 弹出最近20条事件
- 点击画面区域 → 全屏预览该摄像头
- 长按画面 → 弹出控制菜单（开关/切换镜头）

### 4.3 顶部工具栏

- 标题: "Homecam-TE"
- 设备数量指示器 ("已连接: 2/4")
- 右侧 + 按钮 → 添加设备对话框（手动输入IP）
- 右上角设置齿轮 → 设置页


## 五、UDP 自动发现协议

### 5.1 协议设计

**发现端口**: 45678 (UDP)

**流程**:
1. TE 端启动时向 255.255.255.255:45678 广播 "HOMECAM_DISCOVER"
2. HomeCam 端收到后单播回应 "HOMECAM_RESPONSE|{设备名}|{IP}|{端口}|{设备ID}"
3. TE 端收集 3 秒内的所有回应，去重后加入设备列表

**示例**:
```
请求: HOMECAM_DISCOVER
回应: HOMECAM_RESPONSE|卧室摄像头|192.168.1.101|8080|cam_001
```


## 六、API 集成

所有调用基于 HomeCam 现有 REST API：

| 功能 | 方法 | 端点 | 用途 |
|------|------|------|------|
| 视频流 | GET | /video | MJPEG 流，用 OkHttp 流式读取 |
| 状态 | GET | /api/status | 运行状态、最新事件 |
| 事件日志 | GET | /api/events | 历史事件列表（含label） |
| 摄像头列表 | GET | /api/cameras | 枚举可用摄像头 |
| 切换镜头 | GET | /api/camera/switch | 切换摄像头（cameraId+logicalCameraId） |
| 开关电源 | GET | /api/camera/power | 开关摄像头（action=on|off） |

### 6.1 MJPEG 流解析方案

OkHttp 请求 /video → 获取 InputStream → 逐行读取 boundary 头部 → 提取 JPEG 数据块 → BitmapFactory 解码 → Image 显示

```kotlin
// 伪代码
val client = OkHttpClient()
val request = Request.Builder().url("http://{ip}:{port}/video").build()
val response = client.newCall(request).execute()
val body = response.body?.byteStream()
// 解析 multipart/x-mixed-replace 格式
while (isActive) {
    // 读取到 --boundary
    // 读取 Content-Length
    // 读取 JPEG 字节
    // BitmapFactory.decodeByteArray → callback
}
```


## 七、报警系统

### 7.1 报警触发条件

TE 端每 5 秒轮询 `/api/events`，对比上次轮询时间，新事件触发报警。

### 7.2 报警类型

| 报警类型 | 语音(TTS)播报内容 | 振动 |
|----------|-------------------|------|
| enter | "有xxx进入了" | 短振 200ms |
| leave | "有xxx离开了" | 短振 200ms |
| cry | "检测到婴儿哭声" | 连续振 1s |
| sleep | "宝宝睡着了" | 短振 100ms |
| wake_up | "宝宝睡醒了" | 短振 100ms |

### 7.3 设置页配置项

- 报警总开关 (开/关)
- 按事件类型独立开关
  - 进入报警
  - 离开报警
  - 哭声报警
  - 睡眠报警
- 振动开关
- 语音开关

## 八、数据模型

```kotlin
// 本地存储的设备信息
@Entity
data class CameraDevice(
    @PrimaryKey val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val isAutoDiscovered: Boolean = false,
    val lastSeen: Long = 0L
)

// 运行时设备状态
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

// 事件条目
data class EventItem(
    val type: String,
    val time: Long,
    val label: String = "",
    val displayText: String = ""
)
```

## 九、开发阶段

### Phase 1: 项目脚手架
- 创建 Android 项目 (Jetpack Compose + Material3)
- 配置依赖: OkHttp, Coil, Room, Coroutines
- 搭建三层架构包结构
- 实现 Navigation (主屏 + 设置页)

### Phase 2: 核心网络层
- 实现 ApiClient (OkHttp + REST)
- 实现 MjpegClient (MJPEG 流解析)
- 实现 EventPoller (5秒轮询)
- 测试单设备连接

### Phase 3: 主页面 UI
- 实现 CameraCard (MjpegView + StatusBar)
- 实现 TEGridScreen (自适应网格布局)
- 实现 EventSheet (底部事件列表弹窗)
- 实现全屏预览功能

### Phase 4: 设备管理
- 实现 Room 数据库 + CameraDevice Entity
- 实现手动添加对话框
- 实现设备列表管理 (增/删)
- 实现 UDP 发现服务 (DiscoveryService)

### Phase 5: 摄像头控制
- 长按菜单: 开关电源 / 切换镜头
- 调用 /api/camera/power
- 调用 /api/camera/switch
- 切换/开关后刷新 MJPEG 流

### Phase 6: 报警系统
- 实现 AlertManager (TTS + Vibrator)
- 实现设置页 UI
- 报警开关持久化
- 端到端测试报警流程

### Phase 7: 打磨
- 多设备同时显示性能优化
- 网络断开重连机制
- 错误状态 UI (设备离线/连接失败)
- 横竖屏适配
- 应用图标
- 编译 Release APK

## 十、关键实现注意点

1. **MJPEG 流并发**: 每台设备一个独立 OkHttp 请求 + 协程，互不阻塞
2. **内存管理**: Bitmap 复用，超出 4 台时释放不可见卡片资源
3. **后台保活**: 使用前台服务或 WorkManager 保持发现和轮询
4. **UDP 广播权限**: Android 13+ 需要 POST_NOTIFICATIONS，Android 8+ 需要 ACCESS_FINE_LOCATION（WiFi 扫描）
5. **线程模型**: 网络操作在 IO 调度器，UI 更新在主调度器
6. **冷启动恢复**: Room 中存储的设备列表在启动时自动恢复连接


## 十一、横竖屏适配策略

- 竖屏: 按 4.1 节的网格规则布局
- 横屏: 2 台以上时自动改为 2 列网格

## 十二、MJPEG 渲染方案

使用 AndroidView 包裹原生 ImageView，避免 Compose 每帧重组

## 十三、包结构

```
com.homecam.te/
├── ui/
├── data/
├── network/
├── service/
└── model/
```

---

## 十五、UDP 自动发现协议格式（已实现）

**发现端口**: 45678 (UDP)

**请求**: HOMECAM_DISCOVER

**响应**: HOMECAM_RESPONSE|{ 设备名 }|{ IP }|{ 端口 }|{ 设备 ID }

**字段说明**:
- 设备名: HomeCam-{ ANDROID_ID 后 6 位 }
- IP: 本机局域网 IP
- 端口: Web 服务端口（默认 8080）
- 设备 ID: Settings.Secure.ANDROID_ID

**实现**: CamWebServer.kt · runUdpListener() · Daemon 线程 + DatagramSocket
---

*文档结束*
