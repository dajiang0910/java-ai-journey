package com.journey;

/**
 * 天气数据载体(record 自动生成 getter/equals/hashCode/toString)
 * 字段命名说明:
 *  - city/中文:直接展示
 *  - temperature:浮点(°C)
 *  - windSpeed:浮点(km/h)
 *  - weatherCode:int(WMO 编码:0=晴,3=阴,61=雨,71=雪)
 */
public record Weather(
        String city,
        double temperature,
        double windSpeed,
        int weatherCode
) {}