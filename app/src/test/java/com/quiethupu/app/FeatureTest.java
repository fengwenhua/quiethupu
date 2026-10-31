package com.quiethupu.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Feature 编号、key 与编译期默认值映射。
 *
 * <p>id 是统一功能编号，可随重编号调整；key 是 remote prefs 读写的键，
 * 改了会连带断掉已保存的开关状态，必须锁死。默认值遵循统一策略：
 * AD/UI 默认开；PV/EN/EX 默认关，但剪贴板广告（PV-01）按需求默认开；
 * blocked 一律默认关。
 */
public class FeatureTest {

    private static final Pattern ID_PATTERN = Pattern.compile("^(AD|UI|PV|EN|EX)-\\d{2}$");

    private static final Map<String, String> EXPECTED_IDS = new LinkedHashMap<>();

    static {
        EXPECTED_IDS.put("ad_splash_cold", "AD-01");
        EXPECTED_IDS.put("ad_splash_hot", "AD-02");
        EXPECTED_IDS.put("ad_feed", "AD-03");
        EXPECTED_IDS.put("ad_refresh", "AD-04");
        EXPECTED_IDS.put("ad_banner", "AD-05");
        EXPECTED_IDS.put("ad_float", "AD-06");
        EXPECTED_IDS.put("ad_popup", "AD-07");
        EXPECTED_IDS.put("ad_search_icon", "AD-08");
        EXPECTED_IDS.put("ad_preload", "AD-09");
        EXPECTED_IDS.put("ad_shake_jump", "AD-10");
        EXPECTED_IDS.put("ad_reward_video", "AD-11");
        EXPECTED_IDS.put("ui_update_dialog", "UI-01");
        EXPECTED_IDS.put("pv_clipboard_ad", "PV-01");
    }

    @Test
    public void thirteenFeaturesPresent() {
        assertEquals("Feature 数量必须是 13", 13, Feature.values().length);
    }

    @Test
    public void keysMatchSpec() {
        Set<String> actual = new HashSet<>();
        for (Feature f : Feature.values()) {
            actual.add(f.key);
        }
        assertEquals(new HashSet<>(EXPECTED_IDS.keySet()), actual);
    }

    @Test
    public void idsMatchSpecAndEnumOrder() {
        assertEquals("枚举顺序必须与设置页顺序一致",
                new ArrayList<>(EXPECTED_IDS.keySet()), keysInEnumOrder());
        for (Feature f : Feature.values()) {
            assertEquals("编号与预期不一致: " + f.key,
                    EXPECTED_IDS.get(f.key), f.id);
        }
    }

    @Test
    public void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Feature f : Feature.values()) {
            assertTrue("id 重复: " + f.id, seen.add(f.id));
        }
        assertEquals(Feature.values().length, seen.size());
    }

    @Test
    public void keysAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Feature f : Feature.values()) {
            assertTrue("key 重复: " + f.key, seen.add(f.key));
        }
        assertEquals(EXPECTED_IDS.size(), seen.size());
    }

    @Test
    public void idsUseUnifiedFormat() {
        for (Feature f : Feature.values()) {
            assertTrue("id 格式不合法: " + f.id, ID_PATTERN.matcher(f.id).matches());
        }
    }

    @Test
    public void idPrefixMatchesGroup() {
        for (Feature f : Feature.values()) {
            assertTrue("id 前缀与分组不一致: " + f.id + " / " + f.group,
                    f.id.startsWith(f.group.name() + "-"));
        }
    }

    @Test
    public void adAndUiDefaultEnabled() {
        for (Feature f : Feature.values()) {
            if (f.group == Feature.Group.AD || f.group == Feature.Group.UI) {
                if (f.blocked()) {
                    assertFalse(f.id + " " + f.key + " blocked 应默认关闭", f.defaultEnabled);
                } else {
                    assertTrue(f.id + " " + f.key + " 应默认开启", f.defaultEnabled);
                }
            }
        }
    }

    @Test
    public void pvDefaultsOffExceptClipboard() {
        for (Feature f : Feature.values()) {
            if (f.group == Feature.Group.PV || f.group == Feature.Group.EN
                    || f.group == Feature.Group.EX) {
                if (f == Feature.CLIPBOARD_AD_BLOCK) {
                    assertTrue(f.id + " " + f.key + " 按需求应默认开启", f.defaultEnabled);
                } else {
                    assertFalse(f.id + " " + f.key + " 应默认关闭", f.defaultEnabled);
                }
            }
        }
    }

    @Test
    public void noBlockedFeaturesInThisRelease() {
        int blocked = 0;
        for (Feature f : Feature.values()) {
            if (f.blocked()) {
                blocked++;
                assertFalse(f.id + " " + f.key + " blocked 必须默认关闭", f.defaultEnabled);
            }
        }
        // 激励视频/短剧已纳入拦截，本版本所有 Feature 都有安装器。
        assertEquals("blocked 数量必须是 0", 0, blocked);
    }

    private static List<String> keysInEnumOrder() {
        List<String> keys = new ArrayList<>();
        for (Feature f : Feature.values()) {
            keys.add(f.key);
        }
        return keys;
    }
}
