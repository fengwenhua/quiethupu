package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * UI-01 版本更新弹窗。
 *
 * <p>虎扑更新逻辑在 comp_basic_update 模块，UpdateViewModel.showUpdateDialog(
 * AppCompatActivity, UpdateInfo, Continuation) 是弹窗的唯一展示入口。
 * 该方法是 suspend，正常返回 kotlin.Unit.INSTANCE；这里直接返回 Unit 表示
 * “挂起调用已完成”，调用方继续执行但不会弹出更新框。
 *
 * <p>release 包经过 R8 后，安装阶段（NIS attachBaseContext 之后）可能还看不到
 * {@code kotlin.Unit}，所以延迟到方法真正被调用时，从目标对象/Continuation
 * 的 ClassLoader 再找一次；实在找不到就返回 null——该调用点丢弃返回值，
 * 不会影响协程继续执行。
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
            Method show = HookKit.findByNameAndArity(cls, "showUpdateDialog", 3);
            HookKit.install(module, f, "UpdateViewModel#showUpdateDialog", show,
                    interceptShow(module, f, cl));
            HookState.note(f, "更新弹窗：UpdateViewModel#showUpdateDialog 直接返回 Unit");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static XposedInterface.Hooker interceptShow(XposedModule module, Feature f,
                                                        ClassLoader installCl) {
        return chain -> {
            try {
                Object unit = unitFrom(loaderOf(chain.getThisObject()));
                if (unit == null) {
                    unit = unitFrom(loaderOf(chain.getArg(2)));
                }
                if (unit == null) {
                    unit = unitFrom(Thread.currentThread().getContextClassLoader());
                }
                if (unit == null) {
                    unit = unitFrom(installCl);
                }
                HookKit.hit(module, f, unit == null
                        ? "update dialog skipped (unit unavailable, returning null)"
                        : "update dialog skipped");
                // 调用点只检查 COROUTINE_SUSPENDED，不校验返回值类型；null 表示
                // 协程已完成，不会弹更新框，也不会让调用方 NPE。
                return unit;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error showUpdateDialog", t);
                return chain.proceed();
            }
        };
    }

    private static ClassLoader loaderOf(Object object) {
        try {
            return object == null ? null : object.getClass().getClassLoader();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object unitFrom(ClassLoader cl) {
        if (cl == null) {
            return null;
        }
        try {
            Class<?> unit = Class.forName("kotlin.Unit", false, cl);
            return unit.getField("INSTANCE").get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
