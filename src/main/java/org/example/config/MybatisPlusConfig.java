package org.example.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册分页拦截器
     *
     * 它的原理是拦截即将执行的 SQL，自动拼接 LIMIT 语句。
     * 不注册的话，分页查询会把全表数据查出来再在内存里切分，数据量大时非常危险。
     *
     * 配套依赖：pom.xml 里的 mybatis-plus-jsqlparser
     * （MyBatis-Plus 3.5.9 起 SQL 解析器被拆成了独立模块，漏引会报 ClassNotFoundException）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
