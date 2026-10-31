package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-06 浮层广告。
 *
 * <p>HpAdFloat（独立浮层）与 HpAdRvFloat（RecyclerView 浮层）都通过 loadData()
 * 发起请求并决定是否挂载浮层。跳过 loadData 并返回 this，避免调用方拿到 null。
 */
final class FloatAdHooks {

    private static final String FLOAT_AD = "com.hupu.adver_float.HpAdFloat";
    private static final String RV_FLOAT_AD = "com.hupu.adver_float.HpAdRvFloat";

    private FloatAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.FLOAT_AD_BLOCK;
        try {
            Class<?> floatAd = HookKit.findClass(module, f, cl, FLOAT_AD);
            if (floatAd != null) {
                Method loadData = HookKit.findMethodQuiet(floatAd, "loadData");
                HookKit.install(module, f, "HpAdFloat#loadData", loadData,
                        HookKit.returnThis(module, f, "float ad request skipped"));
            }
            Class<?> rvFloat = HookKit.findClassOptional(module, f, cl, RV_FLOAT_AD);
            if (rvFloat != null) {
                Method loadData = HookKit.findMethodQuiet(rvFloat, "loadData");
                HookKit.install(module, f, "HpAdRvFloat#loadData", loadData,
                        HookKit.returnThis(module, f, "rv float ad request skipped"));
            }
            HookState.note(f, "浮层广告：HpAdFloat + HpAdRvFloat");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
