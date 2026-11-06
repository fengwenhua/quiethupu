package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-12 插屏广告（8.2.67 新增的 adver_interstitial 模块）。
 *
 * <p>H5 页面通过 {@code AdInterstitialAbility#openInterstitial} 创建
 * {@code HpInterstitialAd} 并调用 {@code load()}，加载成功后由
 * {@code show(response, listener)} 展示。本类在两层拦截：
 * <ul>
 *   <li>{@code HpInterstitialAdCore#loadFromNet()} 不请求；反射核心对象里的
 *       {@code loadListener} 回调 {@code loadFail}，让 H5 桥接层结束流程并回传失败；</li>
 *   <li>{@code HpInterstitialAd#show(...)} 作为兜底不展示，回调
 *       {@code onShowFail}（不行再退回 {@code onAdClose}）。</li>
 * </ul>
 *
 * <p>回调失败时 load 仍不执行（避免广告请求），show 则 proceed 以 fail-open。
 */
final class InterstitialAdHooks {

    private static final String CORE = "com.hupu.adver_interstitial.HpInterstitialAdCore";
    private static final String AD = "com.hupu.adver_interstitial.HpInterstitialAd";
    private static final String AD_ERROR = "com.hupu.adver_base.constant.AdError";

    private InterstitialAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.INTERSTITIAL_AD_BLOCK;
        try {
            Class<?> core = HookKit.findClass(module, f, cl, CORE);
            if (core != null) {
                Method load = HookKit.findMethodQuiet(core, "loadFromNet");
                HookKit.install(module, f, "HpInterstitialAdCore#loadFromNet", load,
                        failLoad(module, f, cl));
            }

            Class<?> ad = HookKit.findClassOptional(module, f, cl, AD);
            if (ad != null) {
                Method show = HookKit.findByNameAndArity(ad, "show", 2);
                HookKit.install(module, f, "HpInterstitialAd#show", show,
                        failShow(module, f, cl));
            }
            HookState.note(f, "插屏广告：HpInterstitialAdCore#loadFromNet + HpInterstitialAd#show");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static XposedInterface.Hooker failLoad(XposedModule module, Feature f,
                                                   ClassLoader cl) {
        return chain -> {
            try {
                Object core = chain.getThisObject();
                Object listener = field(core, "loadListener");
                if (listener != null) {
                    notifyLoadFail(listener, errorCode(cl));
                }
                HookKit.hit(module, f, "HpInterstitialAdCore#loadFromNet blocked");
                return null;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error loadFromNet", t);
                return chain.proceed();
            }
        };
    }

    private static XposedInterface.Hooker failShow(XposedModule module, Feature f,
                                                   ClassLoader cl) {
        return chain -> {
            try {
                Object listener = chain.getArg(1);
                if (listener != null) {
                    int code = errorCode(cl);
                    if (notifyShowFail(listener, code)) {
                        HookKit.hit(module, f, "HpInterstitialAd#show blocked");
                        return null;
                    }
                    if (notifyAdClose(listener)) {
                        HookKit.hit(module, f, "HpInterstitialAd#show blocked (onAdClose)");
                        return null;
                    }
                }
                HookKit.warn(module, f.id + " show listener missing, proceed");
                return chain.proceed();
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error show", t);
                return chain.proceed();
            }
        };
    }

    private static Object field(Object target, String name) {
        for (Class<?> c = target == null ? null : target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                // 继续沿父类找
            }
        }
        return null;
    }

    private static void notifyLoadFail(Object listener, int code) {
        try {
            Method m = listener.getClass().getMethod("loadFail", int.class, String.class);
            m.setAccessible(true);
            m.invoke(listener, code, "quiet-hupu: interstitial ad blocked");
        } catch (Throwable ignored) {
            // 回调失败不影响拦截本身
        }
    }

    private static boolean notifyShowFail(Object listener, int code) {
        try {
            Method m = listener.getClass().getMethod("onShowFail", int.class, String.class);
            m.setAccessible(true);
            m.invoke(listener, code, "quiet-hupu: interstitial ad blocked");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean notifyAdClose(Object listener) {
        try {
            Method m = listener.getClass().getMethod("onAdClose");
            m.setAccessible(true);
            m.invoke(listener);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取官方 FEED_AD_PID_NULL 的 code；取不到用 -1。 */
    private static int errorCode(ClassLoader cl) {
        try {
            Class<?> errorClass = Class.forName(AD_ERROR, false, cl);
            Object constant = Enum.valueOf(errorClass.asSubclass(Enum.class), "FEED_AD_PID_NULL");
            Object code = errorClass.getMethod("getCode").invoke(constant);
            if (code instanceof Integer) {
                return (Integer) code;
            }
        } catch (Throwable ignored) {
            // 版本变化时允许退化为 -1
        }
        return -1;
    }
}
