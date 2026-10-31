package com.quiethupu.app.hooks;

import android.app.Application;
import android.view.ViewGroup;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-01 冷启动开屏广告。
 *
 * <p>虎扑的开屏广告有两条入口：
 * <ul>
 *   <li>首页 SplashFragment 直接 new HpSplashAd().load(listener)，成功后 show(container)；</li>
 *   <li>后台预加载 BootPreLoadManager.preLoad()，回前台由 BootPreLoadManager.show() 取缓存展示。</li>
 * </ul>
 *
 * <p>本类同时处理：
 * <ul>
 *   <li>BootPreLoadManager.show(...) 返回 null（不展示缓存广告）；</li>
 *   <li>BootPreLoadManager.preLoad() 不执行（不再后台预加载）；</li>
 *   <li>HpSplashAd.load(listener) 不请求，并主动回调 listener.onError，
 *       让 SplashFragment 走官方失败分支直接进主页；</li>
 *   <li>HpSplashAd.show / showFromCache 兜底不展示；</li>
 *   <li>HwSplashManager.start 不注册华为开屏。</li>
 * </ul>
 *
 * <p>不 hook 任何广告 SDK 内部类，也不修改请求参数；失败一律 fail-open。
 */
final class SplashAdHooks {

    private static final String PRELOAD_MANAGER =
            "com.hupu.adver_boot.manager.BootPreLoadManager";
    private static final String SPLASH_AD = "com.hupu.adver_boot.HpSplashAd";
    private static final String HW_SPLASH = "com.hupu.adver_boot.sdk.hw.HwSplashManager";
    private static final String AD_ERROR = "com.hupu.adver_base.constant.AdError";

    private SplashAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SPLASH_COLD;
        try {
            installPreLoadManager(module, cl, f);
            installSplashAd(module, cl, f);
            installHwSplash(module, cl, f);
            HookState.note(f, "冷启动开屏：BootPreLoadManager + HpSplashAd + HwSplashManager");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void installPreLoadManager(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClass(module, f, cl, PRELOAD_MANAGER);
        if (cls == null) {
            return;
        }
        // 不 hook show(...)：preLoad() 已被跳过，splashAd 字段保持为 null，
        // show() 原本就会返回 null。强行返回 null 反而可能在未判空的调用方触发 NPE。
        Method preLoad = HookKit.findMethodQuiet(cls, "preLoad");
        HookKit.install(module, f, "BootPreLoadManager#preLoad", preLoad,
                HookKit.skipVoid(module, f, "BootPreLoadManager#preLoad skipped"));
    }

    private static void installSplashAd(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClass(module, f, cl, SPLASH_AD);
        if (cls == null) {
            return;
        }
        Method load = HookKit.findByNameAndArity(cls, "load", 1);
        HookKit.install(module, f, "HpSplashAd#load", load, blockLoad(module, f, cl));

        Method show = HookKit.findMethodQuiet(cls, "show", ViewGroup.class);
        HookKit.install(module, f, "HpSplashAd#show", show,
                HookKit.skipVoid(module, f, "HpSplashAd#show blocked"));

        Method showFromCache = HookKit.findByShape(cls, "showFromCache", ViewGroup.class);
        HookKit.install(module, f, "HpSplashAd#showFromCache", showFromCache,
                HookKit.skipVoid(module, f, "HpSplashAd#showFromCache blocked"));
    }

    private static void installHwSplash(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, HW_SPLASH);
        if (cls == null) {
            return;
        }
        Method start = HookKit.findMethodQuiet(cls, "start", Application.class);
        HookKit.install(module, f, "HwSplashManager#start", start,
                HookKit.skipVoid(module, f, "HwSplashManager#start skipped"));
    }

    /**
     * 不请求开屏数据，同时主动回调加载监听器的 onError，让调用方走失败分支。
     * 回调失败时 proceed 原逻辑，宁可显示一次开屏，也不能让启动页卡死。
     */
    private static XposedInterface.Hooker blockLoad(XposedModule module, Feature f,
                                                    ClassLoader cl) {
        return chain -> {
            try {
                Object listener = chain.getArg(0);
                if (listener != null) {
                    if (!notifyLoadError(listener, cl)) {
                        // 拿不到监听器/错误码：放行，避免 SplashFragment 永远等不到回调。
                        HookKit.warn(module, f.id + " splash listener notify failed, proceed");
                        return chain.proceed();
                    }
                }
                HookKit.hit(module, f, "HpSplashAd#load blocked");
                return null;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error HpSplashAd#load", t);
                return chain.proceed();
            }
        };
    }

    private static boolean notifyLoadError(Object listener, ClassLoader cl) {
        try {
            Method onError = listener.getClass()
                    .getMethod("onError", int.class, String.class);
            onError.setAccessible(true);
            onError.invoke(listener, bootErrorCode(cl),
                    "quiet-hupu: splash ad skipped");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取官方 BOOT_AD_GETOTHER_FAIL 的 code；取不到时用 -1。 */
    private static int bootErrorCode(ClassLoader cl) {
        try {
            Class<?> errorClass = Class.forName(AD_ERROR, false, cl);
            Object constant = Enum.valueOf(errorClass.asSubclass(Enum.class),
                    "BOOT_AD_GETOTHER_FAIL");
            Object code = errorClass.getMethod("getCode").invoke(constant);
            if (code instanceof Integer) {
                return (Integer) code;
            }
        } catch (Throwable ignored) {
            // 版本变化时允许退化为 -1；SplashFragment 只把非成功当失败处理。
        }
        return -1;
    }
}
