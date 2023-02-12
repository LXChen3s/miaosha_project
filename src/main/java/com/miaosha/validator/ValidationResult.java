package com.miaosha.validator;

import java.util.HashMap;
import java.util.Map;

/**
 * 校验结果
 */
public class ValidationResult {
    // 校验是否有错
    private boolean hasErr=false;
    // 校验错误信息
    private Map<String,String> errMsgMap=new HashMap<>();

    public boolean isHasErr() {
        return hasErr;
    }

    public void setHasErr(boolean hasErr) {
        this.hasErr = hasErr;
    }

    public Map<String, String> getErrMsgMap() {
        return errMsgMap;
    }

    public void setErrMsgMap(Map<String, String> errMsgMap) {
        this.errMsgMap = errMsgMap;
    }

    // 格式化获取错误结果
    public String getErrMsg(){
        return org.apache.commons.lang3.StringUtils.join(errMsgMap.values().toArray(),",");
    }

}
