package com.journey;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeatherServiceTest {

    @Test
    void fetch_EmptyString_IllegalArgumentException() {
        WeatherService service = new WeatherService();
        assertThrows(IllegalArgumentException.class, () -> service.fetch(""));
    }

    @Test
    void fetch_AllWhitespace_IllegalArgumentException() {
        WeatherService service = new WeatherService();
        assertThrows(IllegalArgumentException.class, () -> service.fetch("   "));
    }

    @Test
    void fetch_null_IllegalArgumentException() {
        WeatherService service = new WeatherService();
        assertThrows(IllegalArgumentException.class, () -> service.fetch(null));
    }
}