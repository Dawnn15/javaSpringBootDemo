package org.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.entity.Todo;

/**
 * 待办事项业务接口 —— 业务逻辑层
 *
 * 继承 IService<Todo> 后获得 save / removeById / updateById / getById / list / page / count 等方法。
 * 单表操作直接白嫖，真正的业务规则写在 impl 里。
 *
 * 为什么要接口 + 实现分开？
 *   1. Spring 的事务/AOP 默认基于 JDK 动态代理，需要接口
 *   2. 以后换实现（比如从 MySQL 换 MongoDB），上层代码不用改
 */
public interface TodoService extends IService<Todo> {
}
