package com.assistant.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 工具执行服务 — 本地执行 AI 调用的工具
 * <p>
 * 模型说"我要调用 get_weather，参数是北京"
 * → 这个类负责真正执行，返回结果给模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 执行工具 — 根据工具名分发到对应方法
     *
     * @param toolName 工具名称
     * @param argsJson 参数 JSON 字符串
     * @return 工具执行结果
     */
    public String execute(String toolName, String argsJson) {
        log.info("========== 工具执行开始 ==========");
        log.info("工具名: {}", toolName);
        log.info("参数: {}", argsJson);

        String result = switch (toolName) {
            case "get_current_date" -> executeGetCurrentDate();
            case "calculate" -> executeCalculate(argsJson);
            case "get_ip" -> executeGetIp();
            case "get_weather_by_city" -> executeGetWeatherByCity(argsJson);
            default -> "错误: 未知工具 " + toolName;
        };

        log.info("工具执行结果: {}", result);
        log.info("========== 工具执行结束 ==========");
        return result;
    }

    /**
     * 查询未来几天的天气-根据city
     */
    private String executeGetWeatherByCity(String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            String city = args.path("city").asText("济南");
            Integer days = Math.max(1, Math.min(args.path("days").asInt(1), 16)); // Open-Meteo 免费版最多支持16天

            log.info("========== >>> 我的新工具被调用了！city={}, days={} ==========", city, days);

            // 第一步：地理编码，把城市名转成经纬度
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(city, StandardCharsets.UTF_8)
                    + "&count=1&language=zh&format=json";
            HttpRequest geoRequest = HttpRequest.newBuilder(URI.create(geoUrl)).GET().build();
            HttpResponse<String> geoResponse = httpClient.send(geoRequest, HttpResponse.BodyHandlers.ofString());

            if (geoResponse.statusCode() != 200) {
                return "地理编码服务暂时不可用（HTTP " + geoResponse.statusCode() + "）";
            }

            JsonNode geoResult = objectMapper.readTree(geoResponse.body());
            JsonNode results = geoResult.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "未找到城市：" + city;
            }
            double lat = results.get(0).path("latitude").asDouble();
            double lon = results.get(0).path("longitude").asDouble();

            // 第二步：查询天气
            String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&timezone=Asia%2FShanghai"
                    + "&forecast_days=" + days;
            HttpRequest weatherRequest = HttpRequest.newBuilder(URI.create(weatherUrl)).GET().build();
            HttpResponse<String> weatherResponse = httpClient.send(weatherRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode weatherResult = objectMapper.readTree(weatherResponse.body());
            JsonNode daily = weatherResult.path("daily");

            JsonNode dates = daily.path("time");
            JsonNode codes = daily.path("weather_code");
            JsonNode tMax = daily.path("temperature_2m_max");
            JsonNode tMin = daily.path("temperature_2m_min");

            StringBuilder sb = new StringBuilder(city);

            if (days <= 1) {
                sb.append("今天: ").append(weatherCodeToText(codes.get(0).asInt()))
                        .append(" ").append(tMin.get(0).asText()).append("~").append(tMax.get(0).asText()).append("°C");
            } else {
                sb.append("未来").append(days).append("天: ");
                for (int i = 0; i < codes.size(); i++) {
                    sb.append("第").append(i + 1).append("天")
                            .append(weatherCodeToText(codes.get(i).asInt()))
                            .append(tMin.get(i).asText()).append("~").append(tMax.get(i).asText()).append("°C");
                    if (i < codes.size() - 1) sb.append(", ");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("获取天气失败", e);
            return "获取未来几天的天气失败：" + e.getMessage();
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

    /**
     * WMO 天气代码转中文描述（Open-Meteo 用的是 WMO 标准代码表）
     */
    private String weatherCodeToText(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1, 2, 3 -> "多云";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 61, 63, 65 -> "雨";
            case 71, 73, 75 -> "雪";
            case 80, 81, 82 -> "阵雨";
            case 95, 96, 99 -> "雷雨";
            default -> "未知(" + code + ")";
        };
    }
}
