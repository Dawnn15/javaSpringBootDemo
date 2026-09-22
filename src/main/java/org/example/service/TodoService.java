package org.example.service;

import org.example.entity.Todo;

import java.util.List;
import java.util.Map;

/**
 * 待办事项业务接口 —— 业务逻辑层（原生 MyBatis 实现）
 *
 * <p>对比 MyBatis-Plus 版本：这里不再继承 {@code IService<Todo>}，
 * 所有方法都得自己声明、自己实现。
 *
 * <p>方法设计上特意保持跟 MP 版本"同名同义"，这样切换两版本时 Controller 完全不用改：
 * <ul>
 *   <li>{@link #list} ←→ MP 的 {@code list()}</li>
 *   <li>{@link #getById} ←→ MP 的 {@code getById(id)}</li>
 *   <li>{@link #save} ←→ MP 的 {@code save(todo)}</li>
 *   <li>{@link #updateById} ←→ MP 的 {@code updateById(todo)}</li>
 *   <li>{@link #removeById} ←→ MP 的 {@code removeById(id)}</li>
 *   <li>{@link #stats} ←→ 自定义统计接口，跟原 MP 版本一致</li>
 * </ul>
 *
 * <p>为什么要接口 + 实现分开？
 * <ol>
 *   <li>Spring 的事务/AOP 默认基于 JDK 动态代理，需要接口</li>
 *   <li>以后换实现（比如从 MySQL 换 MongoDB），上层代码不用改</li>
 * </ol>
 */
public interface TodoService {

    /**
     * 列表查询
     *
     * @param keyword 标题关键词，传 null 不过滤
     * @param done    是否已完成，传 null 不过滤
     */
    List<Todo> list(String keyword, Boolean done);

    /** 按 id 查详情；不存在返回 null */
    Todo getById(Long id);

    /** 新增；返回插入后带主键的 Todo */
    Todo save(Todo todo);

    /** 修改；返回 true 表示改了至少一行 */
    boolean updateById(Todo todo);

    /** 删除；返回 true 表示删了至少一行 */
    boolean removeById(Long id);

    /** 切换完成状态；返回 true 表示改了至少一行 */
    boolean toggleDone(Long id, boolean done);

    /** 统计：返回 {total: 总数, done: 已完成数} */
    Map<String, Long> stats();
}