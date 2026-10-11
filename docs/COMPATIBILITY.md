# 兼容测试记录

2026-10-11，云端隔离环境实测。目标 Bukkit/Spigot/Paper 1.12–26.2；真实服务器验证使用以下 Paper 构建。
插件不使用 NMS，公共代码与 Klib 均为 Java 8 字节码，Spigot 1.12.2 API 编译。

## 真实服务端结果

| Paper | 构建号 | Java | 启动 / 功能断言 / 停服 |
| --- | ---: | --- | --- |
| 1.12 | 1169 | Temurin 8u504 | 全部通过 |
| 1.12.2 | 1620 | Temurin 8u504 | 全部通过 |
| 1.16.5 | 794 | Temurin 16.0.2 | 全部通过 |
| 1.19.4 | 550 | Temurin 17.0.20.1 | 全部通过 |
| 1.20.6 | 151 | OpenJDK 21.0.12.1 | 全部通过 |
| 1.21.11 | 132 | OpenJDK 21.0.12.1 | 全部通过 |
| 26.1.2 | 74 | Temurin 25.0.4.1 | 全部通过 |
| 26.2 | 133 | Temurin 25.0.4.1 | 全部通过 |

同一插件 JAR 的 SHA-256：
`e81e88ce43858be11b5f8d4d2160f7f0736fab3bc85c15d9fd3f4ae1e456bef2`

每个服务器均真实启动、达到 ready、通过 `COMMANDANT_SMOKE_PASS`、正常关闭且退出码为 0。
结果和各项断言见 [server-results.json](validation/server-results.json)，官方制品 URL 与校验和见 [downloads.json](validation/downloads.json)。

覆盖范围：

- 插件启用，真实 Bukkit 命令注册、控制台 `/commandant reload`
- 真实事件总线分发 PlayerCommandPreprocessEvent，使用可控 Player 代理
- 默认 Kether tell/cancel、has_permission/no_permission 两个分支
- cancel/replace、玩家与世界占位符、升序优先级、已禁用规则
- 原配置字节保留、在线重载与恢复默认规则
- JavaScript 布尔条件、Java.type / HashMap 互操作，Java 8 和 25 均通过
- 插件关闭、监听器解除注册
- 26.2 另加无 Kether 的模拟 OpenAPI 插件：观察到 6 次协议探测，Commandant 正常通过全套断言

测试服务器只绑定 127.0.0.1，online-mode 开启，没有连接外部玩家。该验证不是完整的 Minecraft 客户端会话测试。
没有逐一实测所有中间版本/补丁、Spigot/Bukkit 发行版、Folia 或每个第三方 Kether 扩展。

## 构建与单元测试

- `clean build`：Java 21 构建、资源打包、源码 JAR、测试、部署 JAR 完整性校验
- 44 项回归测试，在 Java 8、21、25 上执行；覆盖配置、条件、动作、脚本、反射协议与生命周期
- JAR 校验：外层所有 class 不高于 Java 8；没有未隔离 Klib、TabooLib 运行库、Kotlin 运行库
- Nashorn 15.6 / ASM 保留为嵌套 JAR，仅在 Java 11+ 由独立类加载器加载；Java 8 使用内置 Nashorn
- GitHub Actions 从检出代码构建，不需要相邻 Klib 源码目录、mavenLocal 或私有仓库

## 已处理的环境问题

Paper 1.12 原始 2017 S3 下载地址已失效。测试准备器通过官方 Mojang version manifest 取得现行地址，
再按 Paper 内嵌 SHA-256 校验原始服务器，重试后通过。没有换用第三方服务器文件。
部分云端 Mojang认证/更新检查网络请求失败不影响本地 API 断言；没有关闭服务端认证来规避。

## 重现

参见 [scripts/compatibility](../scripts/compatibility/README.md)。矩阵中的 Paper 构建号固定；官方下载均校验 SHA-256。
运行服务器前须自行阅读并接受 [Minecraft EULA](https://www.minecraft.net/eula)，脚本要求显式 `--accept-eula`。

Paper 当前 Java 建议表见 [官方说明](https://docs.papermc.io/paper/getting-started/)；这与历史最低要求不同。
[Minecraft 26.2 正式版](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2) 对应新版 Java 平台；Java 8 插件字节码不代表能用 Java 8 启动新服务器。
