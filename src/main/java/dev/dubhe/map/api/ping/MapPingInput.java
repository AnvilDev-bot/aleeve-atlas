package dev.dubhe.map.api.ping;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;

/// 外部模组提供的世界地图中键标点操作，所有方法均由客户端主线程调用。
public interface MapPingInput {
    /// 在地图选定的世界坐标发送短按标点，网络发送由实现方负责。
    void ping(Vec3 position);

    /// 打开外部轮盘；关闭后必须调用 onClose 清理地图状态，正常关闭时返回 parent。
    void openWheel(Screen parent, Vec3 position, Runnable onClose);
}
