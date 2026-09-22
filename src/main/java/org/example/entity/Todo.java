package org.example.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办事项实体 —— 对应数据库表 todo
 *
 * <p>对比 MyBatis-Plus 版本：本类去掉了：
 * <ul>
 *   <li>{@code @TableName("todo")} —— 原生 MyBatis 通过 {@code resultMap} 或注解映射表名，不需要它</li>
 *   <li>{@code @TableId(type = IdType.AUTO)} —— 通过 {@code @Options(useGeneratedKeys=true, keyProperty="id")} 在 Mapper 上声明主键回填</li>
 * </ul>
 * 字段名、表名、类型保持完全一致，对照时只看差异行就行。
 */
@Data
public class Todo {

    /** 主键。由数据库自增，插入时通过 Mapper 的 @Options 回填到这个字段 */
    private Long id;

    /** 标题，必填 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 个字符")
    private String title;

    /** 详细描述，选填 */
    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;

    /** 是否已完成 */
    private Boolean done;

    /**
     * 截止时间
     * 注意：spring.jackson.date-format 只管老的 java.util.Date，
     *       对 LocalDateTime 无效，必须用 @JsonFormat 控制格式
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 创建时间。留 null 让数据库的 DEFAULT CURRENT_TIMESTAMP 生效 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间。数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}