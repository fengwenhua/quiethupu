package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * PV-01 剪贴板广告注入。
 *
 * <p>AdClipBoardManager.configClipBoard() 会向服务端请求广告口令并写入系统剪贴板，
 * 这是虎扑广告体系里唯一直接读写剪贴板的逻辑。默认关闭，开启后不再请求、
 * 也不再改写剪贴板；用户自己复制的正常内容不受影响。
 */
final class ClipboardAdHooks {

    private static final String CLIPBOARD = "com.hupu.adver_base.clipboard.AdClipBoardManager";

    private ClipboardAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.CLIPBOARD_AD_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, CLIPBOARD);
            if (cls == null) {
                return;
            }
            Method config = HookKit.findMethodQuiet(cls, "configClipBoard");
            HookKit.install(module, f, "AdClipBoardManager#configClipBoard", config,
                    HookKit.skipVoid(module, f, "clipboard ad keyword skipped"));
            HookState.note(f, "剪贴板广告：AdClipBoardManager#configClipBoard");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
