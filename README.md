# 拼图满绩 (QLU-Test-Item-Repository)

一款专为大学生（主要是齐鲁工业大学 QLU）设计的期末备考辅助应用。通过便捷的方式获取、浏览和下载历年真题与复习资料，助你轻松满绩！

## ✨ 功能特性

*   **📚 海量题库**：直接对接 GitHub 仓库数据源 (`Torchman005/QLU-Test-Item-Files`)，实时获取最新的复习资料。
*   **📂 文件浏览**：清晰的目录结构，支持多级文件夹浏览，快速定位所需科目。
*   **⬇️ 便捷下载**：
    *   支持文件下载到本地 (`Downloads/tests` 目录)。
    *   调用系统下载管理器，稳定可靠。
    *   智能识别本地文件，已下载文件可直接打开预览。
    *   支持长按重新下载。
*   **📤 一键分享**：下载后的文件可直接分享至 QQ 或微信，方便同学间互传资料。
*   **🌗 个性化体验**：
    *   支持深色/浅色模式切换。
    *   可自定义主题颜色。
    *   下拉刷新数据。
*   **🚀 现代化 UI**：基于 Jetpack Compose 构建，界面简洁美观，操作流畅。

## 🛠️ 技术栈

*   **语言**：Kotlin
*   **UI 框架**：Jetpack Compose (Material Design 3)
*   **网络请求**：Retrofit + Gson
*   **图片加载**：Coil
*   **架构**：MVVM
*   **构建工具**：Gradle (Kotlin DSL)

## 📱 安装与使用

### 编译运行
1.  克隆本项目到本地：
    ```bash
    git clone https://github.com/Torchman005/QLU-Test-Item-Repository.git
    ```
2.  使用 Android Studio 打开项目。
3.  等待 Gradle 同步完成。
4.  连接 Android 设备或模拟器，点击运行。

### 下载使用
请前往 [Releases](https://github.com/Torchman005/QLU-Test-Item-Repository/releases) 页面下载最新版本的 APK 安装包。

## 🤝 贡献与反馈

数据源来自 [QLU-Test-Item-Files](https://github.com/Torchman005/QLU-Test-Item-Files)。如果你有更多的复习资料，欢迎提交 PR 或 Issue。

*   **应用开源地址**：[QLU-Test-Item-Repository](https://github.com/Torchman005/QLU-Test-Item-Repository)
*   **资料仓库地址**：[QLU-Test-Item-Files](https://github.com/Torchman005/QLU-Test-Item-Files)

## 📄 开源许可

本项目采用 [Apache License 2.0](LICENSE) 开源许可证。

---
Developed with ❤️ by Luminous & Trae AI.
