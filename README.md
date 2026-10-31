# 虎扑静净 (QuietHupu)

面向虎扑 Android 客户端（`com.hupu.games`）的 LSPosed 去广告模块。基于
[libxposed](https://github.com/libxposed) Modern API 102，纯 Java 实现，独立 APK，
不需要改包、不需要 Xposed 旧 API。

> 当前版本 **0.1.1**，目标应用 **虎扑 8.2.66.10232（versionCode 12320，真机已验证）**。
> ⚠️ 本项目与虎扑官方无任何关联，仅供学习、研究和个人设备使用。
> 使用前请阅读文末的[免责声明](#免责声明)。

源码：<https://github.com/fengwenhua/quiethupu>

工程骨架、开关/诊断/回传链路来自 [静盒 (QuietBox)](https://github.com/yigenhuobah/quietbox)
（MIT）。Hook 目标按 8.2.65.10161 的运行时 DEX 编写，并在 8.2.66.10232 真机验证：
30 个锚点全部解析成功，核心广告位均有命中。

---

## 一、目标 App 的广告画像（为什么是这些开关）

分析对象是 8.2.65.10161 的运行时 DEX（真机测试版本为 8.2.66.10232，全部锚点仍可解析；原 APK 被网易易盾 NIS 加固，壳内只有 30 个类；
本仓库分析所用的 20 个真实 DEX 由 r0dump 从内存还原，见文末“分析来源”）。

### 1. 广告 SDK

| SDK | 包前缀 | 说明 |
|---|---|---|
| 穿山甲 / Pangle | `com.byazt`、`com.bytedance.pangle` | 开屏、信息流、激励视频，Zeus 插件 |
| 优量汇 GDT | `com.qq.e`、`com.android.gdt` | 开屏、信息流、Banner |
| Noah | `com.noah.*` | 虎扑广告聚合/投放 SDK |
| Lamps | `com.lamps.sdk.*` | 广告聚合层 |
| 汇川 | 日志/配置中的 `hc` | 开屏/激励视频聚合适配层 |

### 2. 广告位模块（DEX 中的真实包）

| 包 | 广告位 |
|---|---|
| `com.hupu.adver_boot` | 冷启动/热启动开屏、华为开屏 |
| `com.hupu.adver_feed` | 信息流插卡、预加载 |
| `com.hupu.adver_creative` | 下拉刷新广告、搜索框广告、通用创意 |
| `com.hupu.adver_banner` | 单 Banner / 多 Banner（轮播） |
| `com.hupu.adver_float` | 悬浮贴片、RecyclerView 浮层 |
| `com.hupu.adver_popup` / `adver_dialog` | 广告弹窗 / 插屏 |
| `com.hupu.adver_project` | 首页、推荐、热榜、吧内、帖子回复、比赛、评分、话题、标签广场等页面的广告编排 |
| `com.hupu.adver_game` / `adver_drama` | 激励视频、短剧解锁 |
| `com.hupu.adver_base` | 广告配置、SDK 工具、剪贴板广告、下载 |

### 3. 会员权益里的“免广告”

本地 DEX 里能确认的文案：

- `立享免广告等多项特权`（`VipTipsV2View`，虎扑会员/红单会员推广卡）
- `只需9.9元开通会员，立享纯净无广体验`
- `免广告特权`
- `免广告权益已生效` / `免广告权益已发放`
- `免广告加载超时/失败/请求失败`

前三条是**会员权益**的推广文案；后几条来自
`com.hupu.adver_drama.service.AdCommonRewardFreeService` 与
`com.hupu.adver_base.shield.AdNewMoreDialog`，是“看激励视频换 N 小时免广告”的临时权益，
不是会员。

会员页的完整权益列表由服务端下发，客户端没有“VIP → 关闭全部广告”的单一本地开关；
广告是否下发由服务端按权益决定。因此本模块不去伪造会员状态，而是**在客户端逐个广告位拦断**，
效果等价于“免广告”会员在客户端能感知到的部分。

### 4. 最终开关清单

| 编号 | 功能 | 默认 | 实现的挂载点 |
|---|---|---|---|
| AD-01 | 冷启动开屏广告 | 开 | `BootPreLoadManager#preLoad`、`HpSplashAd#load/#show/#showFromCache`、`HwSplashManager#start` |
| AD-02 | 热启动开屏 | 开 | `HpAdBootLifecycle#onResume` |
| AD-03 | 信息流广告 | 开 | `FeedPageManager#getFeedConfig` 返回 null |
| AD-04 | 下拉刷新广告 | 开 | `HpAdRefresh#loadData/#loadFromNet` |
| AD-05 | Banner 广告 | 开 | `HpBannerAd#loadFromNet`、`HpMulBannerAd#loadFromNet` |
| AD-06 | 浮层广告 | 开 | `HpAdFloat#loadData`、`HpAdRvFloat#loadData` |
| AD-07 | 弹窗广告 | 开 | `HpAdRvPopup#loadData`、`HpAdDialog#loadData` |
| AD-08 | 搜索框广告 | 开 | `HpRvSearchIconAd#loadData` |
| AD-09 | 广告预加载断源 | 开 | `AdFeedPreLoadManager#requestAfterPm/#start/#startCommonRatePreRequest` |
| AD-10 | 摇一摇跳转 | 开 | `HpFeedAd#initShake` |
| AD-11 | 激励视频 / 短剧广告 | 开 | 游戏/短剧 `AdRewardVideoManager#load`、`Hp*Ad#loadRewardVideo`、`AdRewardVideo#show`；冷启动“看广告免广告”弹窗不弹 |
| UI-01 | 更新弹窗 | 开 | `UpdateViewModel#showUpdateDialog` 返回 `Unit` |
| PV-01 | 剪贴板广告 | 开 | `AdClipBoardManager#configClipBoard` |

实现原则与 QuietBox/QuietMap 一致：

- 只 hook 虎扑自己的业务类，不碰广告 SDK 内部、不碰 `android.*` / `java.*`；
- 每个 `intercept` 整段 `try/catch(Throwable)`，异常时 `chain.proceed()` 放行；
- 链式 `loadData()` 返回 `this` 而不是 null，避免调用方 NPE；
- 开屏不请求时主动回调 `HpSplashLoadListener.onError`，让 SplashFragment 走官方失败分支，
  不会把启动页卡死；
- 任一挂载点缺失只影响对应功能，诊断页显示 `skip` 原因，其余功能继续工作。

---

## 二、安装与使用

1. 从 Releases 下载 `quiethupu-<版本>.apk` 并安装。
2. LSPosed 管理器中启用「虎扑静净」，作用域勾选 **虎扑**（`com.hupu.games`）。
3. 强制停止虎扑后重新打开。
4. 打开模块 App，状态卡显示「已激活」即注入成功。
5. 按需开关；改动后**强制停止并重启虎扑**生效（remote prefs 在进程启动时读取）。

要求：Android 10+（minSdk 29）、支持 libxposed API 102 的 LSPosed / Vector。

---

## 三、代码结构

```
app/src/main/java/com/quiethupu/app/
├── QuietHupuModule.java        # libxposed 入口，仅目标包 + 每进程安装一次
├── Feature.java                # 功能清单（编号/key/文案/默认值/分组）
├── Config.java                 # 目标进程读 remote prefs
├── ConfigStore.java            # 模块 App 写 remote prefs（异步 commit + 回滚）
├── HookState.java              # 目标进程内逐功能 reg/hit/skip/critical
├── hooks/
│   ├── Hooks.java              # Feature → Installer 注册表，逐功能 fail-open
│   ├── HookKit.java            # 反射定位、PROTECTIVE hook、hit 计数
│   ├── SplashAdHooks.java      # AD-01
│   ├── HotSplashHooks.java     # AD-02
│   ├── FeedAdHooks.java        # AD-03
│   ├── RefreshAdHooks.java     # AD-04
│   ├── BannerAdHooks.java      # AD-05
│   ├── FloatAdHooks.java       # AD-06
│   ├── PopupAdHooks.java       # AD-07
│   ├── SearchIconAdHooks.java  # AD-08
│   ├── AdPreloadHooks.java     # AD-09
│   ├── ShakeHooks.java         # AD-10
│   ├── RewardVideoHooks.java   # AD-11 激励视频/短剧
│   ├── UpdateDialogHooks.java  # UI-01
│   ├── ClipboardAdHooks.java   # PV-01
│   └── Report.java             # 目标进程 → 模块 App 诊断回传
├── StatusProvider.java         # 模块 App 侧接收诊断数据（校验调用方 UID）
├── ReportProtocol.java         # 诊断协议纯 Java 逻辑（可单测）
├── SettingsFragment.java       # 设置页：状态卡 + 横幅 + 开关
├── DiagnosticsFragment.java    # 诊断页：逐功能 registered/hit/skip
├── AboutFragment.java          # 关于页
└── MainActivity.java           # 3 tab 外壳（设置/诊断/关于）
```

诊断链路：目标进程 `HookState` → `hooks/Report` 每 3 秒（内容变化时）/ 60 秒心跳
→ `StatusProvider`（校验调用方必须是 `com.hupu.games`）→ 模块 App 本地
`status_report` → 诊断页与失败横幅。不联网、不上传。

---

## 四、适配与已知限制

- **适配 8.2.66.10232（12320）**，已在真机验证：30 个锚点全部注册，
  AD-01/02/03/04/05/06/07/08/10/11 均有命中，无 FATAL。AD-09/UI-01/PV-01
  已注册，但本次测试场景没有触发；虎扑升级后若类名/签名变化，模块会按 `skip` 失败而不是崩溃。
- **NIS 加固的安装时机**：onPackageReady 时进程里只有壳类，真实 DEX 要等
  `MyApplication.attachBaseContext` 里的 `MyJni.load(...)` 之后才就绪。模块通过
  `DeferredInstall` 在壳 Application 的 `attachBaseContext/onCreate` 之后安装 hook，
  并用 100ms 轮询（壳 ClassLoader / 当前 Activity ClassLoader）兜底，最多 30 秒。
  这解决了早期版本“全部 class not found、一个 hook 都没挂上”的问题。
- 虎扑原 APK 有网易易盾 NIS 加固，运行时 DEX 中约 2%–10% 的方法指令被抽取（表现为
  指令全 0，jadx 只能反编译出空方法体）。本模块依赖的是**类名/方法签名**，这些在 DEX
  方法表中完整保留；但个别业务流程（例如 SplashFragment 的具体分支）无法静态确认，
  开屏采用“主动回调 onError”的保守策略。
- 广告点击外跳、个性化广告设置、埋点抑制未实现：广告位已拦断，点击链路自然不再触发。
- 激励视频/短剧已纳入拦截：`Manager.load` 直接回调 `loadFail`，`AdRewardVideo.show` 兜底
  回调 `onAdClose`。代价是“看广告领奖 / 看广告解锁短剧”会失败，服务端不会因本地拦截而发奖；
  如果仍需要这些权益，请在模块里关闭 AD-11。
- 模块不修改账号数据、不拦截网络请求、不上传任何信息；仅 Hook 目标应用进程。
- 与其它 Hook 虎扑的模块可能冲突，建议只保留一个。

---

## 五、分析来源与参考项目

- 分析 APK：`com.hupu.games` 8.2.65.10161（真机适配 8.2.66.10232），网易易盾 NIS 加固，签名证书 CN=Jerry Ching。
- 分析材料：r0dump 运行时 DEX 转储（20 个应用 DEX，约 101.5MB），经 host-repair 修复后
  用 jadx 1.5.6 反编译。关键 DEX：`adver_boot/adver_base/adver_banner`（000016）、
  `adver_feed/adver_float/adver_popup/adver_dialog/adver_game`（000017）、
  `adver_project`（000018）、`com.hupu.user`（000011）、`comp_basic_update`（000006）。
- 工程参考：
  - **静盒 (QuietBox)**：Feature/Config/HookState/Report/StatusProvider 骨架、remote prefs
    开关、逐功能诊断与失败横幅、`PROTECTIVE` fail-open 约定。
  - **静途 (QuietMap)**：Feature → Installer 注册表完整性校验、“零成功挂载才标 critical”的
    收尾判定；其 DexKit 方案用于混淆类名，本模块的虎扑广告类名未混淆，故未引入 DexKit。
  - **贴吧静净 (TiebaQuiet)**：锚点表 + 运行时反射校验、诊断状态机与
    `FeedCardSweeper` 展示层兜底。本模块选择在广告位配置/请求层断源，比逐个隐藏
    ViewHolder 更稳定，因此没有移植 Sweeper。

### 锚点体检

```bash
python3 tools/check-anchors.py <还原后的 DEX 目录>
```

脚本只解析 DEX 方法表，不执行目标代码。2026-10-31 对 8.2.65.10161 的 20 个应用 DEX
体检结果：**30 个锚点全部 OK，0 个必需锚点缺失**。

---

## 六、构建

工具链：AGP 9.4.1 + Gradle 9.7.1（wrapper）+ JDK 17 + compileSdk 37 / minSdk 29 / targetSdk 36。
`app/build.gradle` 强制 `androidx.recyclerview:recyclerview:1.4.0`，用于兼容本机离线缓存
（material/preference 声明的 1.1.0 未缓存），有网环境同样适用。

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

本次提交已在本机验证：**76 个 JVM 单测通过，debug APK 构建成功（约 13.6 MB）**。
release 读取仓库根目录 `keystore.properties`（不入库），缺失时 `assembleRelease` 直接失败，
不会产出 debug 签名包。

单测覆盖：Feature 编号/key/默认值、prefs.xml 与 Feature 一致性、Hooks 注册表完整性、
Config 回退路径、HookState 并发计数与状态隔离、Report 诊断协议。

---

## 许可证

MIT。工程骨架来自 [QuietBox](https://github.com/yigenhuobah/quietbox)（MIT）；
本仓库的所有 Hook 目标与文案均来自对虎扑 8.2.65 APK 的独立分析。

## 免责声明

- 本项目仅供**学习、研究和个人设备使用**，不得用于商业用途、二次售卖或批量分发。
- 本项目与虎扑及其运营方**无任何隶属、授权或合作关系**。
- 本项目**不包含**目标应用的任何代码、资源或商标素材；不修改目标应用安装包；
  不拦截网络请求；不提供代理、后台服务或常驻进程。
- 使用本模块可能**违反目标应用的用户服务协议**。请自行确认使用场景是否合规。
- Hook 可能导致应用异常、崩溃、数据丢失或设备不稳定，**所有风险由使用者自行承担**。
- 如权利人认为本项目侵犯其权益，请通过 Issue 联系，作者会立即停止分发并删除相关内容。
- **下载后请于 24 小时内删除。如不同意以上任何一条，请不要下载或使用。**
