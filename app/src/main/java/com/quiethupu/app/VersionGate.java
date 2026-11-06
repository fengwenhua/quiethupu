package com.quiethupu.app;

/**
 * 目标应用版本门禁。
 *
 * <p>只声明“适配版本”这一件事：未知版本不阻断 hook（fail-open），
 * 由异常横幅提示；hook 内部仍按 Feature 开关执行。
 */
public final class VersionGate {

    /** 虎扑 8.2.67.10281（versionCode 12322）：真机验证的适配版本。 */
    public static final String SUPPORTED_VERSION = "8.2.67.10281";

    /** 对应的 versionCode。 */
    public static final long SUPPORTED_VERSION_CODE = 12322L;

    private VersionGate() {
    }

    public static boolean isSupported(String versionName) {
        return SUPPORTED_VERSION.equals(versionName);
    }
}
