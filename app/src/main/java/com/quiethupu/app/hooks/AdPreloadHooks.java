package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;
import java.util.HashMap;

import io.github.libxposed.api.XposedModule;

/**
 * AD-09 广告预加载断源。
 *
 * <p>虎扑会在页面打开前后预加载信息流广告（AdFeedPreLoadManager）。
 * 本功能不改变展示，只让预加载请求不再发出，减少后台流量与首屏等待。
 * 展示侧仍由 AD-03 的广告位配置兜底。
 */
final class AdPreloadHooks {

    private static final String PRELOAD =
            "com.hupu.adver_feed.manager.preload.AdFeedPreLoadManager";

    private AdPreloadHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.AD_PRELOAD_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, PRELOAD);
            if (cls == null) {
                return;
            }
            Method requestAfterPm = HookKit.findMethod(module, f, cls, "requestAfterPm",
                    String.class, int.class, HashMap.class);
            HookKit.install(module, f, "AdFeedPreLoadManager#requestAfterPm", requestAfterPm,
                    HookKit.skipVoid(module, f, "feed preload request skipped"));

            Method start = HookKit.findMethodQuiet(cls, "start");
            HookKit.install(module, f, "AdFeedPreLoadManager#start", start,
                    HookKit.skipVoid(module, f, "feed preload start skipped"));

            Method rate = HookKit.findMethodQuiet(cls, "startCommonRatePreRequest");
            HookKit.install(module, f, "AdFeedPreLoadManager#startCommonRatePreRequest", rate,
                    HookKit.skipVoid(module, f, "rate preload skipped"));

            HookState.note(f, "广告预加载：requestAfterPm/start/startCommonRatePreRequest");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
