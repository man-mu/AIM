package lanshan.manmu.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lanshan.manmu.file.model.entity.FileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMapper extends BaseMapper<FileEntity> {
}