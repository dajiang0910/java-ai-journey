package com.journey;

/**
 * 城市参数校验 —— 简单工具类,集中校验逻辑
 * 设计动机:校验逻辑独立出来,便于:
 *  1. WeatherService 和未来其他 Service 复用
 *  2. 单测可独立测(WeatherServiceTest 也可复用)
 *  3. 校验失败抛明确异常,而不是 NPE 或空指针
 */
public final class CityParam {

    private CityParam() {}   // 工具类不允许 new

    /**
     * 校验城市名是否合法
     * @param city 用户输入
     * @return 校验通过的原值(支持链式)
     * @throws IllegalArgumentException 空/null/纯空白
     */
    public static String requireValid(String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("城市名不能为空");
        }
        return city;
    }
}