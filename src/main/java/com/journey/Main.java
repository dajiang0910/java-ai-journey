package com.journey;

import picocli.CommandLine;
import picocli.CommandLine.Option;

@CommandLine.Command(name = "weather", description = "查询城市天气")
public class Main implements Runnable {

    @Option(names = {"-c", "--city"}, description = "城市名", required = true)
    String city;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        WeatherService service = new WeatherService();
        Weather w = service.fetch(city);   // 抛异常会冒到 picocli,自动打印
        System.out.println("城市:" + w.city());
        System.out.println("温度:" + w.temperature() + "°C");
        System.out.println("风速:" + w.windSpeed() + " km/h");
    }
}