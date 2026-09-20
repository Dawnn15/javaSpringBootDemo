# Vue 3 前端搭建与联调记录

> 配套后端：`docs/01-从空架子到SpringBoot系统.md`
> 前端技术栈：Vue 3.5.43 · Vite 8.3.0 · 原生 fetch · 原生 CSS
> 所在目录：`frontend/`

---

## 一、为什么前端要单独一个项目

前后端分离之后，两个项目各跑各的进程：

```
┌─────────────────────┐         ┌──────────────────────┐
│  Vue 前端            │  HTTP   │  Spring Boot 后端    │
│  localhost:5173     │ ──────> │  localhost:8080      │
│  Vite 开发服务器      │  /api   │  内嵌 Tomcat          │
└─────────────────────┘         └──────────┬───────────┘
                                            │ JDBC
                                            ▼
                                    ┌──────────────┐
                                    │  MySQL 3306  │
                                    └──────────────┘
```

好处是分工清晰、各自独立部署。代价是多了一个「**跨域**」问题 —— 这正是后面要解决的核心。

---

## 二、技术选型：为什么这次用原生写法

你之前接触的是 Vue 2 + Element UI。这个项目我刻意没用组件库，原因：

| 选择 | 用什么 | 为什么这么选 |
|---|---|---|
| 框架 | **Vue 3** + `<script setup>` | 组合式 API 是现在的写法，逻辑按功能聚合而不是按选项分散 |
| 构建 | **Vite 8** | 冷启动 300ms 级别，改代码秒级热更新，比 Webpack 快一个量级 |
| UI | **原生 CSS** | 不引入 Element Plus，是为了看清「一个列表页到底由哪些部分构成」 |
| 请求 | **原生 fetch** | 不引入 axios，是为了看清「一次请求到底做了什么」 |

等你把这套跑通了，再上 Element Plus 和 axios 会非常顺 —— 因为你知道它们替你做了什么。

---

## 三、目录结构

```
frontend/
├── package.json          # 依赖与脚本
├── vite.config.js        # 构建配置 + 跨域代理（重点）
├── index.html            # 唯一的 HTML 入口
├── .gitignore
└── src/
    ├── main.js           # 应用入口，把 App.vue 挂到 #app 上
    ├── styles.css        # 全局样式与 CSS 变量
    ├── api/
    │   └── todo.js       # 后端接口封装（所有请求都从这里走）
    └── App.vue           # 页面本体：模板 + 逻辑 + 样式
```

**Vue 单文件组件（SFC）** 的意思是：一个 `.vue` 文件里同时装三样东西。

```vue
<script setup>  /* 逻辑：数据、方法 */  </script>
<template>      /* 结构：HTML */        </template>
<style scoped>  /* 样式：CSS */         </style>
```

`scoped` 表示这段 CSS 只作用于当前组件，不会污染别的组件 —— 相当于自动帮你加了唯一类名前缀。

---

## 四、怎么创建这个项目

`npm create vue@latest` 是官方脚手架，但它是**交互式**的，会一路问你十几个问题（要不要 TypeScript、要不要 Router、要不要 Pinia……）。

为了过程透明可控，我选择**手动创建文件**：

```bash
mkdir frontend && cd frontend
# 手写 package.json、vite.config.js、index.html、src/*
npm install        # 装依赖
npm run dev        # 启动
```

`package.json` 里只有三个依赖，就这么点：

```json
{
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.5.43"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^6.0.9",
    "vite": "^8.3.0"
  }
}
```

| 依赖 | 作用 |
|---|---|
| `vue` | 框架本体，**运行时要用的**，所以放 `dependencies` |
| `vite` | 构建工具，**只在开发/打包时用**，所以放 `devDependencies` |
| `@vitejs/plugin-vue` | 让 Vite 认识 `.vue` 文件（否则它只懂 .js） |

**一个小坑**：`"type": "module"` 这行必须有。Vite 的配置文件用了 ES Module 语法（`import`/`export default`），
没有这行 Node 会按 CommonJS 解析，直接报语法错误。

---

## 五、核心：用代理解决跨域

### 跨域是怎么产生的

浏览器有「同源策略」：**协议 + 域名 + 端口，三者有一个不同就算跨域**。

```
前端 http://localhost:5173
后端 http://localhost:8080
              ↑ 端口不同 → 跨域 → 浏览器拦截请求
```

注意：**是浏览器拦的，不是后端拒绝的**。请求其实发出去了、后端也处理了，只是响应被浏览器丢掉，控制台报一片红。

而且浏览器通常会先发一个 `OPTIONS` 预检请求问后端「你允许我跨域吗」，后端不点头就直接失败。

### 方案一：后端配 CORS（已做）

就是后端那个 `WebCorsConfig`：

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
}
```

**这里有个经典坑**：`allowedOrigins("*")` 和 `allowCredentials(true)` **不能同时用**，
Spring 会直接抛异常。想既放行所有来源又允许带 Cookie，必须写成 `allowedOriginPatterns("*")`。

### 方案二：前端配代理（也做了）

```js
// vite.config.js
server: {
  port: 5173,
  open: true,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

**原理**：前端代码里写 `fetch('/api/todos')`，请求先发到 `localhost:5173`。
Vite 开发服务器看到路径以 `/api` 开头，就在**服务器端**把请求转发给 8080，再把结果原样返回。

关键在于：请求是 **Vite 服务器**替浏览器发的，服务器之间不存在同源策略。
对浏览器来说，从头到尾都只在跟 5173 打交道，压根没有跨域这回事。

```
方案一：浏览器 ──跨域请求──> 后端      （要后端点头）
方案二：浏览器 ──同源请求──> Vite ──转发──> 后端   （浏览器无感）
```

### 为什么两个都配

| 场景 | 靠谁 |
|---|---|
| 本地开发（`npm run dev`，5173/8080） | 代理为主，CORS 兜底 |
| 打包部署（前端静态文件和后端同域） | 都不需要 |
| 前端部署在别的域名（真跨域） | 必须靠后端 CORS |

两个都配，任何一个失效都不至于卡住你。生产环境该关掉代理、按实际域名收紧 CORS。

---

## 六、接口封装：src/api/todo.js

所有请求集中在一个文件里，好处是**要改前缀、要加 token、要统一处理错误，只动一个地方**。

```js
const BASE = '/api/todos'

async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })

  if (!res.ok) {
    throw new Error(`请求失败 HTTP ${res.status}`)
  }

  const body = await res.json()

  // 后端统一返回 { code, message, data }，这里做一次拆包
  if (body.code !== 200) {
    throw new Error(body.message || '操作失败')
  }

  return body.data
}
```

**这个 `request` 函数是整个前端最值得看的地方**，它做了三件事：

1. 统一加 `Content-Type`，不用每个调用点都写一遍
2. 统一判断业务状态码 —— 后端约定 `code !== 200` 就是失败，统一在这里抛异常
3. 统一拆包 —— 页面组件拿到的直接是 `data`，不用每次都 `res.data.data`

因为后端做了统一响应体 `Result<T>`，前端才能这么干净。**这就是统一格式的价值。**

对外暴露的方法就 6 个：

```js
export const todoApi = {
  list(params)        { ... },   // GET    /api/todos?done=&keyword=
  stats()             { ... },   // GET    /api/todos/stats
  create(data)        { ... },   // POST   /api/todos
  update(id, data)    { ... },   // PUT    /api/todos/{id}
  toggle(id)          { ... },   // PATCH  /api/todos/{id}/done
  remove(id)          { ... }    // DELETE /api/todos/{id}
}
```

和后面的 7 个接口一一对应，看名字就知道打哪个 URL。

---

## 七、App.vue 拆解

界面上一共有五个区域，对应模板里的五块：

```
┌──────────────────────────────────────────┐
│  待办清单                                  │  header
│  Spring Boot + MySQL + Vue 3 全栈示例       │
├──────────────────────────────────────────┤
│ [ 5 ]  [ 3 ]  [ 2 ]  [ ▓▓▓░░ 40% ]        │  统计卡片
│  全部   待完成  已完成   完成进度            │
├──────────────────────────────────────────┤
│  新增待办                                  │
│  [ 要做什么？              ]               │  输入区
│  [ 补充说明（选填）         ]               │
│  截止时间 [____]            [ 添加 ]        │
├──────────────────────────────────────────┤
│  (全部)(待完成)(已完成)      [ 搜索标题… ]  │  筛选工具栏
├──────────────────────────────────────────┤
│  ☐  跑通第一个接口                          │
│     打开浏览器访问 /api/todos                │  列表
│     截止 xxx  创建于 xxx      [编辑][删除]   │
└──────────────────────────────────────────┘
```

### 状态设计

用 `ref` / `reactive` 声明，改数据界面自动更新（这就是 Vue 的核心价值）：

```js
const todos = ref([])                 // 列表数据
const stats = ref({ total: 0, finished: 0, pending: 0 })   // 统计数据
const loading = ref(false)            // 加载中
const busyId = ref(null)              // 正在请求的条目 id（用来禁用按钮、防止重复点击）
const filter = ref('all')             // 当前筛选：all / pending / finished
const keyword = ref('')               // 搜索关键词

const form = reactive({ title: '', description: '', deadline: '' })  // 新增表单
const editingId = ref(null)           // 当前在编辑哪一条，null 表示没在编辑
const editForm = reactive({ ... })    // 编辑中的数据
const toast = reactive({ show: false, type: 'success', text: '' })   // 提示
```

`ref` 和 `reactive` 的区别：`ref` 包一个值（读的时候要 `.value`，模板里自动解包），`reactive` 包一个对象。

### 几个值得学的写法

**1. 筛选和搜索变成查询参数，而不是前端过滤**

```js
async function loadTodos() {
  const params = {}
  if (filter.value === 'pending')  params.done = false
  if (filter.value === 'finished') params.done = true
  if (keyword.value.trim())        params.keyword = keyword.value.trim()

  todos.value = await todoApi.list(params)
}
```

筛选交给后端做。数据量上万时，前端全量拉下来再过滤会卡死。

**2. 搜索加防抖**

```js
let searchTimer = null
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadTodos, 300)
})
```

不加防抖的话，你每敲一个字就发一次请求，打「Vue」两个字要发 3 次。防抖让它在停手 300ms 后才发。

**3. 编辑是「就地展开」而不是弹窗**

`editingId` 记录当前编辑哪一行，模板里用它决定渲染编辑态还是展示态：

```vue
<li v-for="todo in todos" :key="todo.id">
  <div v-if="editingId === todo.id">   <!-- 编辑态 -->
  <template v-else>                     <!-- 展示态 -->
</li>
```

**4. 时间格式要转两次**

后端返回 `"2026-09-21 18:00:00"`，但 `<input type="datetime-local">` 要的是 `"2026-09-21T18:00"`：

```js
// 发给后端前
function toApiDatetime(value) {
  if (!value) return null
  return value.replace('T', ' ') + (value.length === 16 ? ':00' : '')
}

// 塞回输入框前
function toInputDatetime(value) {
  if (!value) return ''
  return value.replace(' ', 'T').slice(0, 16)
}
```

**5. 清空描述时传空字符串，不是 null**

```js
description: editForm.description.trim()   // 不能传 null
```

因为后端 MyBatis-Plus 默认跳过 null 字段。传 null 相当于「这个字段不用改」，原来的描述就删不掉了。

---

## 八、启动与联调

### 启动顺序

**后端必须先起**，否则前端页面能打开但请求全失败。

```bash
# 窗口 1：启动后端（在项目根目录）
#   IDEA 里点 DemoApplication 的绿三角
#   或者命令行：mvn spring-boot:run

# 窗口 2：启动前端
cd frontend
npm run dev
```

前端启动后会输出：

```
  VITE v8.3.0  ready in 302 ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

浏览器会自动打开 `http://localhost:5173`（`open: true` 的效果）。

### 怎么确认联调成功了

打开页面后：

| 检查点 | 预期 |
|---|---|
| 统计卡片 | 显示 4 / 3 / 1，进度 25% |
| 列表 | 有 4 条种子数据，未完成的排在前面 |
| 控制台 | 没有红色报错 |
| 加一条待办 | 列表立刻刷新，新条目出现在最上面 |
| IDEA 控制台 | 打印出 `INSERT INTO todo ...` 的 SQL |

**最后一条最能说明问题**：IDEA 控制台里出现 SQL，说明
「点击按钮 → fetch → Vite 代理 → Controller → Service → Mapper → MySQL → 返回 → 界面更新」
整条链路全通了。

### 打包上线

```bash
cd frontend
npm run build     # 产物在 frontend/dist/
```

产物只有三个文件：

```
dist/index.html                  0.42 kB
dist/assets/index-xxx.css        6.92 kB
dist/assets/index-xxx.js        77.59 kB  (gzip 后 30.53 kB)
```

把 `dist/` 里的文件拷到后端 `src/main/resources/static/` 下，前端就和后端同域了，
连代理和 CORS 都不需要。这是最简单的部署方式，适合练手。

---

## 九、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| 页面打开是白屏 | JS 报错 | 按 F12 看 Console 的具体报错 |
| 请求报 `ERR_CONNECTION_REFUSED` | 后端没启动 | 先起后端 8080 |
| 请求报 404 | 后端接口路径不对 | 确认后端是 `/api/todos` 开头 |
| 请求报 CORS 错误 | 代理没生效 / 后端 CORS 没配 | 确认 `vite.config.js` 里 `changeOrigin: true` |
| 改了 `vite.config.js` 不生效 | Vite 不会热重载自己的配置文件 | **必须重启 `npm run dev`** |
| 新增后列表里时间是「null」 | 后端 createTime 问题 | 见 01 文档「坑 4」 |
| `npm install` 卡住 | 走的是国外源 | `npm config set registry https://registry.npmmirror.com` |
| `npm run dev` 报端口占用 | 5173 被占 | Vite 会自动换 5174，看终端输出的实际端口 |

**最有用的一条**：改完 `vite.config.js`（尤其是代理配置）没效果，八成是忘了重启开发服务器。
Vite 能热更新你的组件代码，但读不到自己配置文件的改动。

---

## 十、下一步可以玩什么

1. **换成组合式函数**：把列表加载、增删改抽成 `useTodos()`，让 `App.vue` 只剩调用
2. **上 Element Plus**：表格、分页、弹窗、消息提示全都有现成组件
3. **上 axios**：请求拦截器统一加 token，响应拦截器统一处理 401 跳登录
4. **加路由 vue-router**：拆成「列表页 / 详情页 / 关于页」
5. **加状态管理 Pinia**：多个页面共享同一份待办数据
6. **加 TypeScript**：给接口返回值定义类型，写错字段名编译期就报错
7. **做移动端适配**：现在的 CSS 已经有 `@media (max-width: 640px)` 断点，可以继续打磨

---

## 版本记录

| 组件 | 版本 | 说明 |
|---|---|---|
| Vue | 3.5.43 | 用 `<script setup>` 组合式 API |
| Vite | 8.3.0 | 要求 Node ≥ 20.19 或 ≥ 22.12 |
| @vitejs/plugin-vue | 6.0.9 | 支持 Vite 5 / 6 / 7 / 8 |
| Node（本机） | 26.1.0 | 系统 nvm4w 管理 |
| npm（本机） | 11.13.0 | registry 已指向 npmmirror |
