package org.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.entity.Todo;
import org.example.mapper.TodoMapper;
import org.example.service.TodoService;
import org.springframework.stereotype.Service;

/**
 * 待办事项业务实现
 *
 * ServiceImpl<TodoMapper, Todo> 的两个泛型：
 *   第一个 = 用哪个 Mapper 操作数据库
 *   第二个 = 操作哪个实体
 *
 * 目前是空的 —— 因为全是单表 CRUD，父类已经全包了。
 * 以后要加业务规则（比如"标题不能重复"），就写在这里。
 */
@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, Todo> implements TodoService {
}
