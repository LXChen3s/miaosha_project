package com.miaosha.error;

public enum EmBusinessError implements CommenError{
    // 通用错误类型
    PARAMETER_VALIDATION_ERROR(00001,"参数错误"),
    UNKNOW_ERROR(00002,"未知错误"),
    // 错误码类似http分段
    USER_NOT_EXIST(10001,"用户不存在"),
    USER_LOGIN_FAIL(10002,"用户手机号或密码不正确！"),
    USER_NOT_LOGIN(10003,"用户未登录！"),
    // 30000开头为交易错误
    STOCK_NOT_ENOUGH(30001,"商品库存不足")

    ;


    // 错误码
    private int errCode;
    // 错误信息
    private String errMsg;

    EmBusinessError(int errCode,String errMsg){
        this.errCode=errCode;
        this.errMsg=errMsg;
    }

    @Override
    public int getErrorCode() {
        return errCode;
    }

    @Override
    public String getErrorMsg() {
        return errMsg;
    }

    @Override
    public CommenError setErrorMsg(String errMsg) {
        this.errMsg=errMsg;
        return this;
    }
}
