package lanshan.manmu.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举，按服务分段。
 * <pre>
 *   通用:      0~999
 *   user:    10000~19999
 *   friend:  20000~29999
 *   conv:    30000~39999
 *   message: 40000~49999
 *   file:    50000~59999
 *   signaling:60000~69999
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS           (0,    "success"),
    BAD_REQUEST       (400,  "请求参数错误"),
    UNAUTHORIZED      (401,  "未认证"),
    FORBIDDEN         (403,  "无权限"),
    NOT_FOUND         (404,  "资源不存在"),
    INTERNAL_ERROR    (500,  "服务器内部错误"),

    // user-service 1xxxx
    USER_NOT_FOUND        (10001, "用户不存在"),
    USER_ALREADY_EXISTS   (10002, "用户名已存在"),
    USER_PHONE_EXISTS     (10003, "手机号已被注册"),
    USER_PASSWORD_ERROR   (10004, "密码错误"),
    USER_TOKEN_INVALID    (10005, "Token 无效或已过期"),
    USER_TOKEN_EXPIRED    (10006, "Token 已过期"),
    USER_SESSION_NOT_FOUND(10007, "会话不存在"),
    USER_FORBIDDEN        (10008, "用户被禁用"),

    // friend-service 2xxxx
    FRIEND_ALREADY_EXISTS    (20001, "已经是好友"),
    FRIEND_NOT_EXISTS        (20002, "非好友关系"),
    FRIEND_REQUEST_EXISTS    (20003, "好友申请已存在"),
    FRIEND_REQUEST_NOT_FOUND (20004, "好友申请不存在"),
    FRIEND_REQUEST_HANDLED   (20005, "申请已处理"),
    BLOCKED_BY_USER          (20006, "已被对方拉黑"),
    ALREADY_BLOCKED          (20007, "已拉黑该用户"),
    NOT_BLOCKED              (20008, "未拉黑该用户"),

    // conv-service 3xxxx
    CONV_NOT_FOUND        (30001, "会话不存在"),
    CONV_MEMBER_EXISTS    (30002, "用户已是会话成员"),
    CONV_MEMBER_NOT_FOUND (30003, "用户不在会话中"),
    CONV_NOT_MEMBER       (30004, "非会话成员"),
    CONV_PERMISSION_DENIED(30005, "权限不足"),
    CONV_MUTED            (30006, "已被禁言"),
    CONV_MUTED_ALL        (30007, "全员禁言中"),
    CONV_MEMBER_LIMIT     (30008, "成员数超上限"),
    CONV_OWNER_TRANSFER_SELF(30009, "不能转让给自己"),

    // message-service 4xxxx
    MESSAGE_NOT_FOUND      (40001, "消息不存在"),
    MESSAGE_RECALL_TIMEOUT (40002, "撤回超时"),
    MESSAGE_EDIT_TIMEOUT   (40003, "编辑超时"),
    MESSAGE_DUPLICATE      (40004, "重复消息"),
    MESSAGE_NOT_SENDER     (40005, "非消息发送者"),
    MESSAGE_ALREADY_RECALLED(40006, "消息已撤回"),
    MESSAGE_SEQ_GEN_FAILED (40007, "序号生成失败"),

    // file-service 5xxxx
    FILE_NOT_FOUND        (50001, "文件不存在"),
    FILE_UPLOAD_FAILED    (50002, "文件上传失败"),
    FILE_TOO_LARGE        (50003, "文件过大"),
    FILE_TYPE_NOT_SUPPORT (50004, "不支持的文件类型"),

    // signaling-service 6xxxx
    NOTIF_NOT_FOUND       (60001, "通知不存在"),
    ;

    private final int code;
    private final String message;
}
