# 袖里舆图 Aleeve Atlas

Minecraft 1.21.11（NeoForge 21.11.x）的小地图与世界地图模组，提供队伍地图同步、跨队伍标点、路径点、实体雷达与领地覆层公开 API。

- 需求规格说明书：[Minecraft 1.21.11 (NeoForge) 的小地图与世界地图模组袖里舆图（aleeve_atlas）需求规格说明书.md](<Minecraft 1.21.11 (NeoForge) 的小地图与世界地图模组袖里舆图（aleeve_atlas）需求规格说明书.md>)

## 环境要求

- JDK 21（Gradle 工具链自动解析，亦可通过 foojay 自动下载）
- 无需全局 Gradle，使用随附 wrapper

## 构建与运行

| 命令 | 用途 |
| --- | --- |
| `gradlew.bat build` | 构建模组 jar（输出在 `build/libs`） |
| `gradlew.bat runClient` | 启动开发客户端 |
| `gradlew.bat runServer` | 启动开发服务器 |
| `gradlew.bat runData` | 运行数据生成（生成 `en_us.json` 等，输出至 `src/generated/resources`） |
| `gradlew.bat test` | 运行单元测试 |

## 许可证

- 代码：LGPL-3.0-only（见 [LICENSE](LICENSE)）
- 资源（纹理、模型等）：保留所有权利（All Rights Reserved）
