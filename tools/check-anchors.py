#!/usr/bin/env python3
"""校验 quiethupu 的 Hook 锚点在目标 DEX 中是否存在。

用法：
    python3 tools/check-anchors.py <dex 文件或目录> [...]

输入应为虎扑运行时还原出的标准 DEX（r0dump + host-repair 的 40 个长名 DEX 即可），
不是加固后的 base.apk（壳 DEX 只有 30 个 com.netease.nis.wrapper 类）。

输出 OK / MISS。必需锚点缺失时退出码 1；可选锚点（同广告位的第二种实现）缺失只提示。
只依赖 Python 标准库，不读取、不执行目标 DEX 中的任何代码。
"""
import os
import struct
import sys


def uleb(b, off):
    result = shift = 0
    while True:
        x = b[off]
        off += 1
        result |= (x & 0x7f) << shift
        shift += 7
        if not x & 0x80:
            return result, off


def read_strings(b, header):
    count, off = struct.unpack('<2I', header[56:64])
    out = []
    for i in range(count):
        start = struct.unpack('<I', b[off + 4 * i:off + 4 * i + 4])[0]
        p = start
        while b[p] & 0x80:
            p += 1
        p += 1
        end = b.index(b'\0', p)
        out.append(b[p:end].decode('utf-8', 'replace'))
    return out


def parse_dex(path):
    with open(path, 'rb') as f:
        b = f.read()
    if b[:4] != b'dex\n':
        raise ValueError('not a standard dex: ' + path)
    header = b[:112]
    strings = read_strings(b, header)
    type_count, type_off = struct.unpack('<2I', header[64:72])
    proto_count, proto_off = struct.unpack('<2I', header[72:80])
    method_count, method_off = struct.unpack('<2I', header[88:96])
    types = [struct.unpack('<I', b[type_off + 4 * i:type_off + 4 * i + 4])[0]
             for i in range(type_count)]

    def type_name(idx):
        return strings[types[idx]]

    def proto(index):
        off = proto_off + 12 * index
        ret = type_name(struct.unpack('<I', b[off + 4:off + 8])[0])
        params_off = struct.unpack('<I', b[off + 8:off + 12])[0]
        params = []
        if params_off:
            n = struct.unpack('<I', b[params_off:params_off + 4])[0]
            for k in range(n):
                t = struct.unpack('<H', b[params_off + 4 + 2 * k:params_off + 6 + 2 * k])[0]
                params.append(type_name(t))
        return '(' + ''.join(params) + ')' + ret

    methods = set()
    for i in range(method_count):
        off = method_off + 8 * i
        cls = type_name(struct.unpack('<H', b[off:off + 2])[0])
        proto_idx = struct.unpack('<H', b[off + 2:off + 4])[0]
        name = strings[struct.unpack('<I', b[off + 4:off + 8])[0]]
        methods.add((cls, name, proto(proto_idx)))
    return methods


def clean(desc):
    return desc[1:-1] if desc.startswith('L') and desc.endswith(';') else desc


# (class, method, 说明, 是否必需)
ANCHORS = [
    ('com/hupu/adver_boot/manager/BootPreLoadManager', 'preLoad',
     'AD-01 后台开屏预加载', True),
    ('com/hupu/adver_boot/HpSplashAd', 'load', 'AD-01 开屏请求入口', True),
    ('com/hupu/adver_boot/HpSplashAd', 'show', 'AD-01 开屏展示兜底', True),
    ('com/hupu/adver_boot/HpSplashAd', 'showFromCache', 'AD-01 缓存开屏兜底', True),
    ('com/hupu/adver_boot/sdk/hw/HwSplashManager', 'start', 'AD-01 华为开屏', False),
    ('com/hupu/adver_boot/hot/HpAdBootLifecycle', 'onResume', 'AD-02 热启动开屏', True),
    ('com/hupu/adver_feed/data/FeedPageManager', 'getFeedConfig', 'AD-03 信息流广告位', True),
    ('com/hupu/adver_creative/refresh/HpAdRefresh', 'loadData', 'AD-04 下拉刷新广告', True),
    ('com/hupu/adver_creative/refresh/HpAdRefresh', 'loadFromNet', 'AD-04 刷新广告请求', True),
    ('com/hupu/adver_banner/lonely/HpBannerAd', 'loadFromNet', 'AD-05 单 Banner', True),
    ('com/hupu/adver_banner/mul/HpMulBannerAd', 'loadFromNet', 'AD-05 多 Banner', False),
    ('com/hupu/adver_float/HpAdFloat', 'loadData', 'AD-06 浮层广告', True),
    ('com/hupu/adver_float/HpAdRvFloat', 'loadData', 'AD-06 RecyclerView 浮层', False),
    ('com/hupu/adver_popup/HpAdRvPopup', 'loadData', 'AD-07 弹窗广告', True),
    ('com/hupu/adver_dialog/HpAdDialog', 'loadData', 'AD-07 广告 Dialog', False),
    ('com/hupu/adver_creative/search/HpRvSearchIconAd', 'loadData', 'AD-08 搜索框广告', True),
    ('com/hupu/adver_feed/manager/preload/AdFeedPreLoadManager', 'requestAfterPm',
     'AD-09 广告预加载', True),
    ('com/hupu/adver_feed/manager/preload/AdFeedPreLoadManager', 'start',
     'AD-09 预加载启动', True),
    ('com/hupu/adver_feed/manager/preload/AdFeedPreLoadManager', 'startCommonRatePreRequest',
     'AD-09 评分页预加载', True),
    ('com/hupu/adver_feed/HpFeedAd', 'initShake', 'AD-10 摇一摇', True),
    ('com/hupu/adver_game/rewardvideo/AdRewardVideoManager', 'load',
     'AD-11 游戏激励视频', True),
    ('com/hupu/adver_drama/rewardvideo/AdDramaRewardVideoManager', 'load',
     'AD-11 短剧激励视频', True),
    ('com/hupu/adver_drama/rewardvideo/common/AdCommonRewardVideoManager', 'load',
     'AD-11 通用激励视频', True),
    ('com/hupu/adver_drama/rewardvideo/HpDramaRewardVideoAd', 'loadRewardVideo',
     'AD-11 短剧激励入口', False),
    ('com/hupu/adver_drama/rewardvideo/common/HpCommonRewardVideoAd', 'loadRewardVideo',
     'AD-11 通用激励入口', False),
    ('com/hupu/adver_game/rewardvideo/AdRewardVideo', 'show', 'AD-11 游戏激励展示', True),
    ('com/hupu/adver_drama/rewardvideo/AdRewardVideo', 'show', 'AD-11 短剧激励展示', True),
    ('com/hupu/adver_drama/rewardvideo/cold_start_dialog/AdColdStartRewardFreeDialogManager',
     'startPopupFlow', 'AD-11 冷启动免广告弹窗', False),
    ('com/hupu/comp_basic_update/viewmodel/UpdateViewModel', 'showUpdateDialog',
     'UI-01 更新弹窗', True),
    ('com/hupu/adver_base/clipboard/AdClipBoardManager', 'configClipBoard',
     'PV-01 剪贴板广告', True),
]


def collect(paths):
    files = []
    for path in paths:
        if os.path.isdir(path):
            for name in sorted(os.listdir(path)):
                if name.endswith(('.dex', '.raw')):
                    files.append(os.path.join(path, name))
        else:
            files.append(path)
    return files


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    methods = {}
    for path in collect(argv[1:]):
        try:
            for cls, name, proto in parse_dex(path):
                methods.setdefault((clean(cls), name), set()).add(proto)
        except Exception as exc:  # 跳过非标准/损坏文件，不影响其余 DEX
            print('WARN skip %s: %s' % (path, exc))
    missing_required = 0
    for cls, name, desc, required in ANCHORS:
        hits = methods.get((cls, name))
        if hits:
            print('OK   %-70s %-28s %s' % (cls.replace('/', '.') + '#' + name,
                                           desc, ' | '.join(sorted(hits))))
        else:
            print('%s %-70s %s' % ('MISS' if required else 'OPT ',
                                   cls.replace('/', '.') + '#' + name, desc))
            if required:
                missing_required += 1
    print('\n%d anchors, %d required missing' % (len(ANCHORS), missing_required))
    return 1 if missing_required else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
