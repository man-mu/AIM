package lanshan.manmu.conv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lanshan.manmu.conv.model.entity.ConversationMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {
}
