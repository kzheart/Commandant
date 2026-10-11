# 行为基线

原始版本：`d19e37c`（0.2）。重写保留插件名、数据目录与 YAML 键。

- `/commandant` 帮助、`/commandant reload`，权限 `commandant.admin`。
- 监听玩家命令预处理事件，含前导 `/`；已取消事件仍参与处理。
- 已启用拦截器按 priority 升序执行，同优先级保持配置顺序；取消不会阻止后续拦截器。
- 条件：regex（整串匹配）、all、any、has_permission、no_permission、world、script。
- 权限和世界条件失败时发送可选 message；world 支持字符串或列表。
- 动作：cancel、log、replace、script。动作依配置顺序执行。
- log 替换 `{player}` / `{command}`，写控制台与带本地时间戳的 command.log。
- replace 替换 `{player}` / `{world}`，通过玩家执行命令；它本身不取消原命令。
- 脚本保留 `cancel_cmd_event`、玩家 sender、条件 `@command` 和动作 `@PlayerCommandPreprocessEvent`。
- debug 控制详细日志；默认 config.yml 与 interceptors.yml 不改写。

## 可明确识别的兼容边界

Klib 自带 Kether 实现，不依赖 TabooLib 运行时。任意第三方 TabooLib 扩展不自动导入。
同步事件不能等待回到主线程的异步条件；异步条件必须拒绝并报告，避免旧实现 future.get 导致死锁。
所有声称支持的行为由回归测试和服务器测试记录区分验证，不将编译通过当作全版本实测。
