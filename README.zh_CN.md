<div align="center">

# 袖里舆图 Aleeve Atlas

**适用于 Minecraft 1.21.11 的小地图与世界地图模组，提供路径点、实体雷达、队伍同步和临时标点。**

[English](README.md) | 简体中文

</div>

## 项目简介

袖里舆图（`aleeve_atlas`）是基于 NeoForge 的 Minecraft 1.21.11 小地图与世界地图模组。它记录已探索地形，提供世界内与地图内标记，并支持多人协同地图和路径点分享。客户端与服务端同时安装后可使用同步功能，仅在客户端安装时仍可记录和浏览本地地图。

## 环境依赖

- Minecraft 1.21.11。
- NeoForge 21.11.x。
- 适用于 Minecraft 1.21.11 的 AnvilLib 2.x（2.0.0 或更高版本）。
  - 客户端与服务端均必须安装 `anvillib_config`。
  - 客户端还需安装 `anvillib_wheel`、`anvillib_rendering` 和 `anvillib_util`。
  - `anvillib_integration` 为可选模块，用于 FTB Teams 等模组集成。

## 安装

1. 安装 Minecraft 1.21.11 和 NeoForge 21.11.x。
2. 将所需的 AnvilLib 模块安装到 `mods` 文件夹。
3. 将袖里舆图 jar 放入同一 `mods` 文件夹，然后启动游戏。
4. 如果需要在服务器上使用地图同步、队伍功能和路径点共享，请在服务端同时安装袖里舆图与 `anvillib_config`。

## 主要功能

- 圆形或方形小地图，支持旋转、北向锁定、缩放，以及坐标、群系、光照和时间显示。
- 全屏世界地图，支持拖动、滚轮缩放、路径点管理和客户端配置入口。
- 增量记录探索区域，按服务器或存档与维度分别保存地图缓存。
- 双端安装后支持队伍地图同步和路径点分享。
- 实体雷达，支持玩家头像、实体分类配置与可见性规则。
- 自定义路径点、死亡点、世界内标记，以及导入导出命令。
- 世界内与地图内临时标点，通过轮盘选择危险、警告、一般和前往四类标点。
- 为其他模组提供领地覆层与外部标点 API。

## 默认按键

| 输入 | 功能 |
| --- | --- |
| `M` | 打开或关闭全屏世界地图。 |
| `N` | 在当前位置新建路径点。 |
| `=` / `-` | 放大或缩小小地图视野。 |
| 按住 `Z` | 居中并放大小地图，松开后恢复原位置和尺寸。 |
| 在世界内或世界地图内短按中键 | 在目标位置创建“一般”临时标点。 |
| 在世界内或世界地图内长按中键 | 打开标点轮盘，松开中键确认当前选择的标点类型。 |

模组的键盘按键可在 Minecraft 控制设置中修改。

## 配置与数据

- 客户端配置：`<game_dir>/config/aleeve_atlas-client.toml`，也可通过世界地图打开客户端设置。
- 服务端配置：`<world_dir>/serverconfig/aleeve_atlas-server.toml`。
- 地图缓存与路径点数据：`<game_dir>/data/aleeve_atlas/`。地图缓存文件位于 `mapdata` 子目录，按服务器或存档与维度隔离。

更多说明见[配置与交互指南](docs/CONFIG.md)。

## 文档

- [技术文档与开发者 API](docs/TECH.md)
- [配置与交互指南](docs/CONFIG.md)

## 许可证

- 代码：LGPL-3.0-only，见 [LICENSE](LICENSE)。
- 纹理、模型等资源：保留所有权利，见 [ASSETS_LICENSE](ASSETS_LICENSE)。
