package org.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动类 —— 整个项目的入口
 *
 * 注意：这个类必须放在所有代码的「最外层包」(org.example)，
 *       因为 @SpringBootApplication 只会扫描它所在包及其子包。
 *       如果挪到 org.example.app 下，controller 包就扫不到了，接口会全部 404。
 */
@SpringBootApplication
@MapperScan("org.example.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("""

                ============================================
                  后端启动成功
                  接口地址: http://localhost:8080/api/todos
                  统计接口: http://localhost:8080/api/todos/stats
                ============================================
                """);
    }
}
