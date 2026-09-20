package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（CORS）
 *
 * 为什么需要它？
 *   浏览器有个「同源策略」：前端跑在 http://localhost:5173，
 *   后端跑在 http://localhost:8080，端口不同 = 不同源，
 *   浏览器会直接拦掉请求，控制台报 CORS 错误。
 *
 * 两种解法（本项目两个都配了，双保险）：
 *   ① 前端 Vite 配 proxy，把 /api 转发到 8080 —— 见 frontend/vite.config.js
 *   ② 后端放行跨域请求 —— 就是这个类
 *
 * 注意：allowedOriginPatterns("*") 和 allowCredentials(true) 可以共存，
 *       但如果写成 allowedOrigins("*") 再配 allowCredentials(true)，
 *       Spring 会直接抛异常，这是个经典坑。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
