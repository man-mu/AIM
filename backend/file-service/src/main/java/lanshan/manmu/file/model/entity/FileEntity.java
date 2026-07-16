package lanshan.manmu.file.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件元数据实体。
 * <p>对应 {@code file.files} 表，MinIO 对象元数据镜像。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("files")
public class FileEntity {

    @TableId
    private Long id;
    private String name;
    private String key;            // MinIO object key
    private Long size;             // 字节
    private String mimeType;
    private String ext;            // 安全过滤后的扩展名，如 "jpg"
    private Integer width;         // Phase 1 始终 0
    private Integer height;        // Phase 1 始终 0
    private Integer duration;      // Phase 1 始终 0
    private String md5;
    private Integer purpose;       // 1=消息附件 2=头像 3=文档 4=媒体
    private Integer access;        // 1=私有 2=会话可见 3=公开
    private Long uploaderId;
    private String bucket;
    private Integer status;        // 0=PENDING 1=CONFIRMED 2=DELETED
    private OffsetDateTime createdAt;
}