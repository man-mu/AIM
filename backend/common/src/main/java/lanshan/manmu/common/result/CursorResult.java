package lanshan.manmu.common.result;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游标分页结果。
 *
 * @param <T> 列表元素类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorResult<T> {

    private List<T> list;
    private String nextCursor;
    private boolean hasMore;
    private long total;

    public static <T> CursorResult<T> of(List<T> list, String nextCursor, boolean hasMore, long total) {
        return new CursorResult<>(list, nextCursor, hasMore, total);
    }
}
