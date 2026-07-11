package lanshan.manmu.common.constant;

/**
 * 消息类型常量。
 */
public final class MsgType {

    public static final int UNSPECIFIED = 0;
    public static final int TEXT   = 1;
    public static final int IMAGE  = 2;
    public static final int FILE   = 3;
    public static final int VIDEO  = 4;
    public static final int AUDIO  = 5;
    public static final int LOCATION = 6;
    public static final int SYSTEM = 7;
    public static final int CUSTOM = 8;
    public static final int BOT    = 9;

    private MsgType() {}
}
