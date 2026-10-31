package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-05 Banner 广告。
 *
 * <p>虎扑有两套 Banner 实现：lonely（单个）与 mul（多个/轮播）。两者都在
 * loadFromNet() 里发请求并回调加载视图；跳过即不请求、不渲染广告。
 * mul 是可选实现，缺失时只记普通 skip，不影响 lonely。
 */
final class BannerAdHooks {

    private static final String LONELY_BANNER =
            "com.hupu.adver_banner.lonely.HpBannerAd";
    private static final String MUL_BANNER =
            "com.hupu.adver_banner.mul.HpMulBannerAd";

    private BannerAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.BANNER_AD_BLOCK;
        try {
            installOne(module, cl, f, LONELY_BANNER, true);
            installOne(module, cl, f, MUL_BANNER, false);
            HookState.note(f, "Banner 广告：lonely + mul 的 loadFromNet 已断源");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void installOne(XposedModule module, ClassLoader cl, Feature f,
                                   String className, boolean required) {
        Class<?> cls = required
                ? HookKit.findClass(module, f, cl, className)
                : HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method loadFromNet = HookKit.findMethodQuiet(cls, "loadFromNet");
        HookKit.install(module, f, shortName(className) + "#loadFromNet", loadFromNet,
                HookKit.skipVoid(module, f, shortName(className) + " request skipped"));
    }

    private static String shortName(String dotted) {
        int i = dotted.lastIndexOf('.');
        return i < 0 ? dotted : dotted.substring(i + 1);
    }
}
