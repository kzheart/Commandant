# 兼容测试记录

目标范围为 Bukkit/Spigot/Paper 1.12–26.2。基础插件不使用 NMS；公共代码与内嵌 Klib 保持 Java 8 字节码。
Java 11+ JavaScript 引擎作为隔离的嵌套 JAR 提供，不由 Java 8 加载。

## 服务端要求

Paper 官方当前建议：1.12–1.16.4 使用 Java 11，1.16.5 使用 Java 16，1.17–1.19 使用 Java 17，
1.20–1.21.11 使用 Java 21，26.1+ 使用 Java 25。这是当前推荐表，不等于历史最低要求。

- https://docs.papermc.io/paper/getting-started/
- Minecraft 26.2 正式版：https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2
- Paper 构建：https://fill.papermc.io/v3/projects/paper/versions/26.2/builds

## 验证状态

构建、单元回归、真实服务端测试进行中。此文档将在最终提交中记录确切版本、构建号、Java 和结果。
没有声称所有中间版本逐一实测；Folia 不属于此次目标。
