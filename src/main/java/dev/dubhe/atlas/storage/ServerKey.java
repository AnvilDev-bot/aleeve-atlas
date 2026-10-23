package dev.dubhe.atlas.storage;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

/// 服务器/存档标识解析（规格 §11）。
///
/// 地图缓存与路径点按「服务器或存档」隔离：
/// - 多人服务器取 IP/端口的不可逆稳定哈希，避免泄露地址原文；
/// - 单机存档取存档目录名的稳定哈希，保证不同存档互不干扰且不泄露绝对路径；
/// - 两者均无法取得时回退到 `unknown`。
public final class ServerKey {
    private ServerKey() {
    }

    /// 解析当前连接的服务器或存档标识。
    public static String resolve(Minecraft minecraft) {
        if (minecraft == null) {
            return "unknown";
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return AtlasPaths.hashServerKey(server.ip);
        }
        IntegratedServer integrated = minecraft.getSingleplayerServer();
        if (integrated != null) {
            String levelId = levelId(integrated.getServerDirectory());
            if (levelId != null && !levelId.isBlank()) {
                // 用哈希避免存档名中的特殊字符影响路径，同时与多人服务器格式一致。
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
