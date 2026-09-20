# 从空架子到能跑的系统：Spring Boot 3 + MySQL 实操记录

> 项目路径：`C:\Users\12492\Desktop\studyProject\java\javaSpringBootDemo`
> 最终技术栈：JDK 21 · Spring Boot 3.5.16 · MyBatis-Plus 3.5.12 · MySQL 9.6 · Vue 3 + Vite · Maven
>
> 📌 本文档是**真实执行并验证通过**的记录。文末「实战踩坑实录」记下了过程中撞到的三个意外，
> 那几个坑比正文更值得看 —— 因为它们是你在自己的机器上真的会遇到的。

---

## 一、先回答你的问题：一个空架子，到底缺了什么？

你现在这个项目，其实是 IDEA 新建 Maven 项目时送的"毛坯房"：

```
javaSpringBootDemo/
├── pom.xml                          ← 只声明了"我是个 Java 项目"
├── .mvn/                            ← Maven Wrapper 目录（空的）
└── src/
    ├── main/java/org/example/
    │   └── Main.java                ← 一个 main 方法，打印 Hello World
    ├── main/resources/              ← 空的
    └── test/java/                   ← 空的
```

它有 Java 的身份，但没有"Web 服务"的能力。要变成一个能对外提供 HTTP 接口、能读写 MySQL 的后端，本质上是在做**三件加法**：

| 层面 | 现在缺什么 | 加什么进去 | 加完之后 |
|---|---|---|---|
| **依赖层** | 没有任何第三方库 | 在 `pom.xml` 里引入 Spring Boot 父工程 + Web + MyBatis-Plus + MySQL 驱动 | 项目具备了写 Web 接口和访问数据库的"能力" |
| **配置层** | 不知道连哪个数据库、跑在哪个端口 | `application.yml` | Spring Boot 启动时知道去哪连库、监听哪个端口 |
| **代码层** | 只有一个孤零零的 `Main.java` | 启动类 + Entity + Mapper + Service + Controller | 一个请求进来 → 查数据库 → 返回 JSON 的完整链路 |

再加一个**隐性前提**：依赖得能下载下来。国内直连 Maven 中央仓库极慢，所以还得先给 Maven 配个国内镜像。

这就是全貌。下面一步步来。

### 最终会长成这样

```
javaSpringBootDemo/
├── pom.xml                                  ← 【改】加上 Spring Boot 和依赖
├── docs/
│   └── 01-从空架子到SpringBoot系统.md        ← 本文档
└── src/
    ├── main/
    │   ├── java/org/example/
    │   │   ├── DemoApplication.java         ← 【新】启动类，取代 Main.java
    │   │   ├── common/
    │   │   │   └── Result.java              ← 【新】统一返回格式
    │   │   ├── config/
    │   │   │   └── MybatisPlusConfig.java   ← 【新】分页插件配置
    │   │   ├── controller/
    │   │   │   └── TodoController.java      ← 【新】接收 HTTP 请求
    │   │   ├── entity/
    │   │   │   └── Todo.java                ← 【新】对应数据库表
    │   │   ├── mapper/
    │   │   │   └── TodoMapper.java          ← 【新】操作数据库
    │   │   └── service/
    │   │       ├── TodoService.java         ← 【新】业务接口
    │   │       └── impl/
    │   │           └── TodoServiceImpl.java ← 【新】业务实现
    │   └── resources/
    │       ├── application.yml              ← 【新】配置文件
    │       └── sql/
    │           └── schema.sql               ← 【新】建库建表脚本
    └── test/java/                           ← 暂不动
```

---

## 二、分层架构：这五层分别在干嘛？

后端的核心思想叫"**分层**"——把不同职责的代码分开放，改一层不影响另一层。用餐厅来类比：

```
   浏览器 / Postman / 前端              ← 顾客
            │  HTTP 请求
            ▼
   ┌────────────────────┐
   │  Controller 控制层  │  @RestController
   │  前台服务员         │  只负责迎客、点单、上菜
   └────────┬───────────┘
            │  调用
            ▼
   ┌────────────────────┐
   │   Service 业务层    │  @Service
   │  厨师               │  处理"怎么做这道菜"的业务逻辑
   └────────┬───────────┘
            │  调用
            ▼
   ┌────────────────────┐
   │   Mapper 数据层     │  @Mapper
   │  仓库管理员         │  只管从库里取货 / 存货，不关心为什么
   └────────┬───────────┘
            │  SQL
            ▼
   ┌────────────────────┐
   │  MySQL 数据库       │  todo 表
   └────────────────────┘

   Entity 实体类 = 货架上的货物，各层之间传递的标准集装箱
```

举个具体例子。用户发来请求 `POST /api/todos`，body 是 `{"title":"学 Spring Boot"}`：

| 环节 | 谁在处理 | 做了什么 |
|---|---|---|
| 1 | Controller | 接住这个 HTTP 请求，把 JSON 转成 `Todo` 对象，校验 title 不能为空 |
| 2 | Service | 判断这个待办合不合法（比如标题重复了没有），决定要不要存 |
| 3 | Mapper | 生成 `INSERT INTO todo (...) VALUES (...)` 发给 MySQL |
| 4 | MySQL | 落盘，返回自增主键 |
| 5 | 原路返回 | 主键填回 `Todo` 对象 → Service 返回 → Controller 包装成 JSON 响应 |

**为什么要这么拆？** 因为如果某天业务规则变了（比如"每个用户最多 50 条待办"），你只改 Service 层，Controller 和 Mapper 一行都不用动。如果不分层，所有代码堆在一个类里，三个月后你自己都读不懂。

---

## 三、动手前的环境体检

我先把你这台机器的情况摸了一遍（避免写完代码才发现跑不起来）：

| 检查项 | 结果 | 说明 |
|---|---|---|
| JDK | **21.0.2** ✅ | 位于 `C:\dev\jdk-21.0.2`。Spring Boot 3.x 要求 JDK 17+，满足 |
| IDEA | **2026.2.3** ✅ | 已安装 |
| IDEA 内置 Maven | **有** ✅ | `plugins/maven-plugin/lib/maven3`，不用额外装 Maven |
| 命令行 mvn | 没有 ❌ | 不影响，IDEA 内置的就够用 |
| MySQL | **9.6.0**，端口 3306 已在监听 ✅ | 数据库服务已经是启动状态，省事 |
| 8080 端口 | **空闲** ✅ | Spring Boot 默认端口，没冲突 |
| 本地 Maven 仓库 | `C:\Users\12492\.m2\repository` 存在但**基本是空的** ❌ | 意味着首次构建要下载约 80~120MB 依赖 |
| Maven 镜像配置 | **`settings.xml` 不存在** ❌ | 走默认中央仓库，在国内会非常慢甚至超时 |

两个结论：

1. **必须先配国内镜像**，否则第一次构建可能卡几十分钟。
2. MySQL 已经是 9.6 这个比较新的版本，用 `mysql-connector-j` 9.x 驱动，它支持新版的 `caching_sha2_password` 认证方式，不会遇到老驱动那种 "Public Key Retrieval is not allowed" 的经典报错。

---

## 四、第 1 步：给 Maven 打通国内下载通道

### 为什么

Maven 要往本地仓库下载 jar 包。默认地址是美国的 `repo1.maven.org`，国内访问经常几十 KB/s。国内镜像站（阿里云）是它的完整拷贝，速度能差几十倍。

### 怎么做

Maven 读取配置的顺序是：`~/.m2/settings.xml` → Maven 安装目录下的 `conf/settings.xml`。优先用前者（用户级，不影响别人）。

你目前 `C:\Users\12492\.m2\` 下只有 `repository` 目录，没有 `settings.xml`，所以直接新建一个：

**文件位置**：`C:\Users\12492\.m2\settings.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">

  <!-- 本地仓库位置，默认就是这个，写出来更直观 -->
  <localRepository>C:\Users\12492\.m2\repository</localRepository>

  <mirrors>
    <!-- 阿里云公共仓库：代理所有仓库请求 -->
    <mirror>
      <id>aliyun-public</id>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>

  <profiles>
    <profile>
      <id>jdk-21</id>
      <activation>
        <activeByDefault>true</activeByDefault>
        <jdk>21</jdk>
      </activation>
      <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <maven.compiler.release>21</maven.compiler.release>
      </properties>
    </profile>
  </profiles>

</settings>
```

**`<mirrorOf>*</mirrorOf>` 是关键**：星号表示"所有仓库请求都走这个镜像"。

### 有个坑要提前说

Spring Boot 的父工程 `spring-boot-starter-parent` 会从 Maven 中央仓库拉，阿里云 `public` 仓库覆盖了它，没问题。但如果后面你引入了某些只在 `spring-milestones` 或 `jitpack` 上的库，`mirrorOf *` 会把它们也重定向到阿里云，导致 404。那时候的解法是把它改成 `<mirrorOf>*,!jitpack.io</mirrorOf>` 这种排除写法。入门阶段用 `*` 就行。

---

## 五、第 2 步：建库建表

### 为什么先建库

Spring Boot 启动时会去连数据库。如果库不存在，应用直接启动失败。所以顺序上，先有库，再启动应用。

### 目录约定

我在 `src/main/resources/sql/schema.sql` 下放建表脚本，而不是随手丢在桌面。好处：SQL 跟着代码走，换台电脑 checkout 下来就能重建环境。

**文件位置**：`src/main/resources/sql/schema.sql`

```sql
-- ============================================
-- 待办事项演示库
-- 数据库：demo_db   字符集：utf8mb4
-- ============================================

CREATE DATABASE IF NOT EXISTS demo_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE demo_db;

DROP TABLE IF EXISTS todo;

CREATE TABLE todo
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(200) NOT NULL COMMENT '待办标题',
    description VARCHAR(500) DEFAULT NULL COMMENT '详细描述',
    done        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否完成：0未完成 1已完成',
    deadline    DATETIME     DEFAULT NULL COMMENT '截止时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='待办事项表';

-- 初始化两条数据，方便一会儿验证查询接口
INSERT INTO todo (title, description, done, deadline)
VALUES ('跑通第一个 Spring Boot 接口', '用 IDEA 启动并访问 GET /api/todos', 0, '2026-09-21 18:00:00'),
       ('学会 MyBatis-Plus 单表 CRUD', '理解 BaseMapper 和 IService 的用法', 0, NULL);
```

### 逐行解释几个设计选择

| 写法 | 为什么这么写 |
|---|---|
| `utf8mb4` 而不是 `utf8` | MySQL 的 `utf8` 是残缺的，存不了 emoji。`utf8mb4` 才是完整的 UTF-8 |
| `BIGINT` 而不是 `BIGINT UNSIGNED` | Java 的 `Long` 是有符号的，用 UNSIGNED 在类型映射时容易出幺蛾子。自增主键用 `BIGINT` 足够 |
| `TINYINT` 而不是 `TINYINT(1)` | MySQL 8.0.17 起已废弃整数显示宽度，写 `(1)` 会报 deprecation 警告（MySQL 9.x 更严格） |
| `DEFAULT CURRENT_TIMESTAMP` | 让数据库负责填创建时间，Java 代码里就不用管了 |
| `ON UPDATE CURRENT_TIMESTAMP` | 更新时自动刷新 `update_time`，省掉手动维护 |
| `ENGINE = InnoDB` | MySQL 8+ 默认就是 InnoDB，写出来是显式声明。InnoDB 支持事务，MyISAM 不支持 |

### 执行方式

三种任选：

```bash
# 方式 1：命令行（注意 MySQL 9.6 装在桌面目录下，可能要写全路径）
mysql -uroot -p123456 < src/main/resources/sql/schema.sql

# 方式 2：在 IDEA 右侧 Database 面板里连上 MySQL，然后把 schema.sql 内容贴进去执行
# 方式 3：用 Navicat / DBeaver / MySQL Workbench 打开脚本执行
```

**验证**：

```sql
USE demo_db;
SELECT * FROM todo;
-- 应该看到 2 行数据
DESC todo;
-- 应该看到 7 个字段
```

---

## 六、第 3 步：改造 pom.xml（最关键的一步）

### 原来的 pom 长什么样

IDEA 生成的空 Maven 项目，`pom.xml` 大概是这样：

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.example</groupId>
  <artifactId>javaSpringBootDemo</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
  </properties>
</project>
```

它只说了一件事："我是个叫 javaSpringBootDemo 的 Java 项目，用 21 编译"。没有任何依赖。

### 三个关键增补

| 加的块 | 作用 | 不写会怎样 |
|---|---|---|
| `<parent>` | 继承 `spring-boot-starter-parent`，拿到几百个依赖的**版本管理** + 打包插件 + 默认配置 | 每个依赖你都得手写版本号，且版本之间可能冲突 |
| `<dependencies>` | 声明真正要用的库 | 没有 Web 容器、没有数据库驱动，写不了接口 |
| `<build><plugins>` | 引入 `spring-boot-maven-plugin` | 能用 IDEA 跑，但打不出可执行的 fat jar |

### 完整 pom.xml

**文件位置**：`pom.xml`（整体替换）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ① 继承 Spring Boot 父工程：版本管理的中枢 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <!-- ② 本项目坐标 -->
    <groupId>org.example</groupId>
    <artifactId>javaSpringBootDemo</artifactId>
    <version>1.0-SNAPSHOT</version>
    <name>javaSpringBootDemo</name>
    <description>Spring Boot 3 + MySQL 入门示例</description>

    <properties>
        <java.version>21</java.version>
        <!-- ⚠️ 这里刻意不用最新的 3.5.17，原因见下方「坑 3」 -->
        <mybatis-plus.version>3.5.12</mybatis-plus.version>
    </properties>

    <dependencies>
        <!-- Web 场景：内嵌 Tomcat + Spring MVC，让我们能写 @RestController -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MyBatis-Plus：注意必须用 spring-boot3 后缀的 starter -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MyBatis-Plus 3.5.9 起把 SQL 解析器拆成了独立模块，分页插件依赖它 -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL 驱动：版本交给 Spring Boot 统一管理，不写 version -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- 参数校验：@NotBlank / @NotNull 等注解 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok：用 @Data 自动生成 getter/setter/toString -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 开发期热重载：改完代码不用手动重启 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <!-- 测试：JUnit 5 + Mockito + Spring Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 让 mvn package 能打出可以直接 java -jar 运行的胖 jar -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <!-- Lombok 只在编译期用，不用打进最终 jar -->
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

### 依赖逐个说清楚

| 依赖 | 一句话解释 | 类比 |
|---|---|---|
| `spring-boot-starter-web` | Spring MVC + 内嵌 Tomcat + Jackson(JSON 转换) | 整条"接客流水线" |
| `mybatis-plus-spring-boot3-starter` | MyBatis 的增强版，单表增删改查不用写 SQL | 会自己写 SQL 的仓库管理员 |
| `mybatis-plus-jsqlparser` | SQL 解析器，分页插件靠它把 SQL 改写成 `LIMIT ?` | 管理员的翻译官 |
| `mysql-connector-j` | Java 和 MySQL 之间的"翻译官"，负责 JDBC 协议 | 对讲机 |
| `spring-boot-starter-validation` | 校验请求参数，`@NotBlank` 之类 | 门卫 |
| `lombok` | 编译期生成 getter/setter，少写几百行样板代码 | 代码生成器 |
| `spring-boot-devtools` | 改代码后自动重启应用，开发提速 | 免重启开关 |

### 两个版本上的坑

**坑 1：为什么用 `mybatis-plus-spring-boot3-starter` 而不是 `mybatis-plus-boot-starter`？**

老的那个是给 Spring Boot 2.x 用的。Spring Boot 3 换了 `jakarta.*` 命名空间（不再是 `javax.*`），老 starter 会报 `ClassNotFoundException: javax.servlet...`。名字里带 `boot3` 才是对的。

**坑 2：为什么额外引入 `mybatis-plus-jsqlparser`？**

MyBatis-Plus 从 3.5.9 开始，为了减小体积，把 SQL 解析器拆成了独立模块。如果你的代码里用了 `PaginationInnerInterceptor`（分页）却只引了 starter，启动时会报：

```
java.lang.ClassNotFoundException: net.sf.jsqlparser.parser.JSqlParserException
```

提前加上就没事。这也是本项目引入它的唯一原因。

---

## 七、第 4 步：写 application.yml

### 为什么用 yml 不用 properties

Spring Boot 两种都支持，但 yml 有层级结构，数据库配置一大坨的时候不会像 properties 那样全是重复前缀：

```properties
# properties 写法：前缀重复得让人崩溃
spring.datasource.url=...
spring.datasource.username=...
spring.datasource.password=...
```

```yaml
# yml 写法：同一个父节点只写一次
spring:
  datasource:
    url: ...
    username: ...
    password: ...
```

**文件位置**：`src/main/resources/application.yml`

```yaml
server:
  port: 8080                      # 应用监听端口，改这里就能换端口

spring:
  application:
    name: javaSpringBootDemo      # 应用名，日志和注册中心里会显示

  # ---------- 数据源：连哪个库 ----------
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: "jdbc:mysql://localhost:3306/demo_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    username: root
    password: "123456"

  # ---------- Jackson：JSON 序列化 ----------
  jackson:
    time-zone: Asia/Shanghai
    serialization:
      write-dates-as-timestamps: false   # 日期输出成 "2026-09-20T14:21:51" 而不是时间戳数字

# ---------- MyBatis-Plus ----------
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # create_time(库) -> createTime(Java) 自动映射
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 控制台打印真实执行的 SQL
  global-config:
    db-config:
      id-type: auto                      # 主键交给数据库自增
    banner: false                        # 关掉启动时那行 MyBatis-Plus 广告
  mapper-locations: classpath*:/mapper/**/*.xml   # 以后写 XML 版 SQL 放这里

# ---------- 日志 ----------
logging:
  level:
    root: info
    org.example: debug                   # 自己包的日志调成 debug，方便观察
```

### JDBC URL 上那串参数，每一个都有故事

| 参数 | 作用 | 不加会怎样 |
|---|---|---|
| `useUnicode=true&characterEncoding=utf8` | 告诉驱动用 UTF-8 处理中文 | 中文可能变问号 `???` |
| `serverTimezone=Asia/Shanghai` | 明确时区 | MySQL 8+ 驱动可能报 `The server time zone value '�й���׼ʱ��' is unrecognized`（中文时区名解析失败），而且时间会差 8 小时 |
| `useSSL=false` | 开发环境不走 SSL | 启动时刷一堆 `WARN: Establishing SSL connection without server's identity verification` |
| `allowPublicKeyRetrieval=true` | 允许驱动向服务端索要公钥 | MySQL 8+ 默认 `caching_sha2_password` 认证，第一次连接可能直接报 `Public Key Retrieval is not allowed` |

**注意**：URL 整个用双引号包起来了。因为里面有 `&` 和 `?`，虽然 YAML 里通常不会误解析，但加引号是最省心的写法，一劳永逸。

### 密码的安全提醒

现在密码是明文写在 `application.yml` 里的。本地练手没问题，但如果这个项目要提交到 Git，建议：

1. 把密码抽到环境变量：`password: ${DB_PASSWORD:123456}`，冒号后面是默认值
2. 或者创建 `application-dev.yml` 并加入 `.gitignore`

先记住这个意识，等真正部署时再处理。

---

## 八、第 5 步：写启动类

### 什么是"启动类"

Spring Boot 项目的入口。它做的事：启动 Spring 容器 → 扫描并创建所有 Bean → 启动内嵌 Tomcat → 监听 8080 端口。

**文件位置**：`src/main/java/org/example/DemoApplication.java`

```java
package org.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动类
 *
 * 注意：这个类必须放在所有代码的"最外层包"（org.example），
 * 因为 @SpringBootApplication 只会扫描它所在包及其子包。
 */
@SpringBootApplication
@MapperScan("org.example.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("启动成功 → http://localhost:8080/api/todos");
    }
}
```

### `@SpringBootApplication` 到底做了什么

它是一个"三合一"注解：

| 组成部分 | 作用 |
|---|---|
| `@SpringBootConfiguration` | 声明这是一个配置类 |
| `@EnableAutoConfiguration` | **核心**。Spring Boot 看你的类路径里有什么（比如发现 `spring-boot-starter-web`），就自动配好 Tomcat、DispatcherServlet、Jackson……这就是"约定优于配置" |
| `@ComponentScan` | 扫描当前包及子包下所有 `@Component` / `@Service` / `@Controller` / `@Mapper`，注册成 Bean |

### 三个容易踩的点

**1. 启动类必须在最外层包**

如果你的启动类放在 `org.example.app` 下，那 `org.example.controller` 就不会被扫描到，接口会 404。这是新手最常见的问题。本项目放在 `org.example` 这个根包下，`controller`、`service`、`mapper` 都是它的子包，全部能被扫描到。

**2. `@MapperScan` 是干什么的**

它批量告诉 MyBatis："`org.example.mapper` 包下的接口都是 Mapper，帮我生成实现类。"

替代方案是在每个 Mapper 接口上单独加 `@Mapper` 注解。区别：

| 方式 | 优点 | 缺点 |
|---|---|---|
| `@MapperScan` 放启动类 | 一处配置管全部，不会漏 | 新增 Mapper 包时要记得加路径 |
| 每个接口加 `@Mapper` | 见名知义，单文件自洽 | 文件多了重复 |

本项目用 `@MapperScan`，因为待会儿 Mapper 接口里不写 `@Mapper`，更干净。

**3. 原来的 `Main.java` 怎么办**

直接删掉。一个项目只能有一个入口，留着会让 IDEA 的"运行"按钮指向错误的类。

```bash
# 删除原有的 Main.java
rm src/main/java/org/example/Main.java
```

---

## 九、第 6 步：写五层代码（待办事项 CRUD）

现在开始写业务代码。顺序有讲究：从数据库往上写，`Entity → Mapper → Service → Controller`，这样每一步依赖的东西都已经存在了。

### 6.1 统一返回格式 Result

真实项目里，接口不会直接返回裸数据，而是包一层统一格式。前端拿到的永远是：

```json
{ "code": 200, "message": "success", "data": { ... } }
```

**文件位置**：`src/main/java/org/example/common/Result.java`

```java
package org.example.common;

import lombok.Data;

/**
 * 统一响应体
 * 所有接口都返回这个结构，前端只需判断 code 就行
 */
@Data
public class Result<T> {

    /** 业务状态码：200 成功，其他为失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 真正的数据 */
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }
}
```

**为什么这么做？** 如果接口 A 返回 `{id, title}`，接口 B 返回 `[{...}]`，接口 C 出错返回 `{"error":"xxx"}`，前端得为每个接口写一套解析逻辑。统一包装后，前端只要写一个拦截器就能处理所有接口。

### 6.2 实体类 Todo

**文件位置**：`src/main/java/org/example/entity/Todo.java`

```java
package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办事项实体
 * 对应数据库表 todo
 */
@Data
@TableName("todo")
public class Todo {

    /** 主键，IdType.AUTO 表示由数据库自增生成 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题，必填 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 个字符")
    private String title;

    /** 描述，选填 */
    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;

    /** 是否完成 */
    private Boolean done;

    /** 截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 创建时间，由数据库默认值填充，Java 侧只读 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间，数据库 ON UPDATE 自动维护 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
```

三个注解的含义：

| 注解 | 作用 |
|---|---|
| `@Data` | Lombok：自动生成 getter/setter/equals/hashCode/toString |
| `@TableName("todo")` | 告诉 MyBatis-Plus 这个类对应哪张表。类名 `Todo` 和表名 `todo` 大小写不同，不加可能匹配不上 |
| `@TableId(type = IdType.AUTO)` | 声明主键，并指定"自增"策略 |
| `@NotBlank` / `@Size` | 参数校验，配合 Controller 上的 `@Valid` 生效 |
| `@JsonFormat` | 控制 JSON 输出的时间格式。**注意**：`spring.jackson.date-format` 只管老的 `java.util.Date`，对 `LocalDateTime` 无效，必须用这个注解 |

**关于字段名和列名的映射**：Java 用驼峰 `createTime`，数据库用下划线 `create_time`。因为 `application.yml` 里开了 `map-underscore-to-camel-case: true`，两者自动对应，不用额外写 `@TableField`。

**关于 `createTime` 为什么不手动赋值**：MyBatis-Plus 的 `insert` 默认策略是 `NOT_NULL`——字段为 `null` 时不会拼进 SQL。所以 `createTime` 保持 `null`，SQL 里就不含这一列，数据库的 `DEFAULT CURRENT_TIMESTAMP` 自动生效。这是最省事的做法。

### 6.3 Mapper 接口

这是 MyBatis-Plus 最"魔法"的地方——**一个方法都不写，就有全套增删改查**。

**文件位置**：`src/main/java/org/example/mapper/TodoMapper.java`

```java
package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.entity.Todo;

/**
 * 待办事项 Mapper
 *
 * 继承 BaseMapper<Todo> 后自动获得：
 *   insert / deleteById / updateById / selectById / selectList / selectPage ...
 * 不用写任何 SQL，也不用写 XML
 */
public interface TodoMapper extends BaseMapper<Todo> {
}
```

**为什么不用 `@Mapper` 注解？** 因为启动类上已经写了 `@MapperScan("org.example.mapper")`，这里就省了。两种方式选一种即可，不要重复。

**`BaseMapper<T>` 白送的方法清单**（部分）：

| 方法 | 对应 SQL |
|---|---|
| `insert(entity)` | `INSERT INTO todo ...` |
| `deleteById(id)` | `DELETE FROM todo WHERE id = ?` |
| `updateById(entity)` | `UPDATE todo SET ... WHERE id = ?`（null 字段跳过） |
| `selectById(id)` | `SELECT * FROM todo WHERE id = ?` |
| `selectList(wrapper)` | `SELECT * FROM todo WHERE ...` |
| `selectPage(page, wrapper)` | `SELECT * FROM todo WHERE ... LIMIT ?,?` |
| `selectCount(wrapper)` | `SELECT COUNT(*) FROM todo WHERE ...` |

### 6.4 Service 接口 + 实现

**为什么要 Service 层？** 因为 Controller 不该写业务逻辑。如果只是纯转发，确实显得多余，但只要出现"下单前要检查库存、扣减余额、发送通知"这种多步骤逻辑，就必须有地方放。

MyBatis-Plus 同样提供了 `IService` / `ServiceImpl`，把 Mapper 的方法又包装了一层（多了批量操作等）。

**文件位置**：`src/main/java/org/example/service/TodoService.java`

```java
package org.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.entity.Todo;

/**
 * 待办事项业务接口
 * 继承 IService<Todo>，获得 save / removeById / updateById / getById / list 等常用方法
 */
public interface TodoService extends IService<Todo> {
}
```

**文件位置**：`src/main/java/org/example/service/impl/TodoServiceImpl.java`

```java
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
 *   第一个 = 用哪个 Mapper
 *   第二个 = 操作哪个实体
 */
@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, Todo> implements TodoService {
}
```

**注意包名**：接口在 `org.example.service`，实现在 `org.example.service.impl`。这是 Spring 社区的惯例，不是强制要求，但大家默认这么放。

**为什么要拆接口和实现？** 两个理由：一是 Spring AOP（事务、日志）默认基于 JDK 动态代理，需要接口；二是以后换实现类（比如从 MySQL 换成 MongoDB）时，上层代码不用改。

### 6.5 Controller

**文件位置**：`src/main/java/org/example/controller/TodoController.java`

```java
package org.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.example.common.Result;
import org.example.entity.Todo;
import org.example.service.TodoService;
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

import java.util.List;

/**
 * 待办事项接口
 * 所有接口以 /api/todos 开头
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    /**
     * 构造器注入：Spring 启动时会自动把 TodoServiceImpl 传进来
     * 比 @Autowired 字段注入更好，因为字段可以是 final，且便于单元测试
     */
    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 1. 查询列表，支持按完成状态过滤
     * GET /api/todos          查全部
     * GET /api/todos?done=false  只查未完成
     */
    @GetMapping
    public Result<List<Todo>> list(@RequestParam(required = false) Boolean done) {
        LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<>();
        if (done != null) {
            wrapper.eq(Todo::getDone, done);   // 用方法引用，字段名写错会在编译期报错
        }
        wrapper.orderByDesc(Todo::getId);       // 最新的排前面
        return Result.ok(todoService.list(wrapper));
    }

    /** 2. 按 id 查询详情  GET /api/todos/1 */
    @GetMapping("/{id}")
    public Result<Todo> getById(@PathVariable Long id) {
        Todo todo = todoService.getById(id);
        return todo == null ? Result.fail("待办不存在") : Result.ok(todo);
    }

    /** 3. 新增  POST /api/todos  body: {"title":"xxx"} */
    @PostMapping
    public Result<Todo> create(@RequestBody @Valid Todo todo) {
        todo.setId(null);             // 防止前端传了 id 变成"更新"
        todo.setDone(false);          // 新建的一律未完成
        todoService.save(todo);       // 保存后 id 会自动回填到 todo 对象
        return Result.ok(todo);
    }

    /** 4. 修改  PUT /api/todos/1  body: {"title":"新标题"} */
    @PutMapping("/{id}")
    public Result<Todo> update(@PathVariable Long id, @RequestBody Todo todo) {
        if (todoService.getById(id) == null) {
            return Result.fail("待办不存在");
        }
        todo.setId(id);
        todoService.updateById(todo);   // 只更新非 null 字段
        return Result.ok(todoService.getById(id));
    }

    /** 5. 切换完成状态  PATCH /api/todos/1/done */
    @PatchMapping("/{id}/done")
    public Result<Todo> toggleDone(@PathVariable Long id) {
        Todo todo = todoService.getById(id);
        if (todo == null) {
            return Result.fail("待办不存在");
        }
        todo.setDone(!Boolean.TRUE.equals(todo.getDone()));
        todoService.updateById(todo);
        return Result.ok(todo);
    }

    /** 6. 删除  DELETE /api/todos/1 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean removed = todoService.removeById(id);
        return removed ? Result.ok(null) : Result.fail("待办不存在");
    }
}
```

### 请求方式为什么这么分（RESTful 规范）

| 操作 | HTTP 方法 | URL | 幂等性 |
|---|---|---|---|
| 查列表 | `GET` | `/api/todos` | 幂等，查一百次结果一样 |
| 查详情 | `GET` | `/api/todos/{id}` | 幂等 |
| 新增 | `POST` | `/api/todos` | 非幂等，调两次会建两条 |
| 全量修改 | `PUT` | `/api/todos/{id}` | 幂等 |
| 部分修改 | `PATCH` | `/api/todos/{id}/done` | 幂等 |
| 删除 | `DELETE` | `/api/todos/{id}` | 幂等 |

**用 URL 名词 + HTTP 动词表达操作，而不是 `/getTodoList`、`/addTodo` 这种**——这是 REST 的核心思路，好处是同一个资源的不同操作有统一的结构。

### 几个注解速查

| 注解 | 作用 |
|---|---|
| `@RestController` | = `@Controller` + `@ResponseBody`，返回值自动转成 JSON |
| `@RequestMapping("/api/todos")` | 类级别路径前缀 |
| `@GetMapping` / `@PostMapping` 等 | 方法级别路径 + HTTP 方法 |
| `@PathVariable` | 取 URL 路径里的变量，如 `/api/todos/**1**` |
| `@RequestParam` | 取 URL 问号后面的参数，如 `?done=false` |
| `@RequestBody` | 把请求体 JSON 转成 Java 对象 |
| `@Valid` | 触发实体上的校验注解 |

### 6.6 分页插件配置（可选，但建议加）

MyBatis-Plus 的分页功能需要显式注册拦截器，否则 `selectPage` 不会真的分页（会把全部数据查出来再内存分页）。

**文件位置**：`src/main/java/org/example/config/MybatisPlusConfig.java`

```java
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
     * 它的工作原理是拦截 SQL，自动拼接 LIMIT 语句
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

有了它，以后想写分页接口就是：

```java
Page<Todo> page = new Page<>(1, 10);           // 第 1 页，每页 10 条
todoService.page(page, new LambdaQueryWrapper<Todo>().orderByDesc(Todo::getId));
```

---

## 十、第 7 步：在 IDEA 里跑起来

### 7.1 让 IDEA 认识新依赖

改完 `pom.xml` 后，IDEA 通常会在右上角弹一个小图标提示 "Maven changes detected"。点它，或者：

- 右侧边栏 → `Maven` 面板 → 点左上角的"刷新"图标（🔄）
- 快捷键 `Ctrl + Shift + O`（旧版本可能不同）

首次会下载约 80~120MB 依赖。配了阿里云镜像的话，通常 1~3 分钟。

**怎么判断下载完了？** Maven 面板下方进度条消失，且 `src/main/java` 下的代码不再有红色报错。

### 7.2 ⚠️ 检查 Lombok 设置（这步不做会报错）

**这是最容易踩的坑。** Lombok 靠"注解处理器"在编译期生成 getter/setter。如果 IDEA 没开这个开关，你会看到满屏的 `Cannot resolve method 'getTitle'`，但代码其实没错。

检查路径：

```
File → Settings → 构建、执行、部署 → 编译器 → 注解处理器
  ✅ 勾选「启用注解处理」(Enable annotation processing)
```

IDEA 2020.3 以后的版本内置了 Lombok 插件，一般不需要额外装。如果 Settings → Plugins 里搜不到 Lombok，说明版本太老，需要手动安装。

### 7.3 启动

打开 `DemoApplication.java`，点击类名左边或 `main` 方法左边的绿色三角 ▶，选 `Run 'DemoApplication'`。

### 7.4 看日志判断是否成功

**成功的标志**——控制台出现这几行：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.5.16)

2026-09-20 14:30:12.345  INFO --- [main] org.example.DemoApplication : Starting DemoApplication...
2026-09-20 14:30:13.120  INFO --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat initialized with port 8080 (http)
2026-09-20 14:30:14.567  INFO --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8080 (http)
2026-09-20 14:30:14.570  INFO --- [main] org.example.DemoApplication : Started DemoApplication in 2.83 seconds
启动成功 → http://localhost:8080/api/todos
```

**看到 `Tomcat started on port 8080` 和 `Started DemoApplication` 就是成功了。**

常见的失败日志对照：

| 日志关键字 | 原因 | 解决 |
|---|---|---|
| `Communications link failure` | MySQL 没启动或端口不对 | 确认 3306 在监听 |
| `Access denied for user 'root'@'localhost'` | 密码错 | 检查 `application.yml` 里的 password |
| `Unknown database 'demo_db'` | 库没建 | 执行 `schema.sql` |
| `Port 8080 was already in use` | 端口被占 | 改 `server.port`，或杀掉占用进程 |
| `ClassNotFoundException: javax.servlet.*` | MyBatis-Plus starter 用错了 | 必须用 `mybatis-plus-spring-boot3-starter` |
| `Cannot resolve method 'getTitle'` | Lombok 注解处理没开 | 见 7.2 |

---

## 十一、第 8 步：验证接口

启动成功后，用 curl 或浏览器逐个验证。

### 8.1 查列表

```bash
curl http://localhost:8080/api/todos
```

预期输出（`data` 里是前面 `schema.sql` 插入的两条种子数据）：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 2,
      "title": "学会 MyBatis-Plus 单表 CRUD",
      "description": "理解 BaseMapper 和 IService 的用法",
      "done": false,
      "deadline": null,
      "createTime": "2026-09-20 14:20:00",
      "updateTime": "2026-09-20 14:20:00"
    },
    {
      "id": 1,
      "title": "跑通第一个 Spring Boot 接口",
      "description": "用 IDEA 启动并访问 GET /api/todos",
      "done": false,
      "deadline": "2026-09-21 18:00:00",
      "createTime": "2026-09-20 14:20:00",
      "updateTime": "2026-09-20 14:20:00"
    }
  ]
}
```

### 8.2 新增

```bash
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"体验一下 Spring Boot","description":"原来这么少代码就能起服务"}'
```

预期：返回的 `data.id` 是 3（自增主键回填）。

**同时观察 IDEA 控制台**——因为配了 `log-impl: StdOutImpl`，会打印真实执行的 SQL：

```
==>  Preparing: INSERT INTO todo ( title, description, done ) VALUES ( ?, ?, ? )
==> Parameters: 体验一下 Spring Boot(String), 原来这么少代码就能起服务(String), false(Boolean)
<==    Updates: 1
```

**这个日志非常有用**：以后写复杂查询，先看它生成的 SQL 对不对，能省掉大量猜测。

### 8.3 修改

```bash
curl -X PUT http://localhost:8080/api/todos/3 \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring Boot 真香","description":"已经跑通了"}'
```

注意：`updateById` 只更新非 null 字段。所以这次请求里没传 `done`，数据库里的 `done` 保持不变。这是 MyBatis-Plus 的默认行为，想改成"null 也更新"需要给字段加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。

### 8.4 切换完成状态

```bash
curl -X PATCH http://localhost:8080/api/todos/3/done
```

再调一次，`done` 又会变回 `false`。

### 8.5 删除

```bash
curl -X DELETE http://localhost:8080/api/todos/3
```

### 8.6 验证参数校验

故意不传 title：

```bash
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"description":"没有标题"}'
```

会返回 Spring 默认的 400 错误。**这个体验不好**——因为项目还没加"全局异常处理器"，校验失败的信息格式和 `Result` 不统一。想优化的话见下面「进阶方向」第 1 条。

### 8.7 验证数据库真的写进去了

别只信接口返回，去数据库确认一下：

```sql
USE demo_db;
SELECT * FROM todo ORDER BY id;
```

看到刚才新增、修改的记录，说明**整条链路 Controller → Service → Mapper → MySQL 全通了**。

---

## 十二、完整数据流回顾

以 `POST /api/todos {"title":"测试"}` 为例，走一遍完整链路：

```
1. 浏览器发 POST http://localhost:8080/api/todos
   Body: {"title":"测试"}
            │
            ▼
2. Tomcat 接收到请求，交给 DispatcherServlet
            │
            ▼
3. DispatcherServlet 根据 URL + 方法，匹配到 TodoController.create()
            │
            ▼
4. Jackson 把 JSON 反序列化成 Todo 对象
   @Valid 触发校验：title 不为空 ✓
            │
            ▼
5. TodoController 调用 todoService.save(todo)
            │
            ▼
6. TodoServiceImpl (继承自 ServiceImpl) 调用 baseMapper.insert(todo)
            │
            ▼
7. TodoMapper (MyBatis 动态代理生成的实现类) 拼出 SQL
   INSERT INTO todo (title, done) VALUES (?, ?)
   参数：测试, false
            │
            ▼
8. mysql-connector-j 通过 JDBC 协议发给 MySQL
            │
            ▼
9. MySQL 执行，主键 id=4 写入 todo 表
            │
            ▼
10. 主键回填到 todo 对象（id 从 null 变成 4）
            │
            ▼
11. 原路返回 → Controller 包装成 Result.ok(todo)
            │
            ▼
12. Jackson 序列化成 JSON，响应给浏览器
    {"code":200,"message":"success","data":{"id":4,...}}
```

**理解这条链路，就理解了 Spring Boot 后端的一切。** 后面所有复杂功能，都只是在这条链路上加东西。

---

## 十三、踩坑清单（速查表）

| 现象 | 原因 | 解决 |
|---|---|---|
| 改完 pom 依赖一直下载不下来 | 走的是国外中央仓库 | 配 `~/.m2/settings.xml` 阿里云镜像 |
| Maven 面板报 `Could not resolve dependencies` | 网络或镜像问题 | 检查镜像 URL；试 `mvn -U clean install` 强制更新 |
| `Cannot resolve method 'getTitle'` | Lombok 注解处理器没开 | Settings → 编译器 → 注解处理器 → 勾选启用 |
| 启动报 `Public Key Retrieval is not allowed` | MySQL 8+ 的 `caching_sha2_password` 认证 | JDBC URL 加 `allowPublicKeyRetrieval=true` |
| 启动报时区无法识别 / 时间差 8 小时 | 没指定 `serverTimezone` | JDBC URL 加 `serverTimezone=Asia/Shanghai` |
| `ClassNotFoundException: javax.servlet.*` | 用了 Spring Boot 2 的 starter | 换成 `mybatis-plus-spring-boot3-starter` |
| 分页报 `ClassNotFoundException: JSqlParserException` | 3.5.9+ 拆分了模块 | 加 `mybatis-plus-jsqlparser` 依赖 |
| 接口全部 404 | 启动类不在最外层包 | 启动类放到 `org.example` 根包下 |
| Mapper 注入失败 `No qualifying bean` | 没扫描到 Mapper | 启动类加 `@MapperScan("org.example.mapper")` |
| 中文变 `???` | 字符集问题 | 库表用 `utf8mb4`，URL 加 `characterEncoding=utf8` |
| `Port 8080 was already in use` | 端口冲突 | 改 `server.port`，或找到占用进程结束它 |
| `ddl-auto` 相关报错 | JPA 的配置写到了 MyBatis-Plus 项目里 | 本项目用 MyBatis-Plus，不配 `spring.jpa.*` |

---

## 十四、下一步可以玩什么

按难度排序，建议一个个来：

1. **全局异常处理器**（简单，收益大）
   加 `@RestControllerAdvice`，统一捕获校验失败、404、500，让所有错误也返回 `Result` 格式。

2. **分页查询接口**（简单）
   用上第 6.6 节配好的分页插件，写 `GET /api/todos/page?pageNum=1&pageSize=10`。

3. **条件搜索 + 模糊查询**（简单）
   在 `LambdaQueryWrapper` 里加 `.like(StringUtils.hasText(keyword), Todo::getTitle, keyword)`。

4. **Swagger / Knife4j 接口文档**（中等）
   自动生成可视化接口文档，不用再手写 curl 命令。

5. **事务控制**（中等）
   在 Service 方法上加 `@Transactional`，体验一下回滚。

6. **跨域配置**（中等）
   前端 Vue 项目要连这个后端时，需要配 CORS。

7. **多表关联**（较难）
   加一个 `user` 表，`todo.user_id` 关联它，学 MyBatis-Plus 的连表查询。

8. **接入你自己的 Vue 前端**（较难）
   把这套接口接到已有的前端项目上，跑通前后端分离。

---

## 附录：本文档涉及的文件清单

| # | 文件 | 状态 | 作用 |
|---|---|---|---|
| 1 | `~/.m2/settings.xml` | 新建 | Maven 阿里云镜像配置 |
| 2 | `pom.xml` | 替换 | 引入 Spring Boot 和全部依赖 |
| 3 | `src/main/resources/application.yml` | 新建 | 数据源、MyBatis-Plus、日志配置 |
| 4 | `src/main/resources/sql/schema.sql` | 新建 | 建库建表 + 种子数据 |
| 5 | `src/main/java/org/example/Main.java` | **删除** | 旧的入口类 |
| 6 | `src/main/java/org/example/DemoApplication.java` | 新建 | Spring Boot 启动类 |
| 7 | `src/main/java/org/example/common/Result.java` | 新建 | 统一响应体 |
| 8 | `src/main/java/org/example/config/MybatisPlusConfig.java` | 新建 | 分页插件注册 |
| 9 | `src/main/java/org/example/entity/Todo.java` | 新建 | 实体类 |
| 10 | `src/main/java/org/example/mapper/TodoMapper.java` | 新建 | 数据访问接口 |
| 11 | `src/main/java/org/example/service/TodoService.java` | 新建 | 业务接口 |
| 12 | `src/main/java/org/example/service/impl/TodoServiceImpl.java` | 新建 | 业务实现 |
| 13 | `src/main/java/org/example/controller/TodoController.java` | 新建 | REST 接口 |

**版本记录**

| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | 21.0.2 | 本机 `C:\dev\jdk-21.0.2` |
| Spring Boot | 3.5.16 | 3.5.x 最新稳定版 |
| MyBatis-Plus | 3.5.12 | 需用 `-spring-boot3-` starter；3.5.13+ 包路径有变，见「坑 3」 |
| MySQL | 9.6.0 | 服务已在 3306 监听 |
| mysql-connector-j | 由 Spring Boot BOM 管理 | 9.x |
| IDEA | 2026.2.3 | 内置 Maven |

---

## 十五、实战踩坑实录

第十三节是「预防性清单」，这一节是**真实撞到的**。现象、排查思路、最终修法都在这里。

### 坑 1 · 命令行里 Maven 跑不起来

**现象**

在 Git Bash 里执行 IDEA 自带的 Maven，直接报：

```
错误: 找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher
原因: java.lang.ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher
```

**原因**

IDEA 自带的 Maven 有两个入口：`bin/mvn`（shell 脚本，给 Linux/macOS）和 `bin/mvn.cmd`（批处理，给 Windows）。

在 Git Bash 下执行 `mvn` 时，脚本内部用的是 `/c/Program Files/...` 这种 MSYS 风格路径，
传给 Windows 版的 `java.exe` 它不认识，于是找不到 classworlds 的 jar。

**解决**：用 `mvn.cmd`。

```bash
export JAVA_HOME="C:/dev/jdk-21.0.2"
MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.3/plugins/maven-plugin/lib/maven3/bin/mvn.cmd"
MSYS2_ARG_CONV_EXCL="*" "$MVN" -B clean compile
```

**对你的影响**：几乎没有。你直接在 IDEA 里点绿三角就行，IDEA 会用正确的方式调用 Maven。

---

### 坑 2 · 端口莫名其妙变成 55768

**现象**

`application.yml` 里明明写着 `port: 8080`，启动日志却是：

```
Tomcat initialized with port 55768 (http)
...
APPLICATION FAILED TO START
Description: Web server failed to start. Port 55768 was already in use.
```

**原因**

运行环境里存在环境变量 `SERVER__PORT=55768`。

Spring Boot 的配置优先级是 **环境变量 > application.yml**，而它做「宽松绑定」时会把
`SERVER__PORT` 解析成 `server.port`（双下划线等价于一个点）。配置文件就这样被静默覆盖了。

这种问题最阴险的地方是：你的代码一个字都没错。

**解决**

- 临时：启动前 `unset SERVER__PORT`
- 永久：用优先级更高的方式覆盖，比如 IDEA 运行配置里加 VM 参数 `-Dserver.port=8080`（系统属性 > 环境变量）

**排查手法**：遇到「配置不生效」，先别改代码，执行 `env | grep -i port` 看看是不是被环境变量劫持了。

---

### 坑 3 · MyBatis-Plus 用最新版反而编译不过 ★

**现象**

把 MyBatis-Plus 设为当时最新的 `3.5.17`，编译直接失败：

```
[ERROR] 程序包 com.baomidou.mybatisplus.extension.service 不存在
[ERROR] 找不到符号  符号: 类 IService
[ERROR] 程序包 com.baomidou.mybatisplus.extension.service.impl 不存在
[ERROR] 找不到符号  符号: 类 ServiceImpl
```

**排查过程**

先按常规思路怀疑依赖没下全，去本地仓库翻 jar：

```bash
ls ~/.m2/repository/com/baomidou/mybatis-plus-extension/3.5.17/
# jar 在，176KB，完整的，不是下载问题

# 那 IService 到底躺在哪个 jar 里？
for j in $(find ~/.m2/repository/com/baomidou -name "*.jar" -not -name "*sources*"); do
  if unzip -l "$j" | grep -q "IService.class"; then echo "★ $j"; fi
done
```

答案出来了，包路径是 `com/baomidou/mybatisplus/spring/service/IService.class`。

而网上教程、你搜到的所有示例，import 写的都是 `com.baomidou.mybatisplus.extension.service.IService`。

**结论**

MyBatis-Plus 在 3.5.13 之后做了一次包路径重构：

| 类 | 3.5.12 及更早 | 3.5.17 |
|---|---|---|
| `IService` | `com.baomidou.mybatisplus.extension.service.IService` | `com.baomidou.mybatisplus.spring.service.IService` |
| `ServiceImpl` | `...extension.service.impl.ServiceImpl` | `...spring.service.impl.ServiceImpl` |

**解决**：学习阶段用 `3.5.12`，包路径与主流资料一致，搜到的代码能直接粘贴。

**顺带学个技巧**：想知道某个类在哪个 jar 里，别去搜文档，直接把 jar 拉下来看结构，比任何文档都快。

```bash
curl -o mp.jar https://maven.aliyun.com/repository/public/com/baomidou/mybatis-plus-spring/3.5.12/mybatis-plus-spring-3.5.12.jar
unzip -l mp.jar | grep IService
```

---

### 坑 4 · 新增接口返回的 createTime 是 null ★

**现象**

新增一条待办，接口返回：

```json
{"code":200,"data":{"id":5,"title":"...","createTime":null,"updateTime":null}}
```

但马上去查数据库，`create_time` 明明是有的。前端列表因此显示「创建于 null」。

**原因**

设计上 `createTime` 是留给数据库填的（`DEFAULT CURRENT_TIMESTAMP`），Java 对象里始终是 null。

MyBatis-Plus 的 `insert` **只回填自增主键**，不会把数据库用默认值填好的其他字段读回来。
所以 `save(todo)` 之后，`todo` 对象里只有 `id` 有值。

**解决**：新增之后补一次查询。

```java
@PostMapping
public Result<Todo> create(@RequestBody @Valid Todo todo) {
    todo.setId(null);
    todo.setDone(false);
    todo.setCreateTime(null);
    todo.setUpdateTime(null);

    todoService.save(todo);

    // 多查这一次，是为了把数据库填的 createTime / updateTime 带回来
    return Result.ok(todoService.getById(todo.getId()));
}
```

**延伸**：凡是用数据库默认值的字段（`status`、`version`、`sort_order`……）都有这个问题。
只要值不是 Java 侧赋的，`save` 之后就得重新查一次。

---

### 坑 5 · curl 发中文 JSON 报 UTF-8 错误

**现象**

```bash
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"接口验证测试"}'
```

返回：

```json
{"code":500,"message":"服务器异常：JSON parse error: Invalid UTF-8 start byte 0xbd"}
```

**原因**

不是后端的问题。Windows 命令行默认 GBK 编码，中文在发出去之前就被转成 GBK 字节了，
服务端按 UTF-8 解析，于是报「非法的 UTF-8 起始字节」。

**解决**，三种任选：

1. **写进文件再发**（文件是 UTF-8，最省事）
   ```bash
   curl -X POST http://localhost:8080/api/todos \
     -H "Content-Type: application/json" \
     --data-binary @body.json
   ```

2. **用脚本发**（本项目用的就是这个，写成了 32 项断言的回归测试）
   ```javascript
   // Node 的 fetch 不存在编码问题
   fetch('http://localhost:8080/api/todos', {
     method: 'POST',
     headers: { 'Content-Type': 'application/json; charset=utf-8' },
     body: JSON.stringify({ title: '中文标题' })
   })
   ```

3. **改用纯英文测试数据**（能验证逻辑，验证不了编码）

**提示**：Postman / Apifox 不会有这个问题。别因为 curl 报错就怀疑后端写错了。

---

### 小贴士 · MySQL 的 DATETIME 只精确到秒

写自动化测试时撞到的：新增之后立刻修改，断言 `updateTime` 应该变化，结果两次时间戳一模一样。

原因是 `DATETIME` 默认精度到**秒**。同一秒内的两次写操作，时间戳看起来「没变」。

需要毫秒精度的话，建表时改成：

```sql
create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
update_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
```

`DATETIME(3)` 是毫秒，`DATETIME(6)` 是微秒。默认的 `DATETIME` 等于 `DATETIME(0)`。

---

### 关于前端

前端（Vue 3 + Vite）的搭建、目录结构、代理配置和联调说明，见同目录：

**`docs/02-Vue前端搭建与联调.md`**

