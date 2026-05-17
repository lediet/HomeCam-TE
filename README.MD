# Homecam-TE

HomeCam 监控系统的 Android 显示终端。在局域网内接收多台 HomeCam 设备的 MJPEG 视频流，支持事件监控、语音+振动报警和摄像头控制。

## 功能

- **多设备监控** — 最多同时显示 4 台 HomeCam 设备，自适应网格布局
- **MJPEG 实时流** — 每设备独立连接，互不阻塞
- **UDP 自动发现** — 局域网广播自动搜索 HomeCam 设备
- **事件追踪** — 5 秒轮询事件日志，增量更新，底部弹窗查看
- **语音报警** — 中文 TTS 播报事件类型（进入/离开/哭声/睡眠）
- **振动报警** — 不同事件不同振动时长
- **摄像头控制** — 电源开关、镜头切换（UI 入口已预留）
- **全屏预览** — 点击卡片进入全屏模式
- **设备持久化** — Room 数据库保存设备列表，启动自动恢复连接

## 截图

（待补充）

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 1.9.22 |
| UI | Jetpack Compose + Material3 |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |
| 网络 | OkHttp 4.12.0 |
| 数据库 | Room 2.6.1 + KSP |
| JSON | Gson 2.10.1 |
| 协程 | Kotlinx Coroutines 1.7.3 |

## 构建要求

- Android Studio Hedgehog (2023.1.1+) 或 IntelliJ IDEA
- JDK 17
- Android SDK 34

## 构建步骤

```bash
# 生成 Gradle Wrapper（如未生成）
gradle wrapper --gradle-version 8.5

# Debug APK
./gradlew assembleDebug

# APK 输出位置
app/build/outputs/apk/debug/app-debug.apk
```

或直接用 Android Studio 打开项目根目录，IDE 会自动处理依赖下载。

## 安装

```bash
adb install app-debug.apk
```

## 快速开始

1. 安装 APK 到 Android 设备
2. 启动应用，授予网络权限
3. 点击右上角 `+` 手动添加摄像头（IP + 端口）
4. 或点击搜索图标自动发现局域网内的 HomeCam 设备
5. 连接成功后自动显示视频画面

### 手动添加

- **IP 地址**：HomeCam 设备在局域网中的 IP
- **端口**：HomeCam Web 服务端口（默认 8080）
- **名称**：可选，方便识别

## API 依赖

Homecam-TE 依赖 HomeCam 服务器端提供的 REST API：

| 端点 | 用途 |
|------|------|
| `GET /video` | MJPEG 实时视频流 |
| `GET /api/status` | 设备运行状态 |
| `GET /api/events` | 事件历史记录 |
| `GET /api/cameras` | 可用摄像头列表 |
| `GET /api/camera/switch` | 切换摄像头镜头 |
| `GET /api/camera/power` | 控制摄像头电源 |

## 项目结构

```
com.homecam.te/
├── HomeCamApp.kt           # Application 类
├── data/                   # 数据层
│   ├── CameraDatabase.kt   # Room 数据库
│   └── CameraRepository.kt # 连接管理器
├── network/                # 网络层
│   ├── ApiClient.kt        # REST 客户端
│   ├── MjpegClient.kt      # MJPEG 流解析
│   ├── EventPoller.kt      # 事件轮询
│   └── DiscoveryService.kt # UDP 发现
├── service/                # 服务层
│   └── AlertManager.kt     # 报警管理
├── model/                  # 数据模型
│   └── Models.kt
└── ui/                     # UI 层
    ├── MainViewModel.kt    # ViewModel 协调器
    ├── TEGridScreen.kt     # 主网格屏幕
    ├── CameraCard.kt       # 摄像头卡片
    ├── MjpegView.kt        # MJPEG 渲染组件
    ├── EventSheet.kt       # 事件弹窗
    ├── AddCameraDialog.kt  # 添加对话框
    ├── SettingsScreen.kt   # 设置页面
    └── theme/Theme.kt      # 主题
```

## 许可证

（待定）
