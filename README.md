# javaSpringBootDemo

一个前后端分离的**待办事项（Todo）全栈示例**，适合 Spring Boot + Vue 入门练习。

后端走 Spring Boot 3 + MyBatis-Plus + MySQL，前端用 Vue 3 + Vite + 原生 fetch。

> GitHub: https://github.com/Dawnn15/javaSpringBootDemo

## ⚡ 仓库里有两套数据层实现（对照学习）

| 分支 | 数据层 | 适合谁 |
|---|---|---|
| **`main`** ← 你在这里 | MyBatis-Plus 3.5.12 | 业务项目首选，CRUD 不用写 SQL |
| **`feat/mybatis-pure`** | 原生 MyBatis 3.0.5 | 想看清「MP 到底替你干了什么」 |

切换看不同实现：
```bash
git checkout main              # 当前：MP 版
git checkout feat/mybatis-pure # 对照：原生 MyBatis 版
```

详细对照在 [`docs/04-MyBatis改造过程与MyBatis-Plus对照.md`](./docs/04-MyBatis改造过程与MyBatis-Plus对照.md)

---

## 一、技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Spring Boot | 3.5.16 |
| 数据层 | MyBatis-Plus | 3.5.12 |
| 数据库 | MySQL | 8.0+ / 9.x |
| 构建工具 | Maven | 3.9+ |
| 前端 | Vue 3 + Vite | 3.5.x / 8.3.x |
| JDK | — | 21 |

---

## 二、前置条件

本地必须安装好以下环境：

| 软件 | 推荐版本 | 备注 |
|---|---|---|
| JDK | 21 | Spring Boot 3.x 要求 17+，这里用 21 |
| MySQL | 8.0+ 或 9.x | 需要 root 密码为 `123456`，或用环境变量覆盖 |
| Node.js | 18+ | 用于运行前端，本项目在 26.x 验证通过 |
| Maven | 3.9+ | IDEA 自带 Maven 也可以 |
| IDEA | 2024+ | 推荐，用来写 Java 和点绿三角启动 |

---

## 三、快速开始

### 1. 克隆仓库

```bash
git clone git@github.com:Dawnn15/javaSpringBootDemo.git
cd javaSpringBootDemo
```

如果你没配 SSH，也可以用 HTTPS：

```bash
git clone https://github.com/Dawnn15/javaSpringBootDemo.git
```

### 2. 创建数据库

用 MySQL 客户端执行项目里的建表脚本：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 < src/main/resources/sql/schema.sql
```

脚本会创建 `demo_db` 库和 `todo` 表，并插入 4 条示例数据。

> 如果你的 root 密码不是 `123456`，请修改 `src/main/resources/application.yml` 里的 `spring.datasource.password`。

### 3. 启动后端

**方式 A：用 IDEA（推荐新手）**

1. IDEA 打开项目根目录 `javaSpringBootDemo`
2. 等项目右下角 Maven 依赖下载完成（首次约 80~120MB）
3. 找到 `src/main/java/org/example/DemoApplication.java`
4. 点类名左侧的绿色三角形 → **Run 'DemoApplication'**
5. 控制台出现 `Tomcat started on port(s): 8080` 即成功

**方式 B：命令行**

```bash
# 进入项目根目录
mvn -B spring-boot:run
```

如果本地没有全局 `mvn`，用 IDEA 自带的 Maven：

```bash
"C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" -B spring-boot:run
```

后端启动后验证：

```bash
curl http://localhost:8080/api/todos
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173 即可看到界面。

### 5. 一起验证

| 地址 | 说明 |
|---|---|
| http://localhost:8080/api/todos | 后端列表接口 |
| http://localhost:8080/api/todos/stats | 后端统计接口 |
| http://localhost:5173 | 前端页面 |

---

## 四、常用后端接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/todos` | 列表，支持 `?keyword=xxx` 搜索 |
| GET | `/api/todos/{id}` | 详情 |
| GET | `/api/todos/stats` | 统计待办/已完成数量 |
| POST | `/api/todos` | 新增 |
| PUT | `/api/todos/{id}` | 修改 |
| PATCH | `/api/todos/{id}/done` | 切换完成状态 |
| DELETE | `/api/todos/{id}` | 删除 |

所有接口返回统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

---

## 五、项目目录结构

```text
javaSpringBootDemo/
├── docs/                         # 过程文档
│   ├── 01-从空架子到SpringBoot系统.md
│   └── 02-Vue前端搭建与联调.md
├── frontend/                     # Vue 前端
│   ├── src/
│   │   ├── App.vue
│   │   ├── api/todo.js
│   │   ├── main.js
│   │   └── styles.css
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── scripts/
│   └── api-test.mjs              # 后端接口回归测试脚本
├── src/main/
│   ├── java/org/example/
│   │   ├── DemoApplication.java
│   │   ├── common/               # 统一响应、全局异常
│   │   ├── config/               # MyBatis-Plus、CORS
│   │   ├── controller/           # 控制器
│   │   ├── entity/               # 实体类
│   │   ├── mapper/               # 数据访问层
│   │   ├── service/              # 业务层
│   │   └── service/impl/         # 业务实现
│   └── resources/
│       ├── application.yml       # 主配置
│       └── sql/schema.sql        # 建库建表脚本
├── .gitignore
├── pom.xml
└── README.md
```

---

## 六、日常开发：怎么提交代码

如果你不习惯 IDEA 的 Git 面板，可以**直接用命令行**，和 VS Code 的 Git 逻辑完全一样。

### 提交并推送

```bash
# 查看改了哪些文件
git status

# 把所有改动加入暂存区
git add .

# 提交（写清楚这次改了什么）
git commit -m "feat: 新增 xxx 功能"

# 推到 GitHub
git push
```

### 拉取最新代码

```bash
git pull
```

### 查看提交历史

```bash
git log --oneline
```

---

## 七、配置说明

### 数据库密码

`src/main/resources/application.yml` 中默认使用占位符：

```yaml
password: "${DB_PASSWORD:123456}"
```

本地开发不设置环境变量时，默认密码就是 `123456`。部署到服务器时，通过环境变量覆盖：

```bash
export DB_PASSWORD=你的真实密码
```

### 端口

后端默认端口 `8080`，前端开发服务器默认 `5173`。

---

## 八、常见错误速查

| 现象 | 原因 | 解决 |
|---|---|---|
| `Cannot resolve method 'getTitle'` | IDEA 没开 Lombok 注解处理器 | Settings → Build → Annotation Processors → 勾选 Enable |
| `com.mysql.cj.jdbc.Driver` 找不到 | 依赖没下载完 | 等 Maven 刷新完，或执行 `mvn clean compile` |
| `Public Key Retrieval is not allowed` | JDBC URL 缺参数 | 确认 URL 里有 `allowPublicKeyRetrieval=true` |
| 时间差 8 小时 | 时区没设 | URL 里加 `serverTimezone=Asia/Shanghai` |
| 前端请求 404 / CORS 错误 | 后端没启动或端口不对 | 先启动后端，再启动前端 |

---

## 九、文档

- [docs/01-从空架子到SpringBoot系统.md](docs/01-从空架子到SpringBoot系统.md)：从空 Maven 项目一步步搭出后端
- [docs/02-Vue前端搭建与联调.md](docs/02-Vue前端搭建与联调.md)：前端怎么起、怎么连后端

---

## 十、学习路线建议

1. 先跑通整个项目（按本 README 第三章）
2. 读 `docs/01` 理解后端分层
3. 打开 `DemoApplication.java` 点绿三角，观察控制台 SQL
4. 改 `TodoController` 加一个自己的接口
5. 改 `App.vue` 调整界面

---

> 这个项目是为 Spring Boot 新手准备的，代码注释和文档比生产项目更啰嗦， deliberately 如此。
