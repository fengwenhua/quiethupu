package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-04 下拉刷新广告。
 *
 * <p>HpAdRefresh.loadData() 会把广告挂到 HpRefreshLayout 的刷新回调上，
 * 下拉时请求并展示全屏/半屏广告。跳过 loadData（返回 this 保持链式调用非空），
 * 同时兜底跳过 loadFromNet，刷新行为本身不受影响。
 */
final class RefreshAdHooks {

    private static final String REFRESH_AD = "com.hupu.adver_creative.refresh.HpAdRefresh";

    private RefreshAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.REFRESH_AD_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, REFRESH_AD);
            if (cls == null) {
                return;
            }
            Method loadData = HookKit.findMethodQuiet(cls, "loadData");
            HookKit.install(module, f, "HpAdRefresh#loadData", loadData,
                    HookKit.returnThis(module, f, "refresh ad not attached"));

            Method loadFromNet = HookKit.findMethodQuiet(cls, "loadFromNet");
            HookKit.install(module, f, "HpAdRefresh#loadFromNet", loadFromNet,
                    HookKit.skipVoid(module, f, "refresh ad request skipped"));
            HookState.note(f, "下拉刷新广告：HpAdRefresh#loadData/loadFromNet");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
