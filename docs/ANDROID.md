# Android 使用说明

`apps/android` 是原生 Java 实现，既能作为普通横屏应用打开，也注册为 Android `DreamService` 屏保。它没有 Gradle 依赖，构建脚本直接调用 Android SDK 工具。

## 环境

- JDK 17；
- Android SDK Platform 35；
- Android Build Tools 35.0.0；
- `ANDROID_HOME` 指向 Android SDK。

默认脚本也会尝试使用 macOS Homebrew 的 `/opt/homebrew/opt/openjdk@17`。

## 配置服务地址

编辑：

```text
apps/android/res/values/strings.xml
```

模拟器访问宿主机：

```xml
<string name="server_url">http://10.0.2.2:8790</string>
```

真实设备访问同一局域网中的电脑：

```xml
<string name="server_url">http://192.168.x.x:8790</string>
```

电脑端同时需要监听局域网地址：

```bash
HOST=0.0.0.0 npm start
```

先在设备浏览器中打开 `http://电脑地址:8790/health`，确认能看到 `{"ok":true}`。

## 构建

```bash
bash apps/android/build.sh
```

调试 APK 位于：

```text
apps/android/build/dist/yunhai-shijian-debug.apk
```

构建脚本会在本地创建调试签名，相关文件已被 `.gitignore` 排除。正式分发必须使用自己的发布签名与版本流程。

## 安装

```bash
adb install -r apps/android/build/dist/yunhai-shijian-debug.apk
```

普通应用可直接从桌面启动。屏保入口位于设备的显示或屏幕保护设置中，名称为“云海诗鉴”。不同厂商可能隐藏 Android 系统的 `DreamService` 设置；这属于设备固件限制。

## 终端行为

- 应用打开时保持横屏和常亮；
- 每 15 分钟读取一次云图、诗签和码表；
- 诗签可拖动并记住位置；
- 单击诗签或码表可展开对应面板；
- 应用不会开机自启、后台守护或强行夺取前台。

## 网络说明

示例 Manifest 允许明文 HTTP，便于封闭局域网内快速部署。若设备跨公网访问，请改用 HTTPS、关闭不必要的明文流量，并在反向代理层增加认证和访问控制。
