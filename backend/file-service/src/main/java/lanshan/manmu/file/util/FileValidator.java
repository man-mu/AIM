package lanshan.manmu.file.util;

import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;
import lanshan.manmu.common.constant.CommonConst;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;

/**
 * 文件安全校验器。
 * <ul>
 *   <li>文件名安全过滤：防路径穿越，提取合法扩展名</li>
 *   <li>MIME 白名单：排除可执行文件</li>
 *   <li>大小限制：按 purpose 区分图片/附件</li>
 * </ul>
 */
public final class FileValidator {

    // MIME 白名单前缀 + 精确匹配
    private static final Set<String> MIME_PREFIX_WHITELIST = Set.of(
            "image/", "video/", "audio/"
    );
    /**
     * 前缀白名单内的精确拒绝集：image/ 前缀虽放行普通图片，但 image/svg+xml 可内嵌
     * &lt;script&gt;，浏览器直开即 XSS，必须显式排除。
     */
    private static final Set<String> MIME_EXACT_BLOCKLIST = Set.of(
            "image/svg+xml"
    );
    private static final Set<String> MIME_EXACT_WHITELIST = Set.of(
            "application/pdf",
            "text/plain",
            "application/zip",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    // 合法扩展名正则：仅允许字母+数字，1~16 字符
    private static final Pattern EXT_PATTERN =
            Pattern.compile("^[a-zA-Z0-9]{1,16}$");

    /**
     * 校验文件大小。
     * @param size 文件大小（字节）
     * @param purpose 文件用途：1=消息附件 2=头像 3=文档 4=媒体
     *                注：purpose 由客户端传入，不可信；仅 purpose=2(头像) 按图片限制 5MB，
     *                其余（含 purpose=0 默认值 / purpose=4 媒体）按附件限制 100MB
     */
    public static void validateSize(long size, int purpose) {
        if (size <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件大小必须大于 0");
        }
        long maxSize = CommonConst.FILE_MAX_SIZE_ATTACHMENT;
        if (purpose == 2) {
            maxSize = CommonConst.FILE_MAX_SIZE_IMAGE;
        }
        if (size > maxSize) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小 " + size + " 超过限制 " + maxSize);
        }
    }

    /**
     * 校验 MIME 类型是否在白名单内。
     */
    public static void validateMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORT, "MIME 类型为空");
        }
        // 先判精确黑名单（SVG 等可执行内容），防止被 image/ 前缀误放行
        if (MIME_EXACT_BLOCKLIST.contains(mimeType)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORT,
                    "不支持的 MIME 类型（安全风险）: " + mimeType);
        }
        for (String prefix : MIME_PREFIX_WHITELIST) {
            if (mimeType.startsWith(prefix)) return;
        }
        if (MIME_EXACT_WHITELIST.contains(mimeType)) return;
        throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORT,
                "不支持的 MIME 类型: " + mimeType);
    }

    /**
     * 从文件名中安全提取扩展名。
     * <p>防路径穿越：剔除 / \ .. 等字符，仅保留末尾合法扩展名。
     * @param name 原始文件名（不可信，来自客户端）
     * @return 安全的扩展名（小写，无点号），如 "jpg"；无合法扩展名返回 "bin"
     */
    public static String safeExtractExt(String name) {
        if (name == null || name.isEmpty()) {
            return "bin";
        }
        // 取最后一个 '.' 之后的部分
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "bin";
        }
        String rawExt = name.substring(dotIndex + 1);
        // 仅允许字母+数字，防注入
        if (!EXT_PATTERN.matcher(rawExt).matches()) {
            return "bin";
        }
        return rawExt.toLowerCase();
    }

    /**
     * 生成 MinIO objectKey。
     * <p>格式：files/{yyyy-MM-dd}/{fileId}.{ext}
     * @param fileId Snowflake ID
     * @param ext 安全过滤后的扩展名（无点号）
     */
    public static String buildObjectKey(long fileId, String ext) {
        String date = LocalDate.now().toString();  // yyyy-MM-dd
        return String.format("files/%s/%d.%s", date, fileId, ext);
    }

    private FileValidator() {}
}