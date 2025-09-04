package shuhuai.badmintonflashbackend.excep;

import lombok.Getter;
import shuhuai.badmintonflashbackend.response.ResponseCode;

@Getter
public class BaseException extends RuntimeException {
    private final ResponseCode responseCode;

    public BaseException(ResponseCode responseCode) {
        super(responseCode.getMsg());
        this.responseCode = responseCode;
    }
}