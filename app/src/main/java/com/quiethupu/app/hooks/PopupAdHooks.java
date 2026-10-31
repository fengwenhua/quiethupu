package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-07 弹窗广告。
 *
 * <p>HpAdRvPopup（RecyclerView 弹窗）与 HpAdDialog（通用广告弹窗）都通过
 * loadData() 拉取弹窗素材并展示。跳过 loadData 并返回 this 即不再弹广告。
 * HpAdDialog 为可选实现，缺失时不影响 HpAdRvPopup。
 */
final class PopupAdHooks {

    private static final String RV_POPUP = "com.hupu.adver_popup.HpAdRvPopup";
    private static final String DIALOG_AD = "com.hupu.adver_dialog.HpAdDialog";

    private PopupAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.POPUP_AD_BLOCK;
        try {
            Class<?> popup = HookKit.findClass(module, f, cl, RV_POPUP);
            if (popup != null) {
                Method loadData = HookKit.findMethodQuiet(popup, "loadData");
                HookKit.install(module, f, "HpAdRvPopup#loadData", loadData,
                        HookKit.returnThis(module, f, "popup ad request skipped"));
            }
            Class<?> dialog = HookKit.findClassOptional(module, f, cl, DIALOG_AD);
            if (dialog != null) {
                Method loadData = HookKit.findMethodQuiet(dialog, "loadData");
                HookKit.install(module, f, "HpAdDialog#loadData", loadData,
                        HookKit.returnThis(module, f, "dialog ad request skipped"));
            }
            HookState.note(f, "弹窗广告：HpAdRvPopup + HpAdDialog");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
