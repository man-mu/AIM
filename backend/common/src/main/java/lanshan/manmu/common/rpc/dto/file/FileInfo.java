package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 文件信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private long fileId;
    private String name;
    private String key;
    private long size;
    private String mimeType;
    private String ext;
    private int width;
    private int height;
    private int duration;
    private String md5;
    private int purpose;
    private int access;
    private long uploaderId;
    private String bucket;
    private int status;          // 0=PENDING 1=CONFIRMED 2=DELETED
    private long createdAt;
}
