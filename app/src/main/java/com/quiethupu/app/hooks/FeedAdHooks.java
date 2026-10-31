package com.quiethupu.app.hooks;

import com.quiethupu.app.Feature;
import com.quiethupu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-03 信息流广告。
 *
 * <p>虎扑所有信息流（首页/推荐/热榜/吧内/帖子回复/比赛页/评分页等）共用
 * {@code HpFeedAd}，它按 {@code FeedPageManager.getFeedConfig(pageId, position)}
 * 返回的广告位配置决定是否在指定位置插入广告。返回 null 时 HpFeedAd 直接走
 * “pid 为空”的失败分支，不发请求、不插卡，也不会破坏分页与普通卡片。
 *
 * <p>只动广告位配置，不碰列表数据模型，因此不会误删直播/带货等正常内容。
 */
final class FeedAdHooks {

    private static final String PAGE_MANAGER = "com.hupu.adver_feed.data.FeedPageManager";

    private FeedAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.FEED_AD_FILTER;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, PAGE_MANAGER);
            if (cls == null) {
                return;
            }
            Method getFeedConfig = HookKit.findMethod(module, f, cls, "getFeedConfig",
                    String.class, int.class);
            HookKit.install(module, f, "FeedPageManager#getFeedConfig", getFeedConfig,
                    HookKit.constValue(module, f, "feed ad slot config removed", null));
            HookState.note(f, "信息流广告：FeedPageManager#getFeedConfig 返回 null");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}
