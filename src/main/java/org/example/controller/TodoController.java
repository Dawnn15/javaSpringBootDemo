package org.example.controller;

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
 * 待办事项接口 —— 控制层（原生 MyBatis 版）
 *
 * <p>对比 MyBatis-Plus 版本：这里不再使用 {@code LambdaQueryWrapper} 拼条件，
 * 而是把 keyword / done 作为参数直接传给 Service，让 Service 透传到 Mapper 的动态 SQL。
 * Service 接口里的 {@code list(String, Boolean)} 已经做了这个封装。
 *
 * <p>注意看这里：
 * <ul>
 *   <li>{@code list()} —— 一行就能写完（MP 版本需要 4 行 wrapper 拼接）</li>
 *   <li>{@code stats()} —— Service 已经把 total/done/done 算好了，这里只做拼装</li>
 *   <li>{@code toggleDone()} —— 新增的 {@code toggleDone(id, done)} 方法，先查再切，避免覆盖其他字段</li>
 * </ul>
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
     * GET /api/todos?done=true&keyword=x 两个条件合用
     */
    @GetMapping
    public Result<List<Todo>> list(@RequestParam(required = false) Boolean done,
                                   @RequestParam(required = false) String keyword) {
        // 注意：原 MP 版本按 "未完成在前、id 倒序" 排；这里 mapper 里写死了 id 倒序
        // 如果要保持完全一致的排序，可以在 mapper.findList 里加 ORDER BY done ASC, id DESC
        return Result.ok(todoService.list(
                StringUtils.hasText(keyword) ? keyword : null,
                done
        ));
    }

    /**
     * 2. 统计接口（给前端顶部数据卡片用）
     * GET /api/todos/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> stats = todoService.stats();
        // 给前端多算一个「未完成数」，省得前端自己减
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("total", stats.get("total"));
        data.put("finished", stats.get("done"));
        data.put("pending", stats.get("total") - stats.get("done"));
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
        // Service 层 save() 已经做了：id=null、done=false 的兜底
        // 这里什么都不用做，直接调
        Todo saved = todoService.save(todo);

        // save() 内部已经回读了一次数据库（修复数据库 DEFAULT CURRENT_TIMESTAMP 不回填的坑），
        // 所以这里可以直接返回 saved，不需要再查。
        return Result.ok(saved);
    }

    /**
     * 5. 修改
     * PUT /api/todos/1   body: {"title":"新标题"}
     */
    @PutMapping("/{id}")
    public Result<Todo> update(@PathVariable Long id, @RequestBody @Valid Todo todo) {
        if (todoService.getById(id) == null) {
            return Result.fail(404, "待办不存在");
        }
        todo.setId(id);
        // updateById 的 mapper 用 <set> + <if>，只会更新非 null 字段
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
        // 用专门的 toggleDone 而不是 updateById：避免误改其他字段
        boolean newDone = !Boolean.TRUE.equals(todo.getDone());
        todoService.toggleDone(id, newDone);
        todo.setDone(newDone);
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