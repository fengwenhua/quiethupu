package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-11 激励视频 / 短剧广告。
 *
 * <p>虎扑的激励视频有两个体系，接口同名但包不同：
 * <ul>
 *   <li>游戏模块：{@code com.hupu.adver_game.rewardvideo.*}</li>
 *   <li>短剧/通用：{@code com.hupu.adver_drama.rewardvideo.*}</li>
 * </ul>
 *
 * <p>两条链路都在 Manager.load(listener) 里请求素材，在 AdRewardVideo.show(...)
 * 里展示。本类两层都拦：
 * <ul>
 *   <li>load 不执行，并反射回调 listener.loadFail(code, msg)，让调用方走官方失败分支；</li>
 *   <li>show 兜底不执行，并回调 listener.onAdClose()，避免领取按钮/弹窗悬挂。</li>
 * </ul>
 *
 * <p>注意：拦截后“看广告领奖/解锁短剧”会失败，这是去广告的预期代价；服务端发奖
 * 不会因为本地拦截而白给。
 */
final class RewardVideoHooks {

    private static final String[] LOADERS = {
            "com.hupu.adver_game.rewardvideo.AdRewardVideoManager",
            "com.hupu.adver_drama.rewardvideo.AdDramaRewardVideoManager",
            "com.hupu.adver_drama.rewardvideo.common.AdCommonRewardVideoManager",
    };

    private static final String[] LOAD_WRAPPERS = {
            "com.hupu.adver_drama.rewardvideo.HpDramaRewardVideoAd",
            "com.hupu.adver_drama.rewardvideo.common.HpCommonRewardVideoAd",
    };

    private static final String[] SHOWS = {
            "com.hupu.adver_game.rewardvideo.AdRewardVideo",
            "com.hupu.adver_drama.rewardvideo.AdRewardVideo",
    };

    /** 冷启动“看广告免广告”弹窗；广告已被拦，留着只会点开一个必然失败的流程。 */
    private static final String COLD_START_DIALOG =
            "com.hupu.adver_drama.rewardvideo.cold_start_dialog.AdColdStartRewardFreeDialogManager";

    private static final String AD_ERROR = "com.hupu.adver_base.constant.AdError";

    private RewardVideoHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.REWARD_VIDEO_BLOCK;
        try {
            for (String name : LOADERS) {
                installLoader(module, cl, f, name);
            }
            for (String name : LOAD_WRAPPERS) {
                installLoadWrapper(module, cl, f, name);
            }
            for (String name : SHOWS) {
                installShow(module, cl, f, name);
            }
            installColdStartDialog(module, cl, f);
            HookState.note(f, "激励视频/短剧：Managers.load + Hp*Ad.loadRewardVideo + AdRewardVideo.show");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void installLoader(XposedModule module, ClassLoader cl, Feature f,
                                      String className) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method load = HookKit.findByNameAndArity(cls, "load", 1);
        String label = shortName(className) + "#load";
        HookKit.install(module, f, label, load, failLoad(module, f, cl, label, 0));
    }

    private static void installLoadWrapper(XposedModule module, ClassLoader cl, Feature f,
                                           String className) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method load = HookKit.findByNameAndArity(cls, "loadRewardVideo", 5);
        String label = shortName(className) + "#loadRewardVideo";
        HookKit.install(module, f, label, load, failLoad(module, f, cl, label, 4));
    }

    private static void installShow(XposedModule module, ClassLoader cl, Feature f,
                                    String className) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method show = HookKit.findByNameAndArity(cls, "show", 2);
        String label = shortName(className) + "#show";
        HookKit.install(module, f, label, show, closeShow(module, f, label, 1));
    }

    private static void installColdStartDialog(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, COLD_START_DIALOG);
        if (cls == null) {
            return;
        }
        Method start = HookKit.findByNameAndArity(cls, "startPopupFlow", 1);
        HookKit.install(module, f, "AdColdStartRewardFreeDialogManager#startPopupFlow", start,
                HookKit.skipVoid(module, f, "cold start reward-free popup skipped"));
    }

    /**
     * 不请求激励视频，并回调 listener.loadFail，让调用方清理 loading/提示失败。
     * 回调失败时 proceed，避免把调用方卡在“加载中”。
     */
    private static XposedInterface.Hooker failLoad(XposedModule module, Feature f,
                                                   ClassLoader cl, String label, int argIndex) {
        return chain -> {
            try {
                Object listener = chain.getArg(argIndex);
                if (listener != null && notifyLoadFail(listener, cl)) {
                    HookKit.hit(module, f, label + " blocked");
                    return null;
                }
                HookKit.warn(module, f.id + " " + label + " listener notify failed, proceed");
                return chain.proceed();
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error " + label, t);
                return chain.proceed();
            }
        };
    }

    /** 不展示激励视频，并回调 onAdClose，避免界面停在等待广告的状态。 */
    private static XposedInterface.Hooker closeShow(XposedModule module, Feature f,
                                                    String label, int argIndex) {
        return chain -> {
            try {
                Object listener = chain.getArg(argIndex);
                if (listener != null && notifyAdClose(listener)) {
                    HookKit.hit(module, f, label + " blocked");
                    return null;
                }
                HookKit.warn(module, f.id + " " + label + " listener notify failed, proceed");
                return chain.proceed();
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error " + label, t);
                return chain.proceed();
            }
        };
    }

    private static boolean notifyLoadFail(Object listener, ClassLoader cl) {
        try {
            Method loadFail = listener.getClass().getMethod("loadFail", int.class, String.class);
            loadFail.setAccessible(true);
            loadFail.invoke(listener, rewardErrorCode(cl),
                    "quiet-hupu: reward video blocked");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean notifyAdClose(Object listener) {
        try {
            Method onAdClose = listener.getClass().getMethod("onAdClose");
            onAdClose.setAccessible(true);
            onAdClose.invoke(listener);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取官方 REWARD_VIDEO_AD_GETOTHER_FAIL 的 code；取不到时用 -1。 */
    private static int rewardErrorCode(ClassLoader cl) {
        try {
            Class<?> errorClass = Class.forName(AD_ERROR, false, cl);
            Object constant = Enum.valueOf(errorClass.asSubclass(Enum.class),
                    "REWARD_VIDEO_AD_GETOTHER_FAIL");
            Object code = errorClass.getMethod("getCode").invoke(constant);
            if (code instanceof Integer) {
                return (Integer) code;
            }
        } catch (Throwable ignored) {
            // 版本变化时允许退化为 -1；调用方只按失败处理。
        }
        return -1;
    }

    private static String shortName(String dotted) {
        int i = dotted.lastIndexOf('.');
        return i < 0 ? dotted : dotted.substring(i + 1);
    }
}
