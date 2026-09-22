package org.example.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.entity.Todo;

import java.util.List;

/**
 * 待办事项 Mapper —— 数据访问层（原生 MyBatis 实现）
 *
 * <p>对比 MyBatis-Plus 版本：这里不再继承 {@code BaseMapper<Todo>}，所有 SQL 都得自己写。
 * 两种写法：
 * <ol>
 *   <li>注解式（这个文件）：{@code @Select / @Insert / @Update / @Delete}，SQL 跟着接口方法走，一目了然</li>
 *   <li>XML 写法：在 {@code src/main/resources/mapper/TodoMapper.xml} 里配 {@code <select>...</select>}，
 *       适合复杂 SQL（多表联查、动态条件多）</li>
 * </ol>
 *
 * <p>几个关键注解：
 * <ul>
 *   <li>{@code @Mapper} —— 标记这是 MyBatis Mapper，作用跟 {@code @MapperScan} 二选一即可（本项目用 {@code @MapperScan}）</li>
 *   <li>{@code @Options(useGeneratedKeys=true, keyProperty="id")} —— 配合 {@code @Insert}，
 *       让数据库自增主键在插入后自动回填到 {@code todo.id} 这个字段上</li>
 *   <li>{@code #{title}} 这种语法 —— MyBatis 的占位符，比直接拼字符串安全（防 SQL 注入）</li>
 * </ul>
 */
@Mapper   // 也可以删掉，因为启动类上 @MapperScan 已经覆盖
public interface TodoMapper {

    // ============ 1. 新增 ============
    /**
     * 注意：故意「不」写 create_time 和 update_time 这两列，
     * 让数据库用 DEFAULT CURRENT_TIMESTAMP 自动填。
     *
     * 这是跟 MP 的差别之一：MP 的 save() 会按 @TableField(fill=...) 注解自动决定要不要写这两列，
     * 原生 MyBatis 得自己控制 SQL 的列清单。
     */
    @Insert("""
        INSERT INTO todo (title, description, done, deadline)
        VALUES (#{title}, #{description}, #{done}, #{deadline})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Todo todo);

    // ============ 2. 按 id 查（修改/删除前的存在性判断） ============
    @Select("""
        SELECT id, title, description, done, deadline, create_time, update_time
        FROM todo
        WHERE id = #{id}
        """)
    Todo findById(@Param("id") Long id);

    // ============ 3. 列表查询（支持关键词搜索 + 完成状态过滤） ============
    /**
     * 动态 SQL 注解式写法：用 {@code <script>} 标签包住整段，
     * 里面用 {@code <where> + <if>} 实现可选条件——这是 MyBatis 注解方式最关键的一条技巧。
     *
     * @param titleLike 标题模糊匹配关键词，传 null 表示不过滤
     * @param done     是否已完成，传 null 表示不过滤
     */
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

    // ============ 4. 修改（覆盖式更新，前端传什么就改什么） ============
    /**
     * 注意：跟 findList 一样的陷阱——动态 SQL 标签（{@code <set>} {@code <if>}）必须包在 {@code <script>} 里，
     * 否则 MyBatis 不会解析这些标签，会把字面量直接当 SQL 发出去。
     */
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

    // ============ 5. 单独切换完成状态（比 updateById 更轻量） ============
    @Update("UPDATE todo SET done = #{done} WHERE id = #{id}")
    int updateDone(@Param("id") Long id, @Param("done") Boolean done);

    // ============ 6. 删除 ============
    @Delete("DELETE FROM todo WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // ============ 7. 统计总数 ============
    @Select("SELECT COUNT(*) FROM todo")
    long countAll();

    // ============ 8. 统计已完成数 ============
    @Select("SELECT COUNT(*) FROM todo WHERE done = true")
    long countDone();
}