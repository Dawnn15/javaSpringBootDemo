package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.entity.Todo;

/**
 * 待办事项 Mapper —— 数据访问层
 *
 * 继承 BaseMapper<Todo> 之后，一个方法都不用写，就自动拥有：
 *   insert / deleteById / updateById / selectById / selectList / selectPage / selectCount ...
 * 不用写 SQL，也不用写 XML。
 *
 * 这里没有加 @Mapper 注解，是因为启动类上已经写了
 * @MapperScan("org.example.mapper")，两者选一个即可。
 */
public interface TodoMapper extends BaseMapper<Todo> {
}
