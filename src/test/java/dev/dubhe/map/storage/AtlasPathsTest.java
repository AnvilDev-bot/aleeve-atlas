package dev.dubhe.map.storage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/// 地图与路径点缓存目录布局测试。
class AtlasPathsTest {
    @TempDir
    Path gameDir;

    @Test
    void cacheRootIsInsideGameDataDirectory() {
        Assertions.assertEquals(this.gameDir.resolve("data").resolve("aleeve_atlas"),
                AtlasPaths.root(this.gameDir));
    }

    @Test
    void serverCacheStaysBelowMapDataDirectory() {
        Path root = AtlasPaths.serverRoot(this.gameDir, "server");
        Assertions.assertEquals(this.gameDir.resolve("data").resolve("aleeve_atlas")
                .resolve("mapdata").resolve("server"), root);
    }
}
