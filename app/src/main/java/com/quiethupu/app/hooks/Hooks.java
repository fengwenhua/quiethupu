package com.quiethupu.app.hooks;

import android.util.Log;

import com.quiethupu.app.Config;
import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * 统一在 onPackageReady（已确认包名 com.hupu.games）之后调用。
 * 单个功能的失败不得影响其余功能，也不得让模块加载失败。
 */
public final class Hooks {

    private static final String TAG = "QuietHupu";

    /** 单个 Feature 的安装动作；module/cl/config/feature 统一由 installOne 传入。 */
    @FunctionalInterface
    interface Installer {
        void install(XposedModule module, ClassLoader cl, Config config, Feature feature);
    }

    /** Feature -> 安装器。blocked Feature 不在表内。 */
    static final EnumMap<Feature, Installer> INSTALLERS = createInstallers();

    private Hooks() {
    }

    private static EnumMap<Feature, Installer> createInstallers() {
        EnumMap<Feature, Installer> map = new EnumMap<>(Feature.class);

        map.put(Feature.SPLASH_COLD,
                (module, cl, config, f) -> SplashAdHooks.install(module, cl));
        map.put(Feature.SPLASH_HOT,
                (module, cl, config, f) -> HotSplashHooks.install(module, cl));
        map.put(Feature.FEED_AD_FILTER,
                (module, cl, config, f) -> FeedAdHooks.install(module, cl));
        map.put(Feature.REFRESH_AD_BLOCK,
                (module, cl, config, f) -> RefreshAdHooks.install(module, cl));
        map.put(Feature.BANNER_AD_BLOCK,
                (module, cl, config, f) -> BannerAdHooks.install(module, cl));
        map.put(Feature.FLOAT_AD_BLOCK,
                (module, cl, config, f) -> FloatAdHooks.install(module, cl));
        map.put(Feature.POPUP_AD_BLOCK,
                (module, cl, config, f) -> PopupAdHooks.install(module, cl));
        map.put(Feature.SEARCH_ICON_AD_BLOCK,
                (module, cl, config, f) -> SearchIconAdHooks.install(module, cl));
        map.put(Feature.AD_PRELOAD_BLOCK,
                (module, cl, config, f) -> AdPreloadHooks.install(module, cl));
        map.put(Feature.SHAKE_JUMP_BLOCK,
                (module, cl, config, f) -> ShakeHooks.install(module, cl));
        map.put(Feature.REWARD_VIDEO_BLOCK,
                (module, cl, config, f) -> RewardVideoHooks.install(module, cl));
        map.put(Feature.UPDATE_DIALOG_BLOCK,
                (module, cl, config, f) -> UpdateDialogHooks.install(module, cl));
        map.put(Feature.CLIPBOARD_AD_BLOCK,
                (module, cl, config, f) -> ClipboardAdHooks.install(module, cl));

        return map;
    }

    /** 注册表键快照，供单测断言「非 blocked 全覆盖、blocked 不安装」。 */
    static Set<Feature> installerKeys() {
        EnumSet<Feature> keys = EnumSet.noneOf(Feature.class);
        keys.addAll(INSTALLERS.keySet());
        return Collections.unmodifiableSet(keys);
    }

    /**
     * @param mainProcess 当前是否为主进程（进程名 == 包名）。诊断回传只在主进程启动，
     *                    避免子进程未装全 hook 时把功能误报为失败。
     */
    public static void installAll(XposedModule module, ClassLoader cl, boolean mainProcess) {
        Config config;
        try {
            config = Config.load(module);
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG, "config load failed", t);
            return;
        }
        if (!config.anyEnabled()) {
            module.log(Log.INFO, TAG, "all features disabled, nothing to hook");
            return;
        }
        if (mainProcess) {
            Report.start();
        } else {
            module.log(Log.INFO, TAG, "non-main process, status report disabled");
        }
        for (Feature f : Feature.values()) {
            if (f.blocked()) {
                module.log(Log.INFO, TAG, f.id + " blocked: " + f.blockedReason);
                continue;
            }
            if (!config.enabled(f)) {
                module.log(Log.INFO, TAG, f.id + " disabled");
                continue;
            }
            HookState.enable(f);
            installOne(module, cl, f, config);
        }
    }

    private static void installOne(XposedModule module, ClassLoader cl, Feature f, Config config) {
        try {
            Installer installer = INSTALLERS.get(f);
            if (installer == null) {
                module.log(Log.ERROR, TAG, f.id + " installer missing");
                HookState.markNotFound(f, "未注册安装器");
                return;
            }
            installer.install(module, cl, config, f);
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG, f.id + " install threw", t);
            HookState.markNotFound(f, "安装异常");
        } finally {
            // 零成功挂载 + 至少一条 skip：整个功能不可用，升级为 critical，
            // 让异常横幅能反映版本不兼容。
            if (HookState.labels(f).isEmpty() && HookState.skipReason(f) != null) {
                HookState.markNotFoundCritical(f, "无可用挂载点");
            }
        }
    }
}
