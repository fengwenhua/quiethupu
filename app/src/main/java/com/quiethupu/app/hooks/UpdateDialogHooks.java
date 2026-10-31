package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * UI-01 版本更新弹窗。
 *
 * <p>虎扑更新逻辑在 comp_basic_update 模块，UpdateViewModel.showUpdateDialog(
 * AppCompatActivity, UpdateInfo, Continuation) 是弹窗的唯一展示入口。
 * 该方法是 suspend，直接返回 kotlin.Unit.INSTANCE 表示挂起调用已完成，
 * 调用方继续执行但不会弹出更新框。
 */
final class UpdateDialogHooks {

    private static final String UPDATE_VIEW_MODEL =
            "com.hupu.comp_basic_update.viewmodel.UpdateViewModel";

    private UpdateDialogHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.UPDATE_DIALOG_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, UPDATE_VIEW_MODEL);
            if (cls == null) {
                return;
            }
            Object unit = unitInstance(cl);
            if (unit == null) {
                HookKit.warn(module, f.id + " skip: kotlin.Unit unavailable");
                HookState.markNotFound(f, "kotlin.Unit 不可用");
                return;
            }
            Method show = HookKit.findByNameAndArity(cls, "showUpdateDialog", 3);
            HookKit.install(module, f, "UpdateViewModel#showUpdateDialog", show,
                    HookKit.constValue(module, f, "update dialog skipped", unit));
            HookState.note(f, "更新弹窗：UpdateViewModel#showUpdateDialog 直接返回 Unit");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static Object unitInstance(ClassLoader cl) {
        try {
            Class<?> unit = Class.forName("kotlin.Unit", false, cl);
            return unit.getField("INSTANCE").get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
