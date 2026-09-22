# 04  MyBatis 改造过程与 MyBatis-Plus 对照

> **目的**：把同一个 Todo 后端分别用 MyBatis-Plus 和原生 MyBatis 各实现一遍，并排放在两个分支，让你看清「MP 到底替你干了什么」。
>
> **仓库布局**（两个分支并存，切换即对照）：
>
> | 分支 | 数据层 | 状态 |
> |---|---|---|
> | `main` | MyBatis-Plus 3.5.12 | 完整可用 |
> | `feat/mybatis-pure` | 原生 MyBatis 3.0.5 | 完整可用，已通过 32 项接口回归 |
>
> **怎么切**：
> ```bash
> cd C:\Users\12492\Desktop\studyProject\java\javaSpringBootDemo
> git checkout main              # 看 MyBatis-Plus 版
> git checkout feat/mybatis-pure # 看原生 MyBatis 版
> ```

---

## 1. 改造全景：8 个文件动了哪些？

| 文件 | MP 版本做了什么 | 原生 MyBatis 版做了什么 | 改动量 |
|---|---|---|---|
| `pom.xml` | 引 `mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser` | 引 `mybatis-spring-boot-starter` 3.0.5 | 8 行 |
| `application.yml` | 用 `mybatis-plus.configuration` + `global-config.db-config` | 用 `mybatis.configuration` + `mapper-locations` + `type-aliases-package` | 6 行 |
| `entity/Todo.java` | 加 `@TableName("todo")` + `@TableId(type = IdType.AUTO)` | 这两个注解全删 | 删 2 行 |
| `mapper/TodoMapper.java` | 空接口，只 `extends BaseMapper<Todo>` | 8 个方法，每个都手写 SQL 注解 | 0 → 100 行 |
| `service/TodoService.java` | 空接口，只 `extends IService<Todo>` | 7 个方法声明（list/getById/save/updateById/removeById/toggleDone/stats） | 0 → 50 行 |
| `service/impl/TodoServiceImpl.java` | 1 行 `@Service`，**方法体全部留空**（父类 `ServiceImpl` 已包办） | 7 个方法**全部自己实现**，每个都调 mapper 再处理返回 | 1 → 100 行 |
| `controller/TodoController.java` | 用 `LambdaQueryWrapper` 拼条件（4 行代码） | 直接传 `keyword/done` 参数给 service | 微调 |
| `config/MybatisPlusConfig.java` | 注册分页拦截器 | **整个文件删除**（原生 MyBatis 没有分页拦截器概念） | 整个文件 |

| 维度 | MP 版 | 原生 MyBatis 版 |
|---|---|---|
| 后端 Java 文件数 | 11 | **10**（少一个 config） |
| 业务代码总量 | 约 350 行 | **约 470 行**（+34%，主要在 service impl） |
| 写 SQL 的次数 | 0 | **8 次** |

---

## 2. 七个文件左右栏对照（核心干货）

> 每一节左边是 MP 怎么写，右边是原生 MyBatis 怎么写。
> 把这一节读懂，你对「MyBatis-Plus 在帮你什么」就会彻底清晰。

### 2.1 `pom.xml`：一个 starter 替换另一个

**MP 版**：
```xml
<!-- 注释里写「单表增删改查不用写 SQL」 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.12</version>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>   <!-- MP 自带 SQL 解析器 -->
    <version>3.5.12</version>
</dependency>
```

**原生 MyBatis 版**：
```xml
<!-- 注释里写「单表增删改查都要自己写 SQL」 -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.5</version>
</dependency>
<!-- 没有 jsqlparser 了 -->
```

**差别**：

| | MP | 原生 MyBatis |
|---|---|---|
| 依赖坐标 | `com.baomidou:mybatis-plus-spring-boot3-starter` | `org.mybatis.spring.boot:mybatis-spring-boot-starter` |
| 需要的额外依赖 | `mybatis-plus-jsqlparser`（SQL 解析用） | 无 |
| 帮你做的 | 装配 BaseMapper 的实现类、注册分页拦截器、提供 IService 接口 | 只装配 Mapper 接口和 SqlSessionFactory，**Mapper 接口要你写实现**（其实是用动态代理） |

---

### 2.2 `application.yml`：配置项也是一对一

**MP 版**：
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # create_time -> createTime
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto                     # 全局默认主键策略：自增
  mapper-locations: classpath*:/mapper/**/*.xml
```

**原生 MyBatis 版**：
```yaml
mybatis:
  configuration:
    map-underscore-to-camel-case: true   # 同样需要
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  mapper-locations: classpath*:/mapper/**/*.xml   # 同样路径
  type-aliases-package: org.example.entity        # ★ MP 没有这条
```

**差别**：
- 顶层 key 从 `mybatis-plus` 变成 `mybatis`
- 去掉 `global-config.db-config.id-type: auto`（**主键策略现在要写在每个 @Insert 上**）
- 多一条 `type-aliases-package`：原生 MyBatis 默认不知道 `Todo` 是谁，你得告诉它在哪个包找

---

### 2.3 `entity/Todo.java`：去掉两个注解

**MP 版**（多两行）：
```java
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;

@Data
@TableName("todo")                 // ← 告诉 MP：类对应哪张表
public class Todo {
    @TableId(type = IdType.AUTO)   // ← 告诉 MP：id 是主键、数据库自增
    private Long id;
    // ...
}
```

**原生 MyBatis 版**：
```java
// 不再 import com.baomidou.*
@Data
public class Todo {
    private Long id;   // 主键回填通过 mapper 的 @Options 声明
    // ...
}
```

**差别**：
- `@TableName` 删掉 —— 原生 MyBatis 默认就用类名当表名（todo），或者用 `@Results/@Result` 显式映射
- `@TableId` 删掉 —— 改在 mapper 的 `@Insert` 上写 `@Options(useGeneratedKeys=true, keyProperty="id")`

---

### 2.4 `mapper/TodoMapper.java`：从空接口到 100 行

**MP 版**（**5 行**）：
```java
public interface TodoMapper extends BaseMapper<Todo> {
    // 啥也不用写，继承就有了：
    // insert, deleteById, updateById, selectById, selectList,
    // selectCount, selectMaps ...
}
```

**原生 MyBatis 版**（**~100 行**）：每个方法都自己写。摘录新增方法：

```java
@Insert("""
    INSERT INTO todo (title, description, done, deadline)
    VALUES (#{title}, #{description}, #{done}, #{deadline})
    """)
@Options(useGeneratedKeys = true, keyProperty = "id")
int insert(Todo todo);
```

摘录动态 SQL 查询（**最关键的一条**）：
```java
@Select("""
    <script>
    SELECT id, title, description, done, deadline, create_time, update_time
    FROM todo
    <where>
      <if test="titleLike != null and titleLike != ''">
        AND title LIKE CONCAT('%', #{titleLike}, '%')
      </if>
      <if test="done != null">
        AND done = #{done}
      </if>
    </where>
    ORDER BY id DESC
    </script>
    """)
List<Todo> findList(@Param("titleLike") String titleLike,
                    @Param("done") Boolean done);
```

摘录动态 UPDATE（**第二条最容易踩的坑**）：
```java
@Update("""
    <script>
    UPDATE todo
    <set>
      <if test="title != null">title = #{title},</if>
      <if test="description != null">description = #{description},</if>
      <if test="done != null">done = #{done},</if>
      <if test="deadline != null">deadline = #{deadline},</if>
    </set>
    WHERE id = #{id}
    </script>
    """)
int updateById(Todo todo);
```

**5 个关键注解对照**：

| 注解 | MP 版 | 原生 MyBatis 版 | 区别 |
|---|---|---|---|
| `insert` | `BaseMapper.insert(Todo)`，表名/列名全自动 | `@Insert` 自己写 SQL + `@Options(useGeneratedKeys=true, keyProperty="id")` 自己配主键回填 | MP 全自动 |
| `selectById` | `BaseMapper.selectById(Long id)` | `@Select("...WHERE id = #{id}")` 自己写 | MP 自动生成 |
| `selectList` | `BaseMapper.selectList(Wrapper)` | `@Select` + `<where><if></where>` 动态 SQL 自己拼 | MP 的 Wrapper 帮你拼 |
| `updateById` | `BaseMapper.updateById(Todo)` 默认只更新非 null | `@Update` + `<set><if></set>` 自己写动态更新 | MP 帮你处理 null |
| `deleteById` | `BaseMapper.deleteById(Long id)` | `@Delete` 自己写 | MP 自动生成 |

---

### 2.5 `service/TodoService.java`：接口里 0 行 vs 50 行

**MP 版**：
```java
public interface TodoService extends IService<Todo> {
    // 空接口！save/remove/updateById/getById/list/page/count 全在 IService 里
}
```

**原生 MyBatis 版**：
```java
public interface TodoService {
    List<Todo> list(String keyword, Boolean done);
    Todo getById(Long id);
    Todo save(Todo todo);
    boolean updateById(Todo todo);
    boolean removeById(Long id);
    boolean toggleDone(Long id, boolean done);
    Map<String, Long> stats();
    // 7 个方法全部自己声明
}
```

**差别**：
| 方法 | MP 版写法 | 原生版写法 |
|---|---|---|
| 列表 | `service.lambdaQuery().eq(...).list()` | `service.list(keyword, done)` |
| 改单个 | `service.updateById(todo)` | `service.updateById(todo)` |
| 分页 | `service.page(Page.of(...))` | 自己写 LIMIT 或用 PageHelper |

---

### 2.6 `service/impl/TodoServiceImpl.java`：1 行空实现 vs 100 行实打实

**MP 版**（**1 行空实现**，整个类就这么多）：
```java
@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, Todo> implements TodoService {
    // 啥也不用写
}
```

**原生 MyBatis 版**（**~100 行**，7 个方法自己写完）。摘两个：

```java
@Service
public class TodoServiceImpl implements TodoService {

    private final TodoMapper todoMapper;
    public TodoServiceImpl(TodoMapper todoMapper) {
        this.todoMapper = todoMapper;
    }

    @Override
    public Todo save(Todo todo) {
        // 默认值兜底
        todo.setId(null);
        todo.setDone(false);

        // 写库（@Options 会回填 id）
        todoMapper.insert(todo);

        // 回读数据库默认值（createTime / updateTime）
        return todoMapper.findById(todo.getId());
    }

    @Override
    public boolean updateById(Todo todo) {
        if (todo.getId() == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        int rows = todoMapper.updateById(todo);
        return rows > 0;
    }
    // ... 其他 5 个方法
}
```

**直观感受**：MP 版本这一行能写完的东西，原生版要写 100 行。这就是 MP 的全部价值。

---

### 2.7 `controller/TodoController.java`：从 4 行 wrapper 到 1 行传参

**MP 版**（用 `LambdaQueryWrapper` 拼条件）：
```java
@GetMapping
public Result<List<Todo>> list(@RequestParam(required = false) Boolean done,
                               @RequestParam(required = false) String keyword) {
    LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(done != null, Todo::getDone, done);
    wrapper.like(StringUtils.hasText(keyword), Todo::getTitle, keyword);
    wrapper.orderByAsc(Todo::getDone).orderByDesc(Todo::getId);
    return Result.ok(todoService.list(wrapper));
}
```

**原生 MyBatis 版**（条件透传给 service）：
```java
@GetMapping
public Result<List<Todo>> list(@RequestParam(required = false) Boolean done,
                               @RequestParam(required = false) String keyword) {
    return Result.ok(todoService.list(
        StringUtils.hasText(keyword) ? keyword : null,
        done
    ));
}
```

---

## 3. 改造时撞到的 4 个真实坑

### 坑 1：INSERT 写太完整，把数据库默认值挤掉

```java
// 错：把全部列都写进 SQL，但 createTime 没传值
@Insert("INSERT INTO todo (..., create_time, update_time) VALUES (..., #{createTime}, #{updateTime})")

// 对：要么不写 create_time，让数据库 DEFAULT 填；要么用 #{createTime} 但在 service 里 new Date()
// 原生 MyBatis 这里要你「显式」决策，MP 会按 @TableField(fill=...) 自动判断
```

**报错**： `Column 'create_time' cannot be null`

### 坑 2：动态 SQL 标签必须包在 `<script>` 里

```java
// 错：MyBatis 看到 <set> <if> 不会当成标签处理，会当字面量发给 MySQL
@Update("""
    UPDATE todo
    <set>
      <if test="title != null">title = #{title},</if>
    </set>
    WHERE id = #{id}
    """)

// 对：用 <script> 包一层
@Update("""
    <script>
    UPDATE todo
    <set>
      <if test="title != null">title = #{title},</if>
    </set>
    WHERE id = #{id}
    </script>
    """)
```

**报错**： `You have an error in your SQL syntax near '<set>'`

> MP 没有这个问题，是因为它底层用的就是 MyBatis + 它自己帮你包好了 `<script>`

### 坑 3：`@Param` 必须给多参数方法加上

```java
// 错：多参数方法不写 @Param，MyBatis 找不到 #{id} 对应谁
Todo findById(Long id);     // ⚠ 编译能过，运行报 Parameter 'id' not found

// 对：
Todo findById(@Param("id") Long id);
```

> MP 版继承的 `BaseMapper.selectById` 已经标好了 `@Param`，你看不见

### 坑 4：`@Options(useGeneratedKeys=true)` 是 INSERT 后回填主键的关键

```java
@Insert("INSERT INTO todo (...) VALUES (...)")
@Options(useGeneratedKeys = true, keyProperty = "id")    // ← 没有这行，插入后 todo.id 仍是 null
int insert(Todo todo);
```

> MP 版 `@TableId(type = IdType.AUTO)` 已经隐含了这个开关，你看不见

---

## 4. 跑得通的最终验证

```bash
$ node scripts/api-test.mjs
...
通过 32 项，失败 0 项
```

| 接口 | MP 版 | 原生版 |
|---|---|---|
| GET `/api/todos?keyword=&done=` | ✅ | ✅ |
| GET `/api/todos/stats` | ✅ | ✅ |
| GET `/api/todos/{id}` | ✅ | ✅ |
| POST `/api/todos` | ✅ | ✅ |
| PUT `/api/todos/{id}` | ✅ | ✅ |
| PATCH `/api/todos/{id}/done` | ✅ | ✅ |
| DELETE `/api/todos/{id}` | ✅ | ✅ |

**结论**：两套实现最终行为**完全一致**。差别全在「MP 替你做了多少」。

---

## 5. 怎么选？两种方案的真实适用场景

| 维度 | MyBatis-Plus | 原生 MyBatis |
|---|---|---|
| 单表 CRUD 开发速度 | ⭐⭐⭐⭐⭐ 几行搞定 | ⭐⭐ 每张表都要自己写七八个方法 |
| 复杂 SQL（多表联查、动态条件多） | ⭐⭐⭐ Wrapper 能拼但有限 | ⭐⭐⭐⭐⭐ XML/注解完全自由 |
| 国内企业使用率 | **70%+**（招聘市场主流） | < 20%（多为老项目、外企） |
| 学习门槛 | ⭐⭐⭐⭐ 接口多但常用就那几个 | ⭐⭐⭐ 概念少但 SQL 啥都得懂 |
| 适合谁 | 业务型后端、CRUD 为主 | 复杂数据层、性能敏感、底层优化 |

**给唐的建议**：业务项目用 MP，先学会怎么"白嫖"它；想深入理解机制时切到原生分支看一遍，对比着读——这正是你这个仓库当前的状态。

---

## 6. 一句话总结

> **MyBatis-Plus = MyBatis + 一层「自动挡」封装**。
> 它没发明新技术，只是把单表 CRUD 这种重复活儿写成了「通用代码」，让你只专心写有「业务逻辑」的那部分。
> 就像汽车手动挡和自动挡的区别：原生 MyBatis 是手动挡（完全控制、累），MP 是自动挡（轻松、但偶尔你想换挡时它不让）。

---

## 附录：自己玩一遍的 3 条命令

```bash
# 1. 切到原生分支跑
cd C:\Users\12492\Desktop\studyProject\java\javaSpringBootDemo
git checkout feat/mybatis-pure
mvn spring-boot:run                  # 后端跑起来

# 2. 切回 MP 分支跑
git checkout main
mvn spring-boot:run

# 3. 两个分支并排对比（VS Code / IDEA 都能）
git checkout main
code .
# 另一边
git worktree add ../mybatis-pure feat/mybatis-pure
code ../mybatis-pure
```