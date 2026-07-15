package com.assistant.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * 天气工具 — 复用 ai-assistant 的天气查询逻辑
 * <p>
 * 作为 MCP Tool 暴露，模型可以调用它查询天气。
 * 逻辑和 ai-assistant 的 ToolService.executeGetWeatherByCity 一样，
 * 但这里是独立的，不依赖 ai-assistant 的代码。
 */
public class WeatherTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 执行天气查询
     *
     * @param argsJson 参数 JSON，如 {"city":"北京","days":2}
     * @return 天气结果字符串
     */
    public static String execute(String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            String city = args.path("city").asText("济南");
            int days = Math.max(1, Math.min(args.path("days").asInt(1), 16));

            // 地理编码：城市名 → 经纬度
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(city, StandardCharsets.UTF_8)
                    + "&count=1&language=zh&format=json";
            HttpResponse<String> geoResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(geoUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (geoResponse.statusCode() != 200) {
                return "地理编码服务不可用（HTTP " + geoResponse.statusCode() + "）";
            }

            JsonNode geoResult = objectMapper.readTree(geoResponse.body());
            JsonNode results = geoResult.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "未找到城市：" + city;
            }

            double lat = results.get(0).path("latitude").asDouble();
            double lon = results.get(0).path("longitude").asDouble();

            // 查询天气
            String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&timezone=Asia%2FShanghai"
                    + "&forecast_days=" + days;
            HttpResponse<String> weatherResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(weatherUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            JsonNode weatherResult = objectMapper.readTree(weatherResponse.body());
            JsonNode daily = weatherResult.path("daily");
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
            return "天气查询失败：" + e.getMessage();
        }
    }

    private static String weatherCodeToText(int code) {
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

    /**
     * 返回工具的 JSON Schema（给模型看的参数定义）
     */
    public static String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "city": {
                      "type": "string",
                      "description": "城市名称，如'北京'、'上海'"
                    },
                    "days": {
                      "type": "integer",
                      "description": "未来几天，如1、2、3，默认1"
                    }
                  },
                  "required": ["city"]
                }
                """;
    }
}
