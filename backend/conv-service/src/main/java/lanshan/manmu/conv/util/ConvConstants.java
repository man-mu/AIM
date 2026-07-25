package lanshan.manmu.conv.util;

/**
 * conv-service 本地常量。
 * <p>角色用 {@link lanshan.manmu.common.constant.MemberRole}，会话类型用
 * {@link lanshan.manmu.common.constant.ConvType}，此处不重复定义。
 */
public final class ConvConstants {

    private ConvConstants() {}

    /** memberType：DB 存 'user'/'bot' */
    public static final String MEMBER_TYPE_USER = "user";
    public static final String MEMBER_TYPE_BOT  = "bot";

    /** 成员上限 */
    public static final int MAX_MEMBER_COUNT = 500;

    /** 字段长度限制 */
    public static final int MAX_NAME_LENGTH         = 32;
    public static final int MAX_ANNOUNCEMENT_LENGTH = 500;
    public static final int MAX_ALIAS_LENGTH        = 32;

    /** muteUntil 单位：epoch 秒（不是毫秒），0=永久或未禁言 */
    public static final long MUTE_PERMANENT = 0L;
}
