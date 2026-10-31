package com.quiethupu.app;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.quiethupu.app.hooks.Hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 延迟安装 hook。
 *
 * <p>虎扑 APK 被网易易盾 NIS 加固：onPackageReady 时进程里只有 30 个
 * {@code com.netease.nis.wrapper} 壳类，真实的 {@code com.hupu.*} DEX 要等
 * {@code MyApplication.attachBaseContext} 里调用 {@code MyJni.load(...)} 之后
 * 才由 NIS 解密/注入到 ClassLoader。若在 onPackageReady 直接 {@code Class.forName}
 * 会全部 ClassNotFound（真机日志已确认）。
 *
 * <p>因此本类：
 * <ul>
 *   <li>hook 壳 Application 的 attachBaseContext / onCreate，在原方法执行完（此时
 *       真实 DEX 已就绪）后再尝试安装；</li>
 *   <li>同时起一个 100ms 轮询线程，依次尝试壳 ClassLoader、当前 Activity 的
 *       ClassLoader，最多 30 秒，作为兜底；</li>
 *   <li>用真实类（HupuApplication / MainActivity / HpSplashAd）做探针，探针能加载
 *       时才认为 payload 就绪，并用探针自己的 ClassLoader 安装全部 hook；</li>
 *   <li>只安装一次，失败回滚标记，下一轮轮询可重试。</li>
 * </ul>
 */
final class DeferredInstall {

    private static final String TAG = "QuietHupu";
    private static final String SHELL_APP = "com.netease.nis.wrapper.MyApplication";

    /** 任一能加载，就说明 NIS 的真实 DEX 已经就绪。 */
    private static final String[] PROBES = {
            "com.hupu.games.HupuApplication",
            "com.hupu.games.main.MainActivity",
            "com.hupu.adver_boot.HpSplashAd",
    };

    private static final AtomicBoolean sArmed = new AtomicBoolean();
    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    private DeferredInstall() {
    }

    static void start(XposedModule module, ClassLoader shellCl) {
        if (!sArmed.compareAndSet(false, true)) {
            return;
        }
        module.log(Log.INFO, TAG, "deferred install armed, shellLoader=" + shellCl);
        armShellApplication(module, shellCl);
        startPolling(module, shellCl);
        // 立即试一次：某些设备/版本 NIS 会把 payload 直接加到壳 ClassLoader，
        // 此时 onPackageReady 就能解析到真实类。
        tryInstall(module, shellCl, "package-ready");
    }

    private static void armShellApplication(XposedModule module, ClassLoader shellCl) {
        try {
            Class<?> shellApp = Class.forName(SHELL_APP, false, shellCl);
            installAfter(module, shellApp, "attachBaseContext",
                    new Class<?>[]{Context.class}, "shell attachBaseContext");
            installAfter(module, shellApp, "onCreate",
                    new Class<?>[]{}, "shell onCreate");
            module.log(Log.INFO, TAG, "armed shell application hooks");
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "arm shell application failed", t);
        }
    }

    private static void installAfter(XposedModule module, Class<?> cls, String name,
                                     Class<?>[] params, String source) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            module.hook(m)
                    .setId("QuietHupu#deferred-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            tryInstall(module, chain.getThisObject().getClass().getClassLoader(),
                                    source);
                        } catch (Throwable t) {
                            module.log(Log.WARN, TAG, "deferred install after "
                                    + source + " failed", t);
                        }
                        return result;
                    });
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "hook shell " + name + " failed", t);
        }
    }

    private static void startPolling(XposedModule module, ClassLoader shellCl) {
        Thread thread = new Thread(() -> {
            for (int i = 0; i < 300 && !sInstalled.get(); i++) {
                tryInstall(module, shellCl, "poll-shell");
                if (!sInstalled.get()) {
                    tryInstall(module, activityLoader(), "poll-activity");
                }
                if (!sInstalled.get()) {
                    tryInstall(module, Thread.currentThread().getContextClassLoader(),
                            "poll-tccl");
                }
                if (sInstalled.get()) {
                    return;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!sInstalled.get()) {
                module.log(Log.WARN, TAG, "deferred install timeout: payload classes never visible");
            }
        }, "QuietHupu-deferred");
        thread.setDaemon(true);
        thread.start();
    }

    private static void tryInstall(XposedModule module, ClassLoader cl, String source) {
        if (cl == null || sInstalled.get()) {
            return;
        }
        Class<?> probe = probe(cl);
        if (probe == null) {
            return;
        }
        ClassLoader installCl = probe.getClassLoader() != null ? probe.getClassLoader() : cl;
        if (!sInstalled.compareAndSet(false, true)) {
            return;
        }
        module.log(Log.INFO, TAG, "payload visible via " + probe.getName()
                + " (" + source + "), installing hooks with " + installCl);
        try {
            Hooks.installAll(module, installCl, true);
            module.log(Log.INFO, TAG, "hook installation finished");
        } catch (Throwable t) {
            // 回滚，下一轮轮询/回调可重试。
            sInstalled.set(false);
            module.log(Log.ERROR, TAG, "hook installation failed, will retry", t);
        }
    }

    private static Class<?> probe(ClassLoader cl) {
        for (String name : PROBES) {
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
                // 继续试下一个探针
            }
        }
        return null;
    }

    /** 从 ActivityThread.mActivities 拿当前 Activity 的 ClassLoader（payload 加载器）。 */
    private static ClassLoader activityLoader() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field field = at.getDeclaredField("mActivities");
            field.setAccessible(true);
            Object raw = field.get(thread);
            if (!(raw instanceof Map)) {
                return null;
            }
            for (Object record : ((Map<?, ?>) raw).values()) {
                if (record == null) {
                    continue;
                }
                Field af = record.getClass().getDeclaredField("activity");
                af.setAccessible(true);
                Object activity = af.get(record);
                if (activity instanceof Activity) {
                    ClassLoader cl = ((Activity) activity).getClassLoader();
                    if (cl != null) {
                        return cl;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 页面还没创建时正常失败，下一轮再试。
        }
        return null;
    }
}
