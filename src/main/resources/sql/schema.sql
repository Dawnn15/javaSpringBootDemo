-- ============================================
-- 待办事项演示库：初始化脚本
-- 数据库：demo_db
-- 字符集：utf8mb4（支持 emoji）
--
-- ⚠️ 注意：本脚本包含 DROP TABLE，会清空 todo 表已有数据
--         仅用于首次初始化 / 重置演示环境
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
    PRIMARY KEY (id),
    KEY idx_done (done),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='待办事项表';

-- 种子数据：方便前端一打开就能看到内容
INSERT INTO todo (title, description, done, deadline)
VALUES ('跑通第一个 Spring Boot 接口', '打开浏览器访问 /api/todos 看返回的 JSON', 1, '2026-09-20 18:00:00'),
       ('在 IDEA 里点绿三角启动后端', '观察控制台是否出现 Tomcat started on port 8080', 0, '2026-09-21 12:00:00'),
       ('启动 Vue 前端并连上后端', 'npm run dev 之后访问 http://localhost:5173', 0, '2026-09-21 20:00:00'),
       ('理解 Controller-Service-Mapper 三层', '看看一次新增请求在控制台打印了哪条 SQL', 0, NULL);
