package com.example.shortlink.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / Swagger3 API文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("短链接生成平台 API")
                        .description("短链接生成、跳转与统计分析接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ShortLink Team")));
    }
}
