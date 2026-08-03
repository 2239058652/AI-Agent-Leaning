package com.assistant.ai.tool;

import com.assistant.ai.mcp.McpClientService;
import com.assistant.ai.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 工具执行服务 — 执行 AI 调用的工具
 * <p>
 * 模型说"我要调用 get_weather，参数是北京"
 * → 这个类负责真正执行，返回结果给模型
 * <p>
 * 执行路由：
 * - LOCAL 工具：本地 Java 方法执行
 * - 基础工具：get_current_date、calculate、get_ip
 * - 业务工具：query_orders、analyze_orders、cancel_order
 * - MCP 工具：通过 MCP 协议调用远程服务（get_weather → mcp-server）
 * <p>
 * 安全机制：
 * 1. 白名单 — 只允许 Registry 中注册的工具执行
 * 2. 参数校验 — 执行前校验必填参数和类型
 * 3. 敏感操作 — sensitive=true 的工具会被拒绝，需要走确认流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final ToolValidator toolValidator;
    private final McpClientService mcpClientService;
    private final OrderService orderService;

    /**
     * 执行工具 — 校验 + 分发
     *
     * @param toolName 工具名称
     * @param argsJson 参数 JSON 字符串
     * @return 工具执行结果（可能是正常结果，也可能是需要确认）
     */
    public ToolResult execute(String toolName, String argsJson) {
        log.info("========== 工具执行开始 ==========");
        log.info("工具名: {}", toolName);
        log.info("参数: {}", argsJson);

        // ---- 安全校验 ----

        // 1. 白名单检查
        if (!toolRegistry.isAllowed(toolName)) {
            log.warn("工具不在白名单中: {}", toolName);
            return ToolResult.success("错误: 未知工具 " + toolName);
        }

        ToolDefinition toolDef = toolRegistry.getTool(toolName).orElseThrow();

        // 2. 参数校验
        ToolValidator.ValidationResult validation = toolValidator.validate(toolDef, argsJson);
        if (!validation.success()) {
            log.warn("参数校验失败: {}", validation.errors());
            return ToolResult.success("错误: 参数校验失败 — " + String.join("; ", validation.errors()));
        }

        // 3. 敏感操作检查 — 返回"需要确认"，不是错误
        if (toolDef.isSensitive()) {
            log.info("敏感操作需要确认: {}，等待用户确认", toolName);
            return ToolResult.confirmRequired(toolName, argsJson);
        }

        // ---- 执行工具 ----
        String result;

        // 根据工具来源路由：MCP 工具走远程调用，本地工具走 switch
        if (toolDef.getSource() == ToolDefinition.ToolSource.MCP) {
            result = executeMcpTool(toolName, argsJson);
        } else {
            result = switch (toolName) {
                case "get_current_date" -> executeGetCurrentDate();
                case "calculate" -> executeCalculate(argsJson);
                case "get_ip" -> executeGetIp();
                case "query_orders" -> executeQueryOrders(argsJson);
                case "analyze_orders" -> executeAnalyzeOrders();
                case "cancel_order" -> executeCancelOrder(argsJson);
                default -> "错误: 未知本地工具 " + toolName;
            };
        }

        log.info("工具执行结果: {}", result);
        log.info("========== 工具执行结束 ==========");
        return ToolResult.success(result);
    }

    /**
     * 执行已确认的敏感操作 — 跳过敏感检查
     * <p>
     * 用户在前端确认后，ChatService 调用这个方法执行。
     * 和 execute() 的区别：不检查 sensitive 标记。
     */
    public ToolResult executeConfirmed(String toolName, String argsJson) {
        log.info("========== 执行已确认的敏感操作 ==========");
        log.info("工具名: {}，参数: {}", toolName, argsJson);

        String result = switch (toolName) {
            case "cancel_order" -> executeCancelOrder(argsJson);
            default -> "错误: 不支持的敏感操作 " + toolName;
        };

        log.info("已确认操作结果: {}", result);
        return ToolResult.success(result);
    }

    /**
     * 执行 MCP 工具 — 通过 MCP 协议调用远程服务
     * <p>
     * 将 argsJson（字符串）转为 Map，传给 McpClientService.callTool()。
     * MCP SDK 的 callTool 接受 Map<String, Object> 参数。
     */
    private String executeMcpTool(String toolName, String argsJson) {
        try {
            // 将 JSON 字符串转为 Map
            Map<String, Object> args = objectMapper.readValue(argsJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            return mcpClientService.callTool(toolName, args);
        } catch (Exception e) {
            log.error("MCP 工具参数解析失败: {}", toolName, e);
            return "错误: MCP 工具参数解析失败 — " + e.getMessage();
        }
    }

    /**
     * 查询IP的方法
     */
    private String executeGetIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "IP获取失败: " + e.getMessage();
        }
    }

    /**
     * 获取当前日期
     */
    private String executeGetCurrentDate() {
        LocalDate today = LocalDate.now();
        String[] weekdays = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return "今天是 " + today.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                + " " + weekdays[today.getDayOfWeek().getValue()];
    }

    // ========================================================================
    // 业务工具：订单管理
    // ========================================================================

    /**
     * 查询订单列表
     */
    private String executeQueryOrders(String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            String status = args.path("status").asText(null);
            var orders = orderService.queryOrders(status);

            if (orders.isEmpty()) {
                return status == null ? "暂无订单" : "没有状态为 " + status + " 的订单";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(orders.size()).append(" 个订单：\n");
            for (var order : orders) {
                sb.append("• ").append(order.getOrderNo())
                        .append(" | ").append(order.getProductName())
                        .append(" | ¥").append(order.getAmount())
                        .append(" | ").append(order.getStatus())
                        .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询订单失败: " + e.getMessage();
        }
    }

    /**
     * 今日订单统计
     */
    private String executeAnalyzeOrders() {
        try {
            var stats = orderService.todayStats();
            return String.format("今日订单统计：总订单 %d 单，已支付 %d 单，成交额 ¥%s",
                    stats.getOrderCount(), stats.getPaidCount(), stats.getPaidAmount());
        } catch (Exception e) {
            return "统计失败: " + e.getMessage();
        }
    }

    /**
     * 取消订单 — 敏感写操作
     * <p>
     * 注意：这个方法在正常流程中不会被调用，
     * 因为 execute() 方法在检测到 sensitive=true 时会直接拒绝。
     * 敏感操作需要走确认流程（前端弹窗 → 用户确认 → 后端执行）。
     */
    private String executeCancelOrder(String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            String orderNo = args.path("order_no").asText("");
            return orderService.cancelOrder(orderNo);
        } catch (Exception e) {
            return "取消订单失败: " + e.getMessage();
        }
    }

    /**
     * 简单计算器
     */
    private String executeCalculate(String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            String expression = args.path("expression").asText("");

            // 简单实现：只支持加减乘除
            // 实际项目中应该用表达式解析库，不要用 eval
            String result = evaluateSimpleExpression(expression);
            return expression + " = " + result;
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    /**
     * 极简表达式求值（仅支持 + - * / 和数字）
     * 注意：生产环境不要用这个，应该用专业的表达式解析库
     */
    private String evaluateSimpleExpression(String expr) {
        try {
            // 去掉空格
            expr = expr.replaceAll("\\s+", "");

            // 简单的四则运算，只处理两个数
            if (expr.contains("+")) {
                String[] parts = expr.split("\\+");
                return String.valueOf(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]));
            } else if (expr.contains("-")) {
                String[] parts = expr.split("-");
                return String.valueOf(Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]));
            } else if (expr.contains("*")) {
                String[] parts = expr.split("\\*");
                return String.valueOf(Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]));
            } else if (expr.contains("/")) {
                String[] parts = expr.split("/");
                double divisor = Double.parseDouble(parts[1]);
                if (divisor == 0) return "错误: 除数不能为零";
                return String.valueOf(Double.parseDouble(parts[0]) / divisor);
            } else {
                return expr; // 纯数字
            }
        } catch (Exception e) {
            return "表达式格式错误: " + expr;
        }
    }
}
