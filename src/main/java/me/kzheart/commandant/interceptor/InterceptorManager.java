package me.kzheart.commandant.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import me.kzheart.commandant.config.Values;
import me.kzheart.commandant.interceptor.action.Action;
import me.kzheart.commandant.interceptor.action.ActionRegistry;
import me.kzheart.commandant.interceptor.condition.Condition;
import me.kzheart.commandant.interceptor.condition.ConditionRegistry;
import me.kzheart.commandant.logger.CommandantLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Publishes an immutable snapshot; readers never observe a partly rebuilt rule list. */
public final class InterceptorManager {
    private final ConditionRegistry conditions;
    private final ActionRegistry actions;
    private final CommandantLogger log;
    private volatile List<Interceptor> interceptors = Collections.emptyList();

    public InterceptorManager(ConditionRegistry conditions, ActionRegistry actions, CommandantLogger log) {
        this.conditions = conditions;
        this.actions = actions;
        this.log = log;
    }

    public void reload(Map<String, Object> root) {
        List<Interceptor> next = new ArrayList<>();
        int failed = 0;
        log.info("正在加载拦截器配置...");
        Object section = root.get("interceptors");
        if (!(section instanceof Map)) log.warn("未找到拦截器配置节点");
        for (Map.Entry<String, Object> entry : Values.map(section).entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            try {
                Map<String, Object> config = Values.map(entry.getValue());
                Map<String, Object> conditionConfig = Values.map(config.get("condition"));
                Object type = conditionConfig.get("type");
                Condition condition = type == null ? null : conditions.createCondition(type.toString(), conditionConfig);
                if (condition == null) throw new IllegalArgumentException("无效的条件类型: " + type);
                List<Action> compiledActions = new ArrayList<>();
                for (Object raw : Values.list(config.get("actions"))) {
                    Map<String, Object> actionConfig = Values.map(raw);
                    Object actionType = actionConfig.get("type");
                    if (actionType == null) continue;
                    Action action = actions.createAction(actionType.toString(), actionConfig);
                    if (action != null) compiledActions.add(action);
                    else log.warn("拦截器 [" + entry.getKey() + "] 忽略未知动作: " + actionType);
                }
                next.add(new Interceptor(entry.getKey(), Values.bool(config.get("enabled"), true),
                        Values.integer(config.get("priority"), 0), condition, compiledActions));
            } catch (RuntimeException failure) {
                failed++;
                log.error("加载拦截器失败: " + entry.getKey(), failure);
            }
        }
        // List.sort is stable: equal priorities retain YAML insertion order.
        next.sort(Comparator.comparingInt(Interceptor::getPriority));
        interceptors = Collections.unmodifiableList(next);
        log.info("拦截器加载完成! 成功: " + next.size() + ", 失败: " + failed);
    }

    public int size() { return interceptors.size(); }

    public void processCommand(Player player, String command, PlayerCommandPreprocessEvent event) {
        log.debug("处理命令: " + player.getName() + " -> " + command);
        for (Interceptor interceptor : interceptors) {
            if (!interceptor.isEnabled()) continue;
            log.debug("检查拦截器: " + interceptor.getName() + " (优先级: " + interceptor.getPriority() + ")");
            interceptor.process(player, command, event, log);
        }
    }
}
