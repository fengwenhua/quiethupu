package com.quiethupu.app;

/**
 * 功能清单。key 同时是模块 App 内 remote prefs 的键，也是虎扑进程
 * getRemotePreferences("config") 的键，两处必须一致。
 *
 * <p>id 是统一功能编号（AD/UI/PV + 两位序号），只用于文档、日志前缀、
 * 诊断页与 CHANGELOG；remote key、类名不随编号变化。
 *
 * <p>blockedReason 非空表示本版本不提供实现：保留编号与条目、默认关闭、
 * 不注册 hook、不在设置页渲染。
 *
 * <p>默认值：AD/UI 默认开；PV 默认关，但剪贴板广告（PV-01）按需求默认开；
 * 当前版本没有 blocked 功能。
 */
public enum Feature {

    SPLASH_COLD("AD-01", "ad_splash_cold",
            R.string.f_ad_splash_cold_title, R.string.f_ad_splash_cold_summary,
            true, Group.AD, null),
    SPLASH_HOT("AD-02", "ad_splash_hot",
            R.string.f_ad_splash_hot_title, R.string.f_ad_splash_hot_summary,
            true, Group.AD, null),
    FEED_AD_FILTER("AD-03", "ad_feed",
            R.string.f_ad_feed_title, R.string.f_ad_feed_summary,
            true, Group.AD, null),
    REFRESH_AD_BLOCK("AD-04", "ad_refresh",
            R.string.f_ad_refresh_title, R.string.f_ad_refresh_summary,
            true, Group.AD, null),
    BANNER_AD_BLOCK("AD-05", "ad_banner",
            R.string.f_ad_banner_title, R.string.f_ad_banner_summary,
            true, Group.AD, null),
    FLOAT_AD_BLOCK("AD-06", "ad_float",
            R.string.f_ad_float_title, R.string.f_ad_float_summary,
            true, Group.AD, null),
    POPUP_AD_BLOCK("AD-07", "ad_popup",
            R.string.f_ad_popup_title, R.string.f_ad_popup_summary,
            true, Group.AD, null),
    SEARCH_ICON_AD_BLOCK("AD-08", "ad_search_icon",
            R.string.f_ad_search_icon_title, R.string.f_ad_search_icon_summary,
            true, Group.AD, null),
    AD_PRELOAD_BLOCK("AD-09", "ad_preload",
            R.string.f_ad_preload_title, R.string.f_ad_preload_summary,
            true, Group.AD, null),
    SHAKE_JUMP_BLOCK("AD-10", "ad_shake_jump",
            R.string.f_ad_shake_jump_title, R.string.f_ad_shake_jump_summary,
            true, Group.AD, null),
    REWARD_VIDEO_BLOCK("AD-11", "ad_reward_video",
            R.string.f_ad_reward_video_title, R.string.f_ad_reward_video_summary,
            true, Group.AD, null),
    INTERSTITIAL_AD_BLOCK("AD-12", "ad_interstitial",
            R.string.f_ad_interstitial_title, R.string.f_ad_interstitial_summary,
            true, Group.AD, null),

    UPDATE_DIALOG_BLOCK("UI-01", "ui_update_dialog",
            R.string.f_ui_update_dialog_title, R.string.f_ui_update_dialog_summary,
            true, Group.UI, null),

    CLIPBOARD_AD_BLOCK("PV-01", "pv_clipboard_ad",
            R.string.f_pv_clipboard_ad_title, R.string.f_pv_clipboard_ad_summary,
            true, Group.PV, null);

    public final String id;
    public final String key;
    public final int titleRes;
    public final int summaryRes;
    /** 编译期内置默认值：无 PROP_CAP_REMOTE 时使用。 */
    public final boolean defaultEnabled;
    public final Group group;
    /** 非空 = 本版本不注册任何 hook。 */
    public final String blockedReason;

    Feature(String id, String key, int titleRes, int summaryRes,
            boolean defaultEnabled, Group group, String blockedReason) {
        this.id = id;
        this.key = key;
        this.titleRes = titleRes;
        this.summaryRes = summaryRes;
        this.defaultEnabled = defaultEnabled;
        this.group = group;
        this.blockedReason = blockedReason;
    }

    public boolean blocked() {
        return blockedReason != null;
    }

    public enum Group {
        AD, UI, PV, EN, EX
    }
}
