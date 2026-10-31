package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-10 信息流摇一摇跳转。
 *
 * <p>HpFeedAd.initShake() 会注册加速度传感器，摇一摇后跳到广告落地页。
 * 广告本身已被 AD-03 拦掉，本功能额外避免传感器注册与误触跳转。
 * 开屏广告的摇一摇在广告 SDK 原生层，不在本钩子覆盖范围。
 */
final class ShakeHooks {

    private static final String FEED_AD = "com.hupu.adver_feed.HpFeedAd";

    private ShakeHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SHAKE_JUMP_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, FEED_AD);
            if (cls == null) {
                return;
            }
            Method initShake = HookKit.findMethodQuiet(cls, "initShake");
            HookKit.install(module, f, "HpFeedAd#initShake", initShake,
                    HookKit.skipVoid(module, f, "shake sensor not registered"));
            HookState.note(f, "摇一摇：HpFeedAd#initShake");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
