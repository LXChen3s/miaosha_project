package com.miaosha.error;

public class BusinessException extends Exception implements CommenError {
    private CommenError commenError;

    // 利用CommenError构造异常
    public BusinessException(CommenError commenError){
        super();
        this.commenError=commenError;
    }
    // 自定义错误信息构造异常
    public BusinessException(CommenError commenError,String errMsg){
        super();
        this.commenError=commenError;
        this.commenError.setErrorMsg(errMsg);
    }

    @Override
    public int getErrorCode() {
        return this.commenError.getErrorCode();
    }

    @Override
    public String getErrorMsg() {
        return this.commenError.getErrorMsg();
    }

    @Override
    public CommenError setErrorMsg(String errMsg) {
        this.commenError.setErrorMsg(errMsg);
        return this.commenError;
    }
}
