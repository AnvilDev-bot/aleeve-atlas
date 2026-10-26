package dev.dubhe.atlas.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 洞穴分层缓存重建判定测试。
///
/// 缓存按区块索引、不区分层高；玩家上下移动超过阈值后必须重建，
/// 否则地图会一直显示旧层级的结构。
class CaveRescanTest {
    @Test
    void firstCaveEntryDoesNotRescan() {
        // 首次进入洞穴时没有历史层高，无需重建；缓存尚未建立。
        Assertions.assertFalse(ClientMapManager.needsCaveRescan(true, null, 50));
    }

    @Test
    void smallHeightChangeDoesNotRescan() {
        // 阈值下方的小幅移动不应触发重建，否则玩家每走一步都会重采样。
        Assertions.assertFalse(ClientMapManager.needsCaveRescan(true, 100, 100));
        Assertions.assertFalse(ClientMapManager.needsCaveRescan(true, 100, 105));
        Assertions.assertFalse(ClientMapManager.needsCaveRescan(true, 100, 93));
    }

    @Test
    void largeHeightChangeTriggersRescan() {
        Assertions.assertTrue(ClientMapManager.needsCaveRescan(true, 100, 108), "上升达到阈值应重建");
        Assertions.assertTrue(ClientMapManager.needsCaveRescan(true, 100, 92), "下降达到阈值应重建");
        Assertions.assertTrue(ClientMapManager.needsCaveRescan(true, 100, 160), "大幅移动应重建");
    }

    @Test
    void surfaceModeNeverRescans() {
        // 回到地表时不重建洞穴缓存：地表使用独立缓存，洞穴数据保留待下次进入。
        Assertions.assertFalse(ClientMapManager.needsCaveRescan(false, 100, 200));
        Assertions.assertFalse(ClientMapManager.needsCaveRescan(false, null, 200));
    }
}
