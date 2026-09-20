package org.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.example.common.Result;
import org.example.entity.Todo;
import org.example.service.TodoService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办事项接口 —— 控制层
 *
 * 职责很单纯：接住 HTTP 请求、把参数转成对象、调用 Service、把结果包装成 JSON。
 * 不写业务逻辑，也不碰 SQL。
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    /**
     * 构造器注入：Spring 启动时会自动把 TodoServiceImpl 传进来
     * 比字段上写 @Autowired 更好 —— 字段可以是 final，且便于写单元测试
     */
    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 1. 查询列表
     * GET /api/todos                    查全部
     * GET /api/todos?done=false         只看未完成
     * GET /api/todos?keyword=Spring     标题模糊搜索
     */
    @GetMapping
    public Result<List<Todo>> list(@RequestParam(required = false) Boolean done,
                                   @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<>();
        // 第一个参数是「条件成立才拼进 SQL」，为空时自动忽略这个条件
        wrapper.eq(done != null, Todo::getDone, done);
        wrapper.like(StringUtils.hasText(keyword), Todo::getTitle, keyword);
        // 未完成的排前面，然后按 id 倒序（最新的在最上面）
        wrapper.orderByAsc(Todo::getDone).orderByDesc(Todo::getId);
        return Result.ok(todoService.list(wrapper));
    }

    /**
     * 2. 统计接口（给前端顶部数据卡片用）
     * GET /api/todos/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        long total = todoService.count();
        long finished = todoService.count(new LambdaQueryWrapper<Todo>().eq(Todo::getDone, true));

        Map<String, Long> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("finished", finished);
        data.put("pending", total - finished);
        return Result.ok(data);
    }

    /**
     * 3. 按 id 查询详情
     * GET /api/todos/1
     */
    @GetMapping("/{id}")
    public Result<Todo> getById(@PathVariable Long id) {
        Todo todo = todoService.getById(id);
        return todo == null ? Result.fail(404, "待办不存在") : Result.ok(todo);
    }

    /**
     * 4. 新增
     * POST /api/todos   body: {"title":"xxx","description":"yyy"}
     */
    @PostMapping
    public Result<Todo> create(@RequestBody @Valid Todo todo) {
        todo.setId(null);        // 防止前端传了 id 变成「更新」
        todo.setDone(false);     // 新建的一律未完成
        todo.setCreateTime(null);
        todo.setUpdateTime(null);

        todoService.save(todo);  // 保存后自增主键会自动回填到 todo.id

        // 注意这里又查了一次，为什么？
        // createTime / updateTime 是数据库用 DEFAULT CURRENT_TIMESTAMP 填的，
        // MyBatis-Plus 的 insert 只会回填自增主键，不会把这两个默认值读回来。
        // 如果直接 return Result.ok(todo)，前端拿到的 createTime 就是 null，
        // 列表里会显示成「创建于 null」。
        return Result.ok(todoService.getById(todo.getId()));
    }

    /**
     * 5. 修改
     * PUT /api/todos/1   body: {"title":"新标题"}
     * 注意：MyBatis-Plus 默认只更新非 null 字段，没传的字段保持原值
     */
    @PutMapping("/{id}")
    public Result<Todo> update(@PathVariable Long id, @RequestBody @Valid Todo todo) {
        if (todoService.getById(id) == null) {
            return Result.fail(404, "待办不存在");
        }
        todo.setId(id);
        todoService.updateById(todo);
        return Result.ok(todoService.getById(id));
    }

    /**
     * 6. 切换完成状态（勾选/取消勾选）
     * PATCH /api/todos/1/done
     */
    @PatchMapping("/{id}/done")
    public Result<Todo> toggleDone(@PathVariable Long id) {
        Todo todo = todoService.getById(id);
        if (todo == null) {
            return Result.fail(404, "待办不存在");
        }
        todo.setDone(!Boolean.TRUE.equals(todo.getDone()));
        todoService.updateById(todo);
        return Result.ok(todo);
    }

    /**
     * 7. 删除
     * DELETE /api/todos/1
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean removed = todoService.removeById(id);
        return removed ? Result.ok(null) : Result.fail(404, "待办不存在");
    }
}
