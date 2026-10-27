package dev.dubhe.map.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import java.nio.file.Path;

/// 服务器/存档标识解析。
///
/// 地图缓存与路径点按「服务器或存档」隔离：
/// - 多人服务器安装模组时使用服务端持久下发的标识；未安装时使用服务器地址的不可逆哈希；
/// - 单机存档取存档目录名的稳定哈希，保证不同存档互不干扰且不泄露绝对路径；
/// - 两者均无法取得时回退到 `unknown`。
public final class ServerKey {
    private static volatile String remoteCacheId;

    private ServerKey() {
    }

    public static void setRemoteCacheId(String cacheId) {
        remoteCacheId = cacheId == null || cacheId.isBlank() ? null : cacheId;
    }

    public static void clearRemoteCacheId() {
        remoteCacheId = null;
    }

    /// 解析当前连接的服务器或存档标识。
    public static String resolve(Minecraft minecraft) {
        if (minecraft == null) {
            return "unknown";
        }
        String cacheId = remoteCacheId;
        ServerData server = minecraft.getCurrentServer();
        if (server != null && cacheId != null) {
            return "server_" + AtlasPaths.hashServerKey(cacheId).substring(0, 16);
        }
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return AtlasPaths.hashServerKey(server.ip);
        }
        IntegratedServer integrated = minecraft.getSingleplayerServer();
        if (integrated != null) {
            String levelId = levelId(integrated.getServerDirectory());
            if (levelId != null && !levelId.isBlank()) {
                // 取哈希前 64 位：存档名数量有限，碰撞概率可忽略，且缩短目录名长度。
                return "sp_" + AtlasPaths.hashServerKey(levelId).substring(0, 16);
            }
        }
        return "unknown";
    }

    /// 从世界目录路径中提取存档 ID。
    ///
    /// `IntegratedServer#getServerDirectory()` 在部分版本返回带尾随分隔符的路径，
    /// 因此取最后一个非空路径段。
    static String levelId(Path serverDirectory) {
        if (serverDirectory == null) {
            return null;
        }
        Path normalized = serverDirectory.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        if (fileName != null && !fileName.toString().isBlank()) {
            return fileName.toString();
        }
        return null;
    }
}
