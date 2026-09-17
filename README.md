# Clean 清理

一款免费无广告的安卓存储空间清理工具。无任何会员/付费功能，安装即用全部功能。

## 功能

- **垃圾清理**：扫描并清理缓存、日志、临时文件、缩略图缓存、空文件
- **微信/QQ 深度清理**：清理微信、QQ 的聊天图片、视频、语音与缓存
- **大文件**：找出占用空间最大的文件（>20MB）
- **重复文件**：按名称+大小识别重复文件
- **相似图片**：按尺寸+大小识别相似图片
- **安装包清理**：清理残留的 APK 安装包
- **卸载残留**：清理已卸载应用的遗留数据目录
- **空文件夹**：清理空目录
- **最新/最旧文件**：按修改时间列出最近/最早修改的大文件
- **应用管理**：查看各应用的外部数据占用并可一键清理
- **文件浏览器**：浏览、勾选、删除所有文件

## 系统要求

- Android 7.0 (API 24) 及以上
- Android 11+ 需要授予「所有文件访问」权限（仅用于本地文件操作，无网络上传）

## 下载

最新版本 APK 见 [Releases](https://github.com/138849070/clean/releases)，直链：

- [Clean-1.1.0.apk](https://github.com/138849070/clean/releases/download/v1.1.0/Clean-1.1.0.apk)

## 构建

```bash
# 环境：JDK 17 + Android SDK (platform 34, build-tools 34.0.0)
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/app-release-unsigned.apk（需自行签名）
```

## 安装

下载 APK 传到手机后直接安装。首次打开按引导授予存储权限即可。
