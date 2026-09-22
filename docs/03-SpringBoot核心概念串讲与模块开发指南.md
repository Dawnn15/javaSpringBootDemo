# Spring Boot 核心概念串讲与模块开发指南

> 以本项目（`javaSpringBootDemo` / Todo 待办事项）为标本，从"前端点下一个按钮"开始，把 Spring Boot 的核心机制一次讲透。
>
> 适合：学过 Java 但生疏了、以前端为主、刚开始接触后端的同学。

---

## 阅读建议

| 你现在的状态 | 建议从哪读 |
|---|---|
| 完全没概念，想先建立整体认知 | 第 1 章 → 第 2 章 → 第 3 章 |
| 只想搞懂"依赖注入是什么" | 直接跳第 2 章 |
| 想马上自己写个新功能 | 跳到第 6 章，对照着抄 |
| 记不住注解 | 收藏第 4 章，用到再查 |
| 想学 JWT 登录 | 先读第 2、3 章，再读第 7 章 |

---

## 第 1 章 一次请求的完整旅程

### 1.1 先建立两个"世界"的概念

你项目里其实同时跑着**三个独立进程**：

| 进程 | 端口 | 角色 |
|---|---|---|
| Vite dev server | 5173 | 提供前端页面 + 转发接口请求 |
| Spring Boot（内嵌 Tomcat） | 8080 | 提供后端接口 |
| MySQL | 3306 | 存储数据 |

它们之间只能通过**网络**通信，不能直接调对方的方法。理解这一点，后面所有东西都顺了。

### 1.2 前端：请求怎么出去

你在页面上点"新增"，链路是：

```
App.vue 的 handleCreate()
   → todoApi.create(form)
   → frontend/src/api/todo.js 的 request()
   → fetch('/api/todos', { method: 'POST', body: ... })
```

**关键细节：URL 写的是 `/api/todos`（相对路径），不是完整地址。**

浏览器看到相对路径，会拼上当前页面的地址，于是实际请求的是：

```
http://localhost:5173/api/todos
```

### 1.3 代理：为什么你不会遇到跨域

请求发到 5173 后，被 Vite 接住了。它读 `frontend/vite.config.js`：

```js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

规则是：**路径以 `/api` 开头的请求，由 Vite 在服务器侧转发给 8080**。

| 视角 | 看到什么 |
|---|---|
| 浏览器 | 我在跟 5173 说话，全程同源，没有跨域问题 |
| Vite | 我收到 `/api/todos`，转发给 8080，拿到结果再还回去 |
| Spring Boot | 有人访问我 `POST /api/todos` |

> **所以你项目里其实不会出现 CORS 报错。** `WebCorsConfig` 那道后端配置是"双保险"，防止哪天你不走代理直接访问 8080。

**顺带记住**：`npm run build` 打包后的静态文件没有 Vite 代理了，所以生产环境要么用 Nginx 做同样的转发，要么靠后端的 CORS 配置放行。

### 1.4 后端入口：Tomcat 和 DispatcherServlet

请求到了 8080，第一个接住它的是 **Tomcat**（Spring Boot 内置的 Web 服务器，你不用单独装）。

Tomcat 只管"把 HTTP 报文收下来转成 Java 对象"这件事，真正决定"交给哪个方法处理"的是 Spring MVC 的**前端控制器** `DispatcherServlet`。

它凭什么知道 `POST /api/todos` 该给谁？看 `TodoController`：

```java
@RestController                              // 我是接口类，返回值自动转 JSON
@RequestMapping("/api/todos")                // 我负责这个前缀
public class TodoController {

    @PostMapping                             // POST 到 /api/todos 的，找我这个方法
    public Result<Todo> create(@RequestBody @Valid Todo todo) { ... }
}
```

三条注解叠加，启动时就在一张"路由表"里登记了一行：

```
POST /api/todos  →  TodoController.create
```

请求来了照表派活。**这就是注解的第一层作用：给框架看的登记信息。**

### 1.5 控制层：TodoController 逐行拆解

`src/main/java/org/example/controller/TodoController.java`：

```java
private final TodoService todoService;          // ① 一个接口类型的字段

public TodoController(TodoService todoService) {// ② 构造器要一个 TodoService
    this.todoService = todoService;
}
```

**这两行是全篇最重要的地方**——你没写 `new`，对象却是从外面送进来的。第 2 章专门讲。

再看新增接口：

```java
@PostMapping
public Result<Todo> create(@RequestBody @Valid Todo todo) {
    todo.setId(null);        // 防止前端传了 id 导致变成"更新"
    todo.setDone(false);     // 新建的一律未完成
    todo.setCreateTime(null);
    todo.setUpdateTime(null);

    todoService.save(todo);  // 保存，自增主键会回填到 todo.id

    // 为什么保存完又查一次？
    // createTime / updateTime 是数据库用 DEFAULT CURRENT_TIMESTAMP 填的，
    // MyBatis-Plus 的 insert 只回填自增主键，不会把默认值读回来。
    // 直接 return 的话前端拿到的 createTime 就是 null。
    return Result.ok(todoService.getById(todo.getId()));
}
```

**控制层的职责边界**（重要）：

| 该做 | 不该做 |
|---|---|
| 接收请求、解析参数 | 写业务规则（如"库存不足不能下单"） |
| 参数校验（配合注解） | 写 SQL |
| 调用 Service | 直接操作数据库 |
| 包装返回值 | 复杂的 if/else 业务判断 |

### 1.6 业务层：为什么 Service 是空的还能干活

`service/TodoService.java`：

```java
public interface TodoService extends IService<Todo> {
}
```

`service/impl/TodoServiceImpl.java`：

```java
@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, Todo> implements TodoService {
}
```

两个文件，一行业务代码都没有，但 `todoService.save()`、`.getById()`、`.list()` 全都能用。

**原因**：`ServiceImpl<M, T>` 这个父类已经把单表增删改查全实现好了。你在 `impl` 里只要**加业务规则**，不加就是"标准 CRUD"。

以后要加"标题不能重复"，就写在 impl 里：

```java
@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, Todo> implements TodoService {

    @Override
    public boolean save(Todo todo) {
        long count = lambdaQuery().eq(Todo::getTitle, todo.getTitle()).count();
        if (count > 0) {
            throw new IllegalArgumentException("标题已存在：" + todo.getTitle());
        }
        return super.save(todo);
    }
}
```

> 注意 `save` 方法被 `@Override` 了——这就是"接口 + 实现"分层的好处：上层调用方式不变，底层规则随便加。

### 1.7 数据层：Mapper 为什么不用写 SQL

`mapper/TodoMapper.java`：

```java
public interface TodoMapper extends BaseMapper<Todo> {
}
```

**又是个空接口。** 但 `BaseMapper<Todo>` 提供了 `insert / deleteById / updateById / selectById / selectList / selectCount` 等方法。

SQL 是谁写的？**MyBatis 在运行时用动态代理生成的**。你写的 `todoService.save(todo)` 最终变成：

```sql
INSERT INTO todo (title, description, done, deadline) VALUES (?, ?, ?, ?)
```

字段名从哪来？从 `Todo` 类的字段名 + `application.yml` 里的配置：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true    # create_time ←→ createTime 自动互转
```

### 1.8 全链路时序（把上面串起来）

以"新增一条待办"为例：

```
[浏览器]
  1. 点击按钮 → handleCreate()
  2. fetch('/api/todos', POST, {title: 'xxx'})
       ↓
[Vite :5173]
  3. 路径以 /api 开头 → 转发到 :8080
       ↓
[Tomcat :8080]
  4. 收报文 → 交给 DispatcherServlet
  5. DispatcherServlet 查路由表 → POST /api/todos 匹配 TodoController.create
       ↓
[Controller]
  6. Jackson 把 JSON 反序列化成 Todo 对象
  7. @Valid 触发 @NotBlank / @Size 校验
  8. 调用 todoService.save(todo)   ← 实际是代理对象
       ↓
[Service 代理]
  9. 开启事务（如果加了 @Transactional）
       ↓
[Service 真身]
 10. 调用 baseMapper.insert(todo)
       ↓
[Mapper 代理]
 11. 生成 SQL，从连接池拿连接
       ↓
[MySQL]
 12. 执行 INSERT，返回自增主键
       ↓
 13. 主键回填到 todo.id
 14. 原路返回
       ↓
[Controller]
 15. todoService.getById(id) 再查一次（拿回数据库填的时间字段）
 16. Result.ok(todo) 包装
       ↓
[Jackson]
 17. Result 对象 → JSON 字符串
       ↓
[浏览器]
 18. request() 拆包 body.data
 19. todos.value 更新 → Vue 响应式重渲染界面
```

---

## 第 2 章 依赖注入（重点）

### 2.1 不用 Spring 会写成什么样

```java
// 手动组装
TodoMapper mapper = new TodoMapper();          // 编译都过不去，Mapper 是接口
TodoServiceImpl service = new TodoServiceImpl(mapper);
TodoController controller = new TodoController(service);
```

四个绕不过去的麻烦：

| 麻烦 | 说明 |
|---|---|
| 接口没法 new | `TodoMapper` 没有实现类，MyBatis 要运行时动态生成 |
| 依赖会传染 | ServiceImpl 还要 SqlSessionFactory，后者要 DataSource……手写会写到手断 |
| 换实现要翻全项目 | 明天改用 Redis 缓存，所有 `new` 的地方都要改 |
| 想加事务很麻烦 | 得手动 `setAutoCommit(false)` + try/catch + commit/rollback |

### 2.2 Spring 的答案：把"要什么"写成声明

```java
@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, Todo> implements TodoService {
}
```

```java
@RestController
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {    // 只说"我需要一个 TodoService"
        this.todoService = todoService;
    }
}
```

启动时 Spring 自动完成：

```
1. 扫描到 @Service 的 TodoServiceImpl → 创建实例
   它的父类需要 TodoMapper
   → 创建 TodoMapper 的动态代理
   → 塞进去                                     ← 第 1 次注入

2. 扫描到 @RestController 的 TodoController → 创建实例
   构造器需要一个 TodoService 类型的参数
   → 容器里刚好有 TodoServiceImpl（它实现了 TodoService）
   → 塞进去                                     ← 第 2 次注入
```

**"注入"的就是这个对象实例。** 不是类型，不是配置，就是一个能用的对象。

### 2.3 控制反转（IoC）到底反转了什么

| | 传统 | Spring |
|---|---|---|
| 谁决定对象何时创建 | 你的代码 | 容器 |
| 谁把依赖递过来 | 你的代码 | 容器 |
| 控制权在 | **你**手里（正转） | **容器**手里（反转） |

依赖注入（DI）是 IoC 的**实现手段**——"依赖"是"注入"进去的，所以叫依赖注入。

### 2.4 为什么要"接口 + 实现"两个文件

| 理由 | 说明 |
|---|---|
| **AOP 需要接口** | Spring 加事务/日志默认用 **JDK 动态代理**，而它**只能代理接口**。没接口可能就得改用 CGLIB 子类代理 |
| **换实现不改上层** | Controller 只认 `TodoService` 接口。换成远程调用，Controller 一行不用动 |
| **团队协作** | 接口先行，两人可以并行写（一个写接口+Controller，一个写实现） |

### 2.5 三种注入方式对比

| 方式 | 写法 | 评价 |
|---|---|---|
| 构造器注入 | `public X(Y y) { this.y = y; }` | **推荐**。字段可 `final`，依赖一目了然，好写测试 |
| 字段注入 | `@Autowired private Y y;` | 不推荐。破坏 `final`，依赖藏在中间，测试要反射 |
| Setter 注入 | `@Autowired public void setY(Y y)` | 很少用。适合可选依赖 |

**你项目用的是构造器注入**——注意它连 `@Autowired` 都不需要写（Spring 4.3 起，单构造器会自动注入）。

### 2.6 用前端概念类比

| 你熟悉的 Vue | Spring |
|---|---|
| `import TodoList from './TodoList.vue'` | 构造器参数声明 `TodoService` |
| 组件由 Vue 实例化 | Bean 由 Spring 实例化 |
| `provide('api', v)` / `inject('api')` | `@Service` + 构造器注入 |
| 组件树 | Bean 依赖图 |

**一句话**：你在前端从不手动 `new VueComponent()`，靠 `import` 声明；在后端你就从不手动 `new Service()`，靠构造器声明。**这是同一种思维方式。**

---

## 第 3 章 AOP（面向切面编程）

### 3.1 代理模式：你拿到的不是"真身"

调用 `todoService.save(todo)` 时，拿到的其实是**代理对象**：

```
TodoController
    ↓ 调用 save()
[代理对象]  ← Spring 生成
    ├─ 1. 开启事务 / 记日志
    ├─ 2. 调用真正的 save()
    └─ 3. 提交或回滚
        ↓
TodoServiceImpl（你写的代码）
```

**代理和真身实现同一个接口**，调用方完全分不出来。额外逻辑只在代理里加一次，**业务代码零改动**——这就是 AOP 的价值。

### 3.2 项目里的三个 AOP 落点

**落点 1：全局异常处理（你已经写了）**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) { ... }
}
```

效果：你 7 个接口方法里**一个 try-catch 都没有**，但出错时格式依然统一。

**落点 2：分页拦截器（你已经配了）**

```java
interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
```

效果：你写 `selectPage(...)`，框架自动在 SQL 末尾拼 `LIMIT`。业务代码里没有一行 LIMIT。

**落点 3：事务（还没用，但马上会用）**

```java
@Transactional
public void transfer(Long from, Long to, int amount) { ... }
```

| | 手动事务 | @Transactional |
|---|---|---|
| 代码量 | `setAutoCommit(false)` + try/catch + commit/rollback | 一行注解 |
| 出错时 | 需要自己保证回滚，容易漏 | 自动回滚 |

### 3.3 四个术语

| 术语 | 含义 | 项目中对应 |
|---|---|---|
| 切面 Aspect | 要织入的额外逻辑 | `GlobalExceptionHandler` 类 |
| 连接点 JoinPoint | 能被织入的位置 | 每个 Controller 方法调用 |
| 切点 Pointcut | 选中哪些连接点 | "所有 Controller 抛出的校验异常" |
| 通知 Advice | 什么时机执行 | `@ExceptionHandler`（异常发生时） |

---

## 第 4 章 注解速查表

### 4.1 最关键的 6 个（先记住这些）

| 注解 | 贴在哪 | 作用 |
|---|---|---|
| `@SpringBootApplication` | 启动类 | 开启自动配置 + 组件扫描 |
| `@RestController` | Controller 类 | 标记为接口类，返回值转 JSON |
| `@Service` | Service 实现类 | 标记为业务 Bean，交给容器管理 |
| `@MapperScan` | 启动类 | 告诉 MyBatis 去哪扫 Mapper 接口 |
| `@RequestBody` | Controller 方法参数 | 请求体 JSON → Java 对象 |
| `@Transactional` | Service 方法 | 开启事务（AOP 实现） |

### 4.2 完整分类表

**启动类**

| 注解 | 作用 | 漏了会怎样 |
|---|---|---|
| `@SpringBootApplication` | 自动配置 + 扫描本包及子包 | Spring 完全不启动 |
| `@MapperScan("org.example.mapper")` | 指定 Mapper 接口所在包 | Mapper 注入失败，启动报错 |

**控制层**

| 注解 | 作用 |
|---|---|
| `@RequestMapping("/api/todos")` | 类级 URL 前缀 |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping` | 对应 HTTP 方法 |
| `@RequestBody` | 请求体 → 对象 |
| `@PathVariable` | URL 里的 `{id}` → 参数 |
| `@RequestParam` | 查询串 `?keyword=x` → 参数 |
| `@Valid` | 触发实体上的校验注解 |

**业务层 / 数据层**

| 注解 | 作用 |
|---|---|
| `@Service` | 标记业务 Bean |
| `@Transactional` | 事务 |
| `@Mapper` | 标记 MyBatis 接口（本项目用 `@MapperScan` 代替） |

**实体类**

| 注解 | 作用 |
|---|---|
| `@TableName("todo")` | 类 ↔ 表名 |
| `@TableId(type = IdType.AUTO)` | 字段 ↔ 主键，数据库自增 |
| `@Data`（Lombok） | 编译期生成 getter/setter/toString |
| `@NotBlank` / `@NotNull` / `@Size` / `@PositiveOrZero` | 校验规则 |
| `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` | 日期序列化格式 |

**配置类**

| 注解 | 作用 |
|---|---|
| `@Configuration` | 标记配置类 |
| `@Bean` | 方法返回值交给容器管理 |

**切面 / 注入**

| 注解 | 作用 |
|---|---|
| `@RestControllerAdvice` | 声明针对所有 Controller 的切面 |
| `@ExceptionHandler` | 指定处理哪种异常 |
| `@Autowired` | 手动指定注入点（本项目用构造器注入，不需要它） |

---

## 第 5 章 Java 基础复习（只讲项目里真实用到的）

| 语法点 | 出现位置 | 一句话解释 |
|---|---|---|
| **接口与实现** | `TodoService` / `TodoServiceImpl` | 接口声明"能做什么"，实现类写"怎么做" |
| **泛型** | `Result<T>`、`BaseMapper<Todo>` | 类型占位符。`Result<Todo>` 表示 data 字段是 Todo |
| **继承** | `ServiceImpl<TodoMapper, Todo>` | 子类直接获得父类方法，不是复制粘贴 |
| **`final`** | `private final TodoService todoService` | 只能赋一次值，配合构造器注入 |
| **方法引用** | `Todo::getTitle` | 把"取 title 字段"这件事当参数传给框架 |
| **Lambda** | `list.stream().map(FieldError::getDefaultMessage)` | 简化匿名内部类 |
| **可变参数** | `SpringApplication.run(DemoApplication.class, args)` | 参数个数可变 |
| **包装类型 vs 基本类型** | `Boolean done` 而不是 `boolean done` | 包装类型可为 null，能表达"未传值" |

### 重点说 `Todo::getTitle` 这种写法

它就是 **Java 8 的方法引用**，等价于给你一张"取哪个字段"的地址卡：

```java
wrapper.like(StringUtils.hasText(keyword), Todo::getTitle, keyword);
```

对比手写字符串：

```java
wrapper.like(StringUtils.hasText(keyword), "title", keyword);   // 不推荐
```

| 写法 | 风险 |
|---|---|
| `"title"` 字符串 | 字段改名后编译不报错，运行时才炸 |
| `Todo::getTitle` | 字段改名编译直接失败，安全 |

---

## 第 6 章 从零写一个新模块（实战）

以"图书管理 Book"为例，完整走一遍。**目标**：自己写完后，前端能对图书增删改查。

### 6.1 第 0 步：想清楚字段（最重要，别跳）

设计表之前先问自己四个问题：

| 问题 | 对图书表的回答 |
|---|---|
| 有哪些属性？ | 书名、作者、ISBN、价格、库存 |
| 哪些必填？ | 书名、作者、价格、库存 |
| 有没有"唯一"要求？ | ISBN 理论上唯一（本例先不做约束） |
| 常用什么条件查？ | 按书名搜、按作者筛 |

**字段设计原则**：

| 原则 | 说明 |
|---|---|
| 主键统一用 `BIGINT AUTO_INCREMENT` | 别用业务字段当主键（ISBN 可能填错要改） |
| 字符串长度按实际给，别一律 `VARCHAR(255)` | 书名 200、作者 100 够了 |
| 金额用 `DECIMAL(10,2)`，**不要用 FLOAT/DOUBLE** | 浮点有精度误差，钱不能丢 |
| 时间字段用数据库默认值 | `DEFAULT CURRENT_TIMESTAMP`，不用 Java 手动塞 |
| 常用查询条件加索引 | `KEY idx_author (author)` |
| 表名用单数、字段名用下划线 | 团队规范，MyBatis-Plus 会自动转驼峰 |

### 6.2 第 1 步：建表

在 `src/main/resources/sql/` 下新建 `book.sql`（或追加进 `schema.sql`）：

```sql
CREATE TABLE book
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(200)   NOT NULL COMMENT '书名',
    author      VARCHAR(100)   NOT NULL COMMENT '作者',
    isbn        VARCHAR(20)    DEFAULT NULL COMMENT 'ISBN',
    price       DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '价格',
    stock       INT            NOT NULL DEFAULT 0 COMMENT '库存',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_author (author)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='图书表';
```

执行：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 demo_db < src/main/resources/sql/book.sql
```

### 6.3 第 2 步：实体类

新建 `src/main/java/org/example/entity/Book.java`：

```java
package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图书实体 —— 对应数据库表 book
 */
@Data
@TableName("book")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "书名不能为空")
    @Size(max = 200, message = "书名最长 200 个字符")
    private String title;

    @NotBlank(message = "作者不能为空")
    @Size(max = 100, message = "作者最长 100 个字符")
    private String author;

    @Size(max = 20, message = "ISBN 最长 20 个字符")
    private String isbn;

    @NotNull(message = "价格不能为空")
    @PositiveOrZero(message = "价格不能为负数")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @PositiveOrZero(message = "库存不能为负数")
    private Integer stock;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
```

**三个容易错的地方**：

| 坑 | 说明 |
|---|---|
| 金额类型 | 一定用 `BigDecimal`，别用 `Double` |
| 校验注解的组合 | `@NotNull` 管对象不为 null，`@NotBlank` 管字符串非空白，`@Size` 管长度 |
| 时间字段名 | 数据库 `create_time` ↔ Java `createTime`，靠下划线转驼峰配置自动对齐 |

### 6.4 第 3 步：Mapper 接口

新建 `src/main/java/org/example/mapper/BookMapper.java`：

```java
package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.entity.Book;

/**
 * 图书 Mapper —— 继承 BaseMapper 就有全套单表方法，一行不用写
 */
public interface BookMapper extends BaseMapper<Book> {
}
```

**不用加 `@Mapper`**，因为启动类的 `@MapperScan("org.example.mapper")` 已经覆盖了这个包。

### 6.5 第 4 步：Service 接口 + 实现

`src/main/java/org/example/service/BookService.java`：

```java
package org.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.entity.Book;

public interface BookService extends IService<Book> {
}
```

`src/main/java/org/example/service/impl/BookServiceImpl.java`：

```java
package org.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.entity.Book;
import org.example.mapper.BookMapper;
import org.example.service.BookService;
import org.springframework.stereotype.Service;

@Service
public class BookServiceImpl extends ServiceImpl<BookMapper, Book> implements BookService {
}
```

**注意两个泛型的顺序**：`ServiceImpl<Mapper类型, 实体类型>`。

### 6.6 第 5 步：Controller

新建 `src/main/java/org/example/controller/BookController.java`：

```java
package org.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.example.common.Result;
import org.example.entity.Book;
import org.example.service.BookService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图书接口
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /** 列表，支持按书名模糊搜索 */
    @GetMapping
    public Result<List<Book>> list(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(keyword), Book::getTitle, keyword);
        wrapper.orderByDesc(Book::getId);
        return Result.ok(bookService.list(wrapper));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<Book> getById(@PathVariable Long id) {
        Book book = bookService.getById(id);
        return book == null ? Result.fail(404, "图书不存在") : Result.ok(book);
    }

    /** 新增 */
    @PostMapping
    public Result<Book> create(@RequestBody @Valid Book book) {
        book.setId(null);
        book.setCreateTime(null);
        book.setUpdateTime(null);
        bookService.save(book);
        return Result.ok(bookService.getById(book.getId()));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<Book> update(@PathVariable Long id, @RequestBody @Valid Book book) {
        if (bookService.getById(id) == null) {
            return Result.fail(404, "图书不存在");
        }
        book.setId(id);
        bookService.updateById(book);
        return Result.ok(bookService.getById(id));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean removed = bookService.removeById(id);
        return removed ? Result.ok(null) : Result.fail(404, "图书不存在");
    }
}
```

### 6.7 第 6 步：前端对接

新建 `frontend/src/api/book.js`：

```js
/**
 * 图书接口封装
 */
const BASE = '/api/books'

async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  if (!res.ok) {
    throw new Error(`请求失败 HTTP ${res.status}`)
  }
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(body.message || '操作失败')
  }
  return body.data
}

export const bookApi = {
  list(params = {}) {
    const qs = new URLSearchParams(params).toString()
    return request(qs ? `${BASE}?${qs}` : BASE)
  },
  detail(id) {
    return request(`${BASE}/${id}`)
  },
  create(data) {
    return request(BASE, { method: 'POST', body: JSON.stringify(data) })
  },
  update(id, data) {
    return request(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(data) })
  },
  remove(id) {
    return request(`${BASE}/${id}`, { method: 'DELETE' })
  }
}
```

在组件里使用：

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { bookApi } from './api/book'

const books = ref([])

async function load() {
  books.value = await bookApi.list()
}

async function add() {
  await bookApi.create({ title: '三体', author: '刘慈欣', price: 23.5, stock: 10 })
  await load()
}

onMounted(load)
</script>
```

### 6.8 第 7 步：验证

**先重启后端**（新增了类，必须重启才能被扫描到），然后：

```bash
# 新增
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  --data-binary @book.json

# 列表
curl http://localhost:8080/api/books
```

用文件传 JSON 是为了避开 Windows 控制台的中文编码问题（直接写在命令行里会变乱码）。

### 6.9 六步走速查

| 步骤 | 做什么 | 文件 | 关键点 |
|---|---|---|---|
| 0 | 想清字段 | — | 主键自增、金额用 DECIMAL、时间用默认值 |
| 1 | 建表 | `sql/book.sql` | 执行后 `SHOW TABLES` 确认 |
| 2 | 实体 | `entity/Book.java` | `@TableName` + `@TableId` + `@Data` + 校验注解 |
| 3 | Mapper | `mapper/BookMapper.java` | 继承 `BaseMapper<Book>`，空接口 |
| 4 | Service | `service/BookService.java`<br>`service/impl/BookServiceImpl.java` | 接口继承 `IService<Book>`；实现继承 `ServiceImpl<BookMapper, Book>` |
| 5 | Controller | `controller/BookController.java` | `@RestController` + `@RequestMapping`，构造器注入 Service |
| 6 | 前端 | `api/book.js` + 组件 | 复制 todo.js 改路径即可 |
| 7 | 重启验证 | — | **必须重启后端**，新类才会被扫描 |

### 6.10 常见卡壳点

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `Field bookService required a bean` | `BookServiceImpl` 忘了加 `@Service` | 补上注解 |
| 接口 404 | 忘了重启 / `@RequestMapping` 路径写错 | 重启 + 检查路径 |
| 返回字段全是 null | 数据库字段名和 Java 名对不上 | 确认 `map-underscore-to-camel-case: true` |
| 新增后 `createTime` 是 null | MyBatis-Plus 只回填主键 | `save()` 后补一次 `getById()` |
| `Invalid bound statement` | Mapper 没被扫描到 | 检查 `@MapperScan` 的包路径 |
| 中文乱码 | 建库/建表没指定字符集 | `utf8mb4` |

---

## 第 7 章 下一步：JWT 登录鉴权预习

你说"只知道把账号密码送过去，后端返回 token"。下面把**从零到能跑**的完整地图给你，每一块都标注了它和你现在会的东西的联系。

### 7.1 先搞懂：为什么需要 token

| 事实 | 后果 |
|---|---|
| HTTP 是**无状态**的 | 服务器处理完一个请求就"忘了"你是谁，下个请求它不认识了 |
| 但接口需要知道"你是谁" | 否则任何人都能删你的数据 |

所以需要一个东西，在每次请求里证明"我是我"。这东西就是 **token**。

### 7.2 两条技术路线

| | Session（传统） | JWT（现在主流） |
|---|---|---|
| 状态存在哪 | **服务器内存/Redis** | **客户端**（token 自带信息） |
| 服务器要存东西吗 | 要 | 不要 |
| 多台服务器 | 要共享 session | 天然支持（各自验签即可） |
| 撤销难度 | 容易（删 session） | 难（签发后到期前一直有效） |
| 你项目适合 | — | **推荐先用这个练手** |

### 7.3 JWT 长什么样

一个 token 是三段用点号连起来的字符串：

```
eyJhbGciOiJIUzI1NiJ9 . eyJ1c2VySWQiOjEsImV4cCI6MTc... . SflKxwRJSMeKKF2QT4fwpM
└──── Header ────┘   └──────── Payload ────────┘   └──── Signature ────┘
     用什么算法             装了什么数据                 防篡改签名
```

| 段 | 内容 | 是否加密 |
|---|---|---|
| Header | 算法类型（如 HS256） | 否，Base64 编码 |
| Payload | 你想带的数据（userId、过期时间） | **否，Base64 不是加密，谁都能解开看** |
| Signature | 用服务端密钥对前两段签名 | — |

**两条铁律**：

1. **Payload 里绝对不能放密码等敏感信息**——它只是编码，不是加密
2. **密钥（secret）绝对不能泄露**——泄露了别人就能伪造 token

### 7.4 完整登录流程

```
【注册登录阶段】
1. 前端：POST /api/auth/login  { username, password }
2. 后端：查用户 → 用 BCrypt 比对密码哈希
3. 后端：验证通过 → 用密钥签发 JWT（payload 里放 userId、过期时间）
4. 后端：返回 { token: "eyJ..." }
5. 前端：存起来（localStorage 或 Cookie）

【后续请求阶段】
6. 前端：每次请求带 Authorization: Bearer eyJ...
7. 后端：过滤器/拦截器 拦下请求 → 取出 token → 验签 → 解析出 userId
8. 后端：把 userId 放进请求上下文 → Controller 里能直接拿到当前用户
9. 验签失败或过期 → 返回 401，前端跳登录页
```

**第 7、8 步就是 AOP 思想的又一次应用**——不改任何 Controller 代码，统一给所有接口加上鉴权。

### 7.5 落到 Spring Boot 的技术选型

| 方案 | 复杂度 | 适合 |
|---|---|---|
| **HandlerInterceptor + jjwt 库** | 低 | **推荐你先走这条**，能看清每一步 |
| Spring Security + JWT | 高 | 生产项目主流，但概念多、配置绕 |

**推荐先手写拦截器版本**——理解了原理，以后上 Spring Security 才不会懵。

### 7.6 需要新增的文件（手写拦截器版）

| 文件 | 作用 |
|---|---|
| `entity/User.java` | 用户表实体（username、password 存哈希） |
| `mapper/UserMapper.java` | 用户数据访问 |
| `service/AuthService.java` + impl | 注册、登录、签发 token |
| `util/JwtUtil.java` | 生成/解析/校验 token 的工具类 |
| `config/JwtInterceptor.java` | 拦截请求，校验 token |
| `config/WebConfig.java` | 注册拦截器，指定哪些路径要鉴权 |
| `controller/AuthController.java` | `/api/auth/login`、`/api/auth/register` |
| `pom.xml` | 加 `jjwt-api` + `jjwt-impl` + `jjwt-jackson`，或 `java-jwt` |

前端还要：

| 改动 | 说明 |
|---|---|
| `api/request.js` | 统一给请求加 `Authorization` 头 |
| 登录页组件 | 表单 → 调 login → 存 token |
| 路由守卫 | 没 token 就跳登录页 |

### 7.7 学习路线（按顺序，别跳）

| 阶段 | 目标 | 完成后能做到 |
|---|---|---|
| 1 | 建 user 表，写 User/UserMapper | 能按用户名查用户 |
| 2 | 密码用 BCrypt 存哈希 | 注册接口能跑通 |
| 3 | 用 jjwt 生成一个 token 并打印出来 | 能手动解出 payload |
| 4 | 写 `/api/auth/login` 返回 token | 前端能拿到 token |
| 5 | 写拦截器，校验 `Authorization` 头 | 带错 token 返回 401 |
| 6 | 把当前登录用户放进上下文 | Controller 里能拿到当前用户 id |
| 7 | 前端存 token + 请求自动带上 | 完整闭环 |
| 8 | 加退出登录、token 过期处理 | 体验完整 |

### 7.8 预习时要记住的三个坑

| 坑 | 说明 |
|---|---|
| **Payload 不是加密的** | 别往里放敏感信息；想看内容去 jwt.io 粘贴一下就懂了 |
| **密码必须哈希存储** | 用 BCrypt，永远别存明文，也别用 MD5（可彩虹表反查） |
| **密钥别写死在代码里** | 放 `application.yml` 并用环境变量覆盖，参考本项目 `${DB_PASSWORD:123456}` 的写法 |

---

## 附录 A：本项目关键技术决策记录

| 决策 | 原因 |
|---|---|
| MyBatis-Plus 用 3.5.12 而不是 3.5.17 | 3.5.13 起 `IService`/`ServiceImpl` 从 `extension.service` 挪到了 `spring.service`，网上教程全是旧包名 |
| 用构造器注入而非 `@Autowired` | 字段可 `final`，依赖显式，便于测试 |
| `application.yml` 密码用占位符 | `${DB_PASSWORD:123456}`，避免明文进 git |
| 前后端都配了跨域处理 | Vite 代理 + 后端 CORS，双保险 |
| 新增接口保存后又查一次 | MyBatis-Plus 不回读数据库默认值，避免前端拿到 null 时间 |

## 附录 B：遇到问题时的排查顺序

```
1. 看控制台报错的第一行（真正的错误通常在最后一段 Caused by）
2. 接口 404  → 路径对不对？@RequestMapping 拼起来是什么？重启了吗？
3. 接口 500  → 看后端控制台堆栈
4. 数据没变   → application.yml 里 log-impl: StdOutImpl 会打印真实 SQL，照着 SQL 去数据库手动执行
5. 前端拿不到 → F12 看 Network 面板，请求发出去了吗？响应是什么？
6. 认证失败   → token 带了吗？格式是 Bearer 吗？过期了吗？
```

---

> 文档会随项目一起更新。遇到新坑，往第 6.10 节的表里加一行。
