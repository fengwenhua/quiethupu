package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-08 搜索框图标广告。
 *
 * <p>HpRvSearchIconAd 会在列表顶部搜索栏右侧挂一个广告图标，
 * loadData() 负责请求与挂载。跳过并返回 this 即可，搜索功能本身不受影响。
 */
final class SearchIconAdHooks {

    private static final String SEARCH_ICON_AD =
            "com.hupu.adver_creative.search.HpRvSearchIconAd";

    private SearchIconAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SEARCH_ICON_AD_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, SEARCH_ICON_AD);
            if (cls == null) {
                return;
            }
            Method loadData = HookKit.findMethodQuiet(cls, "loadData");
            HookKit.install(module, f, "HpRvSearchIconAd#loadData", loadData,
                    HookKit.returnThis(module, f, "search icon ad request skipped"));
            HookState.note(f, "搜索框广告：HpRvSearchIconAd#loadData");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
