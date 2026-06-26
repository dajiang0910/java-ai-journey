package com.example.notes_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 配置
 *
 * springdoc-openapi 自动扫描 @RestController 生成文档。
 * 访问地址：
 *   - Swagger UI:  http://localhost:8080/swagger-ui.html
 *   - OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Notes API")
                        .description("笔记管理系统 REST API — Week 2 学习项目")
                        .version("1.0.0"));
    }
}
