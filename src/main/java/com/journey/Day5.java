package com.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Day5 {

    private static final Logger log = LoggerFactory.getLogger(Day5.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public record Weather(String city, double tempC, double windKph, int weatherCode) {}

    public static void main(String[] args) {
        try {
            Weather w = fetchWeather(31.2304, 121.4737);
            log.info("天气: city={}, 温度={}°C, 风速={} km/h, 天气码={}",
                    w.city(), w.tempC(), w.windKph(), w.weatherCode());
        } catch (Exception e) {
            log.error("查询失败:{}", e.getMessage(), e);
        }
    }

    static Weather fetchWeather(double lat, double lon) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,wind_speed_10m,weather_code";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        log.debug("API 原始响应: {}", response.body());

        JsonNode root = mapper.readTree(response.body());
        JsonNode cur = root.get("current");
        return new Weather("上海",
                cur.get("temperature_2m").asDouble(),
                cur.get("wind_speed_10m").asDouble(),
                cur.get("weather_code").asInt());
    }
}