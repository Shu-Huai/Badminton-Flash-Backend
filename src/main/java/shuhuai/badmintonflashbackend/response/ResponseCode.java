package shuhuai.badmintonflashbackend.response;

import lombok.Getter;

@Getter
public enum ResponseCode {
    SUCCESS(200, "操作成功"),

    UNAUTHORIZED(401, "需要身份认证"),

    USERNAME_OR_PASSWORD_ERROR(401, "用户名或密码错误"),

    USERNAME_EXISTED(2041, "用户名已存在"),

    TOKEN_EXPIRED(401, "token已过期"),

    TOKEN_INVALID(401, "token无效"),

    FORBIDDEN(403, "权限不足"),

    FAILED(1001, "操作失败"),

    VALIDATE_FAILED(1002, "参数校验失败"),

    VSPHERE_LINK_ERROR(2001, "vsphere连接操作失败"),

    ERROR(5000, "未知错误"),

    PARAM_ERROR(5010, "参数错误"),

    SQL_ERROR(5001, "服务器错误");

    private final int code;
    private final String msg;

    ResponseCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
