# 更新日志

## 0.1.1 - 2026-10-31

真机验证修复版（OnePlus PKH110 / Android 16 / 虎扑 8.2.66.10232）。

### 修复

- 网易易盾 NIS 加固下，`onPackageReady` 时真实 `com.hupu.*` DEX 尚未解密加载，
  原实现全部 `class not found`。新增 `DeferredInstall`：hook 壳 Application 的
  `attachBaseContext/onCreate`，在原方法执行完后安装；另有 100ms 轮询
  （壳 ClassLoader / 当前 Activity ClassLoader）兜底，最多 30 秒。
- 只在主进程安装 hook；`:pushcore`、`:privileged_process0` 不再刷 ClassNotFound。
- 适配版本更新为 **8.2.66.10232（12320）**。

### 验证

- 30 个锚点全部解析并注册。
- 实测命中：AD-01（后台预加载）、AD-02（热启动开屏）、AD-03 信息流（数十次）、
  AD-04 下拉刷新、AD-05 Banner、AD-06 浮层、AD-07 弹窗、AD-08 搜索框、AD-10 摇一摇、
  AD-11 冷启动免广告弹窗；无 FATAL。
- 冷启动未再出现开屏广告。

### 界面与默认值

- 关于页删除「模块互链」，新增「参考项目：QuietBox」与「源码」链接（fengwenhua/quiethupu）及
  https://github.com/yigenhuobah/quietbox 链接。
- PV-01 剪贴板广告默认开启；AD-11 激励视频/短剧默认拦截。

### 工程

- applicationId 改为 `io.github.fengwenhua.quiethupu`，`StatusProvider` authority 同步更新。
- 版本号集中到 `version.properties`。
- 新增 GitHub Actions：`build.yml`（单测/lint/debug）与 `release.yml`
  （tag 构建、release environment secrets 签名、证书 pin、GitHub Release、
  可选同步 Xposed-Modules-Repo）。
- 签名证书 SHA-256 固定在 `release-trust.properties`。
- 清理 QuietBox 遗留资源（AB 目录、未使用图标/颜色/字符串）与 `.DS_Store`。

## 0.1.0 - 2026-10-31

首个版本，基于 QuietBox 0.9.0 的工程骨架重写为虎扑（`com.hupu.games`）去广告模块。

### 适配

- 目标：虎扑 8.2.65.10161（12318）。
- 目标 APK 为网易易盾 NIS 加固；分析使用 r0dump 还原的 20 个运行时 DEX（约 101.5MB）。
- 广告位来自 `com.hupu.adver_boot / adver_feed / adver_creative / adver_banner /
  adver_float / adver_popup / adver_dialog / adver_project` 等模块。

### 功能

- AD-01 冷启动开屏：跳过 BootPreLoadManager#preLoad，HpSplashAd#load 主动回调 onError 走官方失败分支，show/showFromCache 兜底；华为开屏不注册。
- AD-02 热启动开屏：跳过 HpAdBootLifecycle#onResume。
- AD-03 信息流：FeedPageManager#getFeedConfig 返回 null，覆盖首页/推荐/吧内/帖子/比赛等页面。
- AD-04 下拉刷新、AD-05 Banner、AD-06 浮层、AD-07 弹窗、AD-08 搜索框：
  跳过对应 loadData/loadFromNet。
- AD-09 广告预加载断源：AdFeedPreLoadManager 三个入口。
- AD-10 摇一摇：跳过 HpFeedAd#initShake。
- AD-11 激励视频/短剧：拦截游戏与短剧的 `Manager.load` / `AdRewardVideo.show`，
  并关闭冷启动“看广告免广告”弹窗；默认开启，关闭后恢复原样。
- UI-01 更新弹窗：UpdateViewModel#showUpdateDialog 返回 Unit。
- PV-01 剪贴板广告：跳过 AdClipBoardManager#configClipBoard（默认开）。

### 已知限制

- 未在真机逐项验证；13 项功能仅完成静态核对、编译与 JVM 单测。
- 易盾抽取了部分方法指令（2%–10%），相关业务流程无法静态确认；模块只依赖完整的类/方法签名。
- 仅保证 8.2.65.10161，升级后按 skip fail-open。
