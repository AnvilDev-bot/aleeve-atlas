package dev.aleeve.atlas.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.resources.Identifier;

/// 数据存储路径布局（规格 §11）。
///
/// `<游戏目录>/aleeve_atlas/mapdata/<服务器标识或存档名>/<维度ID>/`
/// 服务器标识对 IP/端口做不可逆但稳定的规范化，避免泄露服务器地址原文。
public final class AtlasPaths {
    private static final String ROOT = "aleeve_atlas";
    private static final String MAPDATA = "mapdata";

    private AtlasPaths() {
    }

    /// 地图缓存与路径点的根目录。
    public static Path root(Path gameDir) {
        return gameDir.resolve(ROOT);
    }

    /// 单个服务器（或存档）的地图数据根目录。
    public static Path serverRoot(Path gameDir, String serverKey) {
        return root(gameDir).resolve(MAPDATA).resolve(sanitize(serverKey));
    }

    /// 某维度在小地图缓存中的目录；维度以 `Identifier` 标识（规格 §2.1）。
    public static Path dimensionDir(Path gameDir, String serverKey, Identifier dimension) {
        return serverRoot(gameDir, serverKey).resolve(sanitize(dimension.toString()));
    }

    /// 把服务器地址/存档名规范化为稳定的目录名：SHA-256 前 16 字节十六进制。
    public static String hashServerKey(String serverAddress) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serverAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
