package org.example.service.impl;

import org.example.entity.Todo;
import org.example.mapper.TodoMapper;
import org.example.service.TodoService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办事项业务实现（原生 MyBatis 版）
 *
 * <p>对比 MyBatis-Plus 版本：
 * <ul>
 *   <li>MP 版本这个类只有 1 个 {@code @Service}，方法体是空的——所有 CRUD 都靠父类 {@code ServiceImpl}</li>
 *   <li>这个版本得自己实现 7 个方法，每个都自己调 mapper，再自己处理返回值的包装</li>
 * </ul>
 *
 * <p>这是"原生 MyBatis 比 MP 多写的部分"——看完这一份，再回头看 MP 那一行空实现，
 * 你就彻底明白 MP 在替你干什么了。
 */
@Service
public class TodoServiceImpl implements TodoService {

    private final TodoMapper todoMapper;

    /**
     * 构造器注入：Spring 自动把 TodoMapper 的实例塞进来。
     * （注：原生 MyBatis 的 Mapper 接口没有实现类，框架会自动生成 JDK 动态代理对象）
     */
    public TodoServiceImpl(TodoMapper todoMapper) {
        this.todoMapper = todoMapper;
    }

    // ============ 列表查询 ============
    @Override
    public List<Todo> list(String keyword, Boolean done) {
        return todoMapper.findList(keyword, done);
    }

    // ============ 按 id 查详情 ============
    @Override
    public Todo getById(Long id) {
        return todoMapper.findById(id);
    }

    // ============ 新增 ============
    @Override
    public Todo save(Todo todo) {
        // ---- 业务规则：默认值的兜底 ----
        // 这一段对应 MP 版本的 create() 方法里的逻辑
        todo.setId(null);          // 防止前端传了 id 变成"更新"
        todo.setDone(false);       // 新建的一律未完成
        // createTime / updateTime 留 null，让数据库 DEFAULT CURRENT_TIMESTAMP 生效

        // ---- 写库 ----
        todoMapper.insert(todo);
        // 注意：insert 注解配了 @Options(useGeneratedKeys=true, keyProperty="id")
        // 所以 todo.id 在调用后已经被回填成数据库新生成的主键

        // ---- 回读数据库默认值（跟 MP 版本一样的修复） ----
        // 数据库 DEFAULT CURRENT_TIMESTAMP 填进去的值，Java 这边拿不到。
        // 必须再查一次，把 createTime / updateTime 读回来返回给前端。
        // 这是任何用数据库默认时间戳的项目都会遇到的坑。
        return todoMapper.findById(todo.getId());
    }

    // ============ 修改 ============
    @Override
    public boolean updateById(Todo todo) {
        // ---- 业务规则：不允许改 id ----
        if (todo.getId() == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        // updateTime 由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护，Java 不用管

        int rows = todoMapper.updateById(todo);
        return rows > 0;
    }

    // ============ 删除 ============
    @Override
    public boolean removeById(Long id) {
        int rows = todoMapper.deleteById(id);
        return rows > 0;
    }

    // ============ 切换完成状态 ============
    @Override
    public boolean toggleDone(Long id, boolean done) {
        int rows = todoMapper.updateDone(id, done);
        return rows > 0;
    }

    // ============ 统计 ============
    @Override
    public Map<String, Long> stats() {
        // 用 LinkedHashMap 保证 {total, done} 的输出顺序
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", todoMapper.countAll());
        result.put("done", todoMapper.countDone());
        return result;
    }

    /**
     * 这个方法现在用不到，但留着提醒一件事：
     * 原生 MyBatis 没有自动填充 createTime 的功能，
     * 如果你想"不依赖数据库默认值、Java 端自己填时间"，
     * 可以在这里加 todo.setCreateTime(LocalDateTime.now())。
     */
    @SuppressWarnings("unused")
    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}