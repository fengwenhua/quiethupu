package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-02 热启动开屏（后台切回前台后的开屏广告）。
 *
 * <p>HpAdBootLifecycle.onResume 在满足后台时长阈值后启动 HpAdHotBootActivity，
 * 这是热启动开屏的唯一入口。直接跳过该方法即可，不影响其他生命周期逻辑。
 */
final class HotSplashHooks {

    private static final String LIFECYCLE = "com.hupu.adver_boot.hot.HpAdBootLifecycle";

    private HotSplashHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SPLASH_HOT;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, LIFECYCLE);
            if (cls == null) {
                return;
            }
            Method onResume = HookKit.findByNameAndArity(cls, "onResume", 1);
            HookKit.install(module, f, "HpAdBootLifecycle#onResume", onResume,
                    HookKit.skipVoid(module, f, "hot splash activity not launched"));
            HookState.note(f, "热启动开屏：跳过 HpAdBootLifecycle#onResume");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
