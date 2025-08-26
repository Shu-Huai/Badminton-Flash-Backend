package shuhuai.badmintonflashbackend.response;

import lombok.Data;

import java.io.Serializable;

@Data
@SuppressWarnings({"unused"})
public class Response<Type> implements Serializable {
    private int code;
    private ResponseCode enumCode;
    private String message;
    private Type data;

    public Response() {
        code = ResponseCode.SUCCESS.getCode();
        enumCode = ResponseCode.SUCCESS;
        message = ResponseCode.SUCCESS.getMsg();
        data = null;
    }

    public Response(ResponseCode code) {
        this.code = code.getCode();
        this.enumCode = code;
        this.message = code.getMsg();
    }

    //
    public Response(Throwable error) {
        this.message = error.getMessage();
    }

    public Response(ResponseCode code, Type data) {
        this.code = code.getCode();
        enumCode = code;
        message = code.getMsg();
        this.data = data;
    }

    public Response(Type data) {
        code = ResponseCode.SUCCESS.getCode();
        enumCode = ResponseCode.SUCCESS;
        message = ResponseCode.SUCCESS.getMsg();
        this.data = data;
    }

    public void setCode(ResponseCode code) {
        this.code = code.getCode();
        this.enumCode = code;
        this.message = code.getMsg();
        this.data = null;
    }
}
