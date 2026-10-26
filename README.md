# 袖里舆图 Aleeve Atlas

Minecraft 1.21.11（NeoForge 21.11.x）的小地图与世界地图模组，提供队伍地图同步、跨队伍标点、路径点、实体雷达与领地覆层公开 API。

- 需求规格说明书：[Minecraft 1.21.11 (NeoForge) 的小地图与世界地图模组袖里舆图（aleeve_atlas）需求规格说明书.md](<Minecraft 1.21.11 (NeoForge) 的小地图与世界地图模组袖里舆图（aleeve_atlas）需求规格说明书.md>)

## 已实现功能

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 双端自适应 | ✅ | 握手包检测服务端支持并下发锁定项，独立/协同模式自动切换 |
| 小地图 HUD | ✅ | 圆形/方形形态（着色器裁剪与箭头）、缩放、四角定位、北向锁定、信息覆层 |
| 全屏世界地图 | ✅ | 拖拽平移、滚轮缩放、未探索黑底、实体雷达、双击建点、右键标点 |
| 地图采集与缓存 | ✅ | 区块采样、方块变更触发重采样、按服务器/存档与维度隔离、区域文件 NBT 持久化 |
| 地图增量同步 | ✅ | 摘要分批上报、版本比对、差异分片流式传输、限速写入（§3.3） |
| 队伍适配层 | ✅ | 统一接口、原版计分板实现、外部实现注册与配置选择 |
| 实体雷达 | ✅ | 五类实体分类、HEX 配色、玩家双层皮肤头像、Tab 联动 |
| 路径点 | ✅ | 增删查、持久化、地图标记、死亡点、导入导出、同队收发三策略 |
| 临时标点 | ✅ | 仅提供 API 供 Ping Wheel 等外部模组接入，不内置快捷键 |
| 领地覆层 API | ✅ | 公开 API、连通分量、加权极点锚点、交界描边、zOrder 叠加、进出提示 |
| 可见性判定 | ✅ | ALWAYS/NEVER/VISIBLE_ONLY 策略，GPU 遮挡查询与异步结果读取，失败时回退射线检测 |
| 国际化 | ✅ | `en_us` 由 datagen 生成，`zh_cn` 人工维护，构建期校验键集一致 |

## 包坐标

- Java 包：`dev.dubhe.atlas`
- Maven group：`dev.dubhe.atlas`
- 模组 ID：`aleeve_atlas`

## 外部模组接入

### 临时标点（Ping Wheel 等）

```java
import dev.dubhe.atlas.api.ping.MapPingCreatedEvent;
import dev.dubhe.atlas.api.ping.MinimapPingAPI;
import dev.dubhe.atlas.ping.MapPingData;

// 外部模组在 3D 视界标点后推送给本模组，小地图/世界地图即刻渲染波纹标点
MinimapPingAPI.registerPing(position, dimensionId, MapPingData.PingIcon.NORMAL,
        senderId, senderName, teamColor);

// 监听本模组发起的标点，在 3D 世界生成光柱
NeoForge.EVENT_BUS.addListener((MapPingCreatedEvent event) -> { ... });
```

### 领地覆层（FTB Chunks / OPAC 等）

```java
import dev.dubhe.atlas.api.territory.AtlasTerritory;
import dev.dubhe.atlas.territory.SimpleTerritory;

AtlasTerritory.client().upsertTerritory(
        SimpleTerritory.of("my_territory", Level.OVERWORLD, chunks, name, 0x33AAFF));
```

## 客户端命令

| 命令 | 用途 |
| --- | --- |
| `/aleeve_atlas waypoint export <file>` | 导出全部路径点到 `aleeve_atlas_exports/<file>` |
| `/aleeve_atlas waypoint import <file>` | 从该目录导入并合并路径点 |
| `/aleeve_atlas waypoint accept\|ignore <id>` | 手动接受策略下的路径点确认 |
| `/aleeve_atlas territory demo\|clear` | 注册/清除演示领地（含孔洞与飞地，便于验收） |
| `/aleeve_atlas pingdemo` | 在玩家前方注入临时标点，便于验收波纹渲染 |
| `/aleeve_atlas perf` | 输出小地图帧耗统计与标点通道诊断 |

命令名另提供无下划线别名 `/aleeveatlas`，便于输入。

## 实现说明

### 客户端方块变更检测

规格要求方块变更时重新渲染对应区块纹理。NeoForge 1.21.11 没有客户端方块变更事件，`ClientLevel.setBlocksDirty` 是客户端收到方块更新后的唯一入口，因此以 Mixin 注入该方法的 HEAD 处标记所属区块待重新采样。

`aleeve_atlas.mixins.json` 只把该 Mixin 声明在 `client` 段，专用服务端不会加载。地表与洞穴使用两套独立缓存，失效时两份都清除，否则切换模式会命中过期像素。磁盘写入按 200 tick 的周期批量完成，不在每次失效时立即写盘。

### GPU 遮挡查询

`VISIBLE_ONLY` 使用 `GL_SAMPLES_PASSED` 对非同队玩家的代理包围盒执行硬件遮挡查询。查询在不透明地形与实体深度绘制完成后提交，代理管线关闭颜色写入、深度写入和面剔除，保留深度测试。每帧最多提交 64 个目标，结果就绪后异步读取，未就绪时沿用上一有效判定。查询能力、GL 上下文或绘制流程出现异常时，记录原因并切换到多采样点射线检测。

小地图、世界地图和临时标点共用同一可见性结果与渐隐规则。从未取得可见结果的目标不绘制，目标被遮挡或移出视锥后按配置渐隐。

验收辅助：`ALEEVE_ATLAS_OCCLUSION_SELFTEST=1` 在单人世界生成远端玩家，依次验证开阔、墙体遮挡与拆墙恢复；`ALEEVE_ATLAS_FORCE_RAYCAST=1` 强制使用射线实现。

## 依赖

本模组必需 **AnvilLib**（≥ 2.0.0）的 `config` 模块，请安装对应 Minecraft 1.21.11 版本；安装 AnvilLib 时会一并有 `codec` / `network` / `integration` / `util` 四个模块。其中 `integration` 用于 FTB Teams 等外部模组集成，`util` 用于客户端 tick 计数，其余两个模块本模组未直接引用。

## 环境要求

- JDK 21（Gradle 工具链自动解析，亦可通过 foojay 自动下载）
- 无需全局 Gradle，使用随附 wrapper

## 构建与运行

| 命令 | 用途 |
| --- | --- |
| `gradlew.bat build` | 构建模组 jar（输出在 `build/libs`），含单元测试与语言键集校验 |
| `gradlew.bat runClient` | 启动开发客户端 |
| `gradlew.bat runServer` | 启动开发服务器 |
| `gradlew.bat runData` | 运行数据生成（生成 `en_us.json`，输出至 `src/generated/resources`） |
| `gradlew.bat test` | 运行单元测试 |

## 许可证

- 代码：LGPL-3.0-only（见 [LICENSE](LICENSE)）
- 资源（纹理、模型等）：保留所有权利（见 [ASSETS_LICENSE](ASSETS_LICENSE)）
