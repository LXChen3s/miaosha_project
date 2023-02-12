package com.miaosha.response;

/**
 * 通用请求返回类型
 */
public class CommenReturnType {
    // 返回对应请求处理结果；成功为“success”，失败为“fail”；
    private String status;
    // 若status为success，则返回前端需要数据
    // 若status为fail，则返回通用错误码格式
    private Object data;

    // 定义通用创建方法
    public static CommenReturnType create(Object result){
        return CommenReturnType.create("success",result);
    }
    public static CommenReturnType create(String status,Object result){
        CommenReturnType commenReturnType=new CommenReturnType();
        commenReturnType.setStatus(status);
        commenReturnType.setData(result);
        return commenReturnType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
