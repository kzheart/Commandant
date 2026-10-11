# Commandant

基于 Klib 的玩家命令拦截插件。纯 Java 实现，Gradle Kotlin DSL 构建；保持原有插件名、配置格式、条件和动作。

## 安装与构建

构建需要 JDK 21：

```bash
./gradlew clean build
```

将 `build/libs/Commandant-0.3.0-all.jar` 放入服务器 `plugins` 目录并重启。不要使用普通 JAR 或 sources JAR。
Klib 和脚本依赖已内嵌、隔离，无需单独安装 Klib 或 TabooLib，也不会在启动时联网下载运行库。

首次运行生成 `plugins/Commandant/config.yml` 和 `interceptors.yml`。升级时保留已有文件；建议先备份。

## 命令与权限

- `/commandant`：显示帮助
- `/commandant reload`：重载配置，权限 `commandant.admin`
- `config.yml` 的 `debug`：启用详细日志

重载不重复注册命令与监听器。无效 YAML 保留上一份运行配置；单个无效拦截器会报告并跳过。

## 条件和动作

拦截器支持 `enabled`、`priority`、`condition`、`actions`。priority 数值越小越先执行，同优先级按文件顺序。
取消命令后仍检查后续拦截器。玩家命令包含开头的 `/`；regex 匹配整个命令，不是子串搜索。

条件：

- `regex`：`pattern`
- `all` / `any`：嵌套 `rules`
- `has_permission` / `no_permission`：`permission`，可选失败提示 `message`
- `world`：`worlds` 字符串或列表，可选失败提示 `message`
- `script`：`script`，只有实际布尔值 true 才匹配

动作：

- `cancel`：取消原命令
- `log`：`format` 替换 `{player}` 和 `{command}`，输出控制台并追加 UTF-8 `command.log`
- `replace`：`command` 替换 `{player}` 和 `{world}`，以玩家执行。与旧版本一致，它本身不取消原命令；需要时另加 cancel
- `script`：执行 Kether `script`

默认文件提供完整例子。条件和动作仍支持通过 Java 注册工厂扩展；旧 Kotlin DSL 的二进制 API 不是兼容目标。

## 脚本

保留 `tell`、`cancel_cmd_event` 和 Klib 提供的 Kether 动作，参考 [Klib Script 文档](https://github.com/kzheart/klib/wiki/Script)。
条件可读 `@command`，动作可读 `@PlayerCommandPreprocessEvent`，sender 为执行命令的玩家。

```yaml
interceptors:
  example:
    enabled: true
    priority: 10
    condition:
      type: regex
      pattern: '^/example$'
    actions:
      - type: script
        script: |-
          tell 命令已拦截
          cancel_cmd_event
```

- 条件必须同步完成；异步条件会明确报错并取消，不阻塞服务器主线程
- `cancel_cmd_event` 必须在 `wait` 等异步动作之前执行；事件返回后无法再撤回原命令
- Java 8 使用 JDK Nashorn；Java 11+ 使用插件内嵌的隔离 Nashorn，不要求额外安装 JavaScript 引擎
- PlaceholderAPI 是可选依赖；使用 `papi` 时才需要安装
- Klib 提供可选同服 TabooLib OpenContainer 互操作；这不是 TabooLib 运行时。第三方扩展仍需对应插件，并受其协议支持范围限制
- 配置中的脚本由可信服务器管理员维护，具有所暴露的 Bukkit/Java 权限，不是用于执行不可信玩家代码的沙箱

## 兼容与验证

公共代码使用 Java 8 字节码，编译基线为 Spigot 1.12.2 API；目标 Bukkit/Spigot/Paper 1.12–26.2。
现代服务器必须使用其要求的 Java，不能因为插件是 Java 8 字节码就使用 Java 8 启动 Paper 26.2。
具体实测版本、Java 版本和未验证范围见 [兼容测试记录](docs/COMPATIBILITY.md)。
[行为基线](docs/FEATURE_PARITY.md) 区分保留行为和必要的安全改进。

## 许可证

参见 [LICENSE](LICENSE) 和 [第三方组件说明](THIRD_PARTY_NOTICES.md)。
