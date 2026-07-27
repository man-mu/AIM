package lanshan.manmu.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lanshan.manmu.user.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
