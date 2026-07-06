package com.assistant.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 工具执行服务 — 本地执行 AI 调用的工具
 *
 * 模型说"我要调用 get_weather，参数是北京"
 * → 这个类负责真正执行，返回结果给模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ObjectMapper objectMapper;

    /**
     * 执行工具 — 根据工具名分发到对应方法
     *
     * @param toolName 工具名称
     * @param argsJson 参数 JSON 字符串
     * @return 工具执行结果
     */
    public String execute(String toolName, String argsJson) {
        log.info("执行工具: {}({})", toolName, argsJson);

        return switch (toolName) {
            case "get_weather" -> executeGetWeather(argsJson);
            case "get_current_date" -> executeGetCurrentDate();
            case "calculate" -> executeCalculate(argsJson);
            default -> "错误: 未知工具 " + toolName;
        };
    }

    /**
     * 查询天气（假数据，实际项目中调真实天气 API）
     */
    private String executeGetWeather(String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            String location = args.path("location").asText("未知城市");

            // 假数据 — 实际项目中这里调和风天气、OpenWeather 等 API
            return switch (location) {
                case "北京" -> "天气: 晴, 温度: 28°C, 湿度: 40%, 风力: 3级";
                case "上海" -> "天气: 多云, 温度: 30°C, 湿度: 65%, 风力: 2级";
                case "广州" -> "天气: 阵雨, 温度: 32°C, 湿度: 80%, 风力: 4级";
                default -> "天气: 晴, 温度: 25°C（" + location + "的模拟数据）";
            };
        } catch (Exception e) {
            return "解析参数失败: " + e.getMessage();
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
