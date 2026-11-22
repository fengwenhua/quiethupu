package com.quiethupu.app;

/**
 * 目标应用版本门禁。
 *
 * <p>只声明“适配版本”这一件事：未知版本不阻断 hook（fail-open），
 * 由异常横幅提示；hook 内部仍按 Feature 开关执行。
 */
public final class VersionGate {

    /** 虎扑 8.2.69.11122（versionCode 12326）：当前适配版本。 */
    public static final String SUPPORTED_VERSION = "8.2.69.11122";

    /** 同 versionCode 的 8.2.68.11062（部分渠道/测试包使用该 versionName）。 */
    public static final String SUPPORTED_VERSION_ALT = "8.2.68.11062";

    /** 对应的 versionCode。 */
    public static final long SUPPORTED_VERSION_CODE = 12326L;

    private VersionGate() {
    }

    public static boolean isSupported(String versionName) {
        return SUPPORTED_VERSION.equals(versionName)
                || SUPPORTED_VERSION_ALT.equals(versionName);
    }
}
