package com.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public Weather fetch(String city) {
        CityParam.requireValid(city);   // ← 接 CityParam,业务方法本身极简

        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=31.23&longitude=121.47"
                + "&current=temperature_2m,wind_speed_10m,weather_code"
                + "&timezone=Asia%2FShanghai";

        log.info("请求 URL: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode cur = root.get("current");
            return new Weather(city,
                    cur.get("temperature_2m").asDouble(),
                    cur.get("wind_speed_10m").asDouble(),
                    cur.get("weather_code").asInt());
        } catch (Exception e) {
            log.error("查询天气失败: city={}", city, e);
            throw new RuntimeException("查询天气失败", e);
        }
    }
}