package lanshan.manmu.common.result;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页结果。
 *
 * @param <T> 列表元素类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }
}
