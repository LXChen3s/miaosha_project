package com.miaosha.controller;

import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.response.CommenReturnType;
import com.miaosha.util.ExcpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * controller层统一异常处理中心
 */
@ControllerAdvice
public class ExceptionHandleAdvice {

    private static Logger logger=LoggerFactory.getLogger(ExceptionHandleAdvice.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Object handleException(HttpServletRequest httpServletRequest,Exception ex){
        Map<String,Object> returnData=new HashMap<>();

        if(ex instanceof BusinessException){
            BusinessException businessException=(BusinessException)ex;

            returnData.put("errCode",businessException.getErrorCode());
            returnData.put("errMsg",businessException.getErrorMsg());
        }else if(ex instanceof ServletRequestBindingException){
            returnData.put("errCode",EmBusinessError.UNKNOW_ERROR.getErrorCode());
            returnData.put("errMsg","url路由绑定问题！");
        }else if(ex instanceof NoHandlerFoundException){
            returnData.put("errCode",EmBusinessError.UNKNOW_ERROR.getErrorCode());
            returnData.put("errMsg","没有找到对应访问路径！");
        }else{
            // 异常打印进日志
            while (ex != null){
                logger.error(ExcpUtil.buildErrorMessage(ex));
                ex= (Exception) ex.getCause();
            }

//            ex.printStackTrace();

            returnData.put("errCode",EmBusinessError.UNKNOW_ERROR.getErrorCode());
            returnData.put("errMsg",EmBusinessError.UNKNOW_ERROR.getErrorMsg());
        }

        return CommenReturnType.create("fail",returnData);
    }
}
