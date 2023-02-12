package com.miaosha.error;

/**
 * 通用错误返回类型
 */
public interface CommenError {
    int getErrorCode();
    String getErrorMsg();
    CommenError setErrorMsg(String errMsg);
}
