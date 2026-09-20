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
 * 待办事项实体 —— 对应数据库表 todo
 *
 * 类比：货架上的货物，各层之间传递的标准集装箱
 */
@Data
@TableName("todo")
public class Todo {

    /** 主键。IdType.AUTO 表示由数据库自增生成，插入后会自动回填到这个字段 */
    @TableId(type = IdType.AUTO)
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
