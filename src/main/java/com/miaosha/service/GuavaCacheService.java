package com.miaosha.service;

// 本地缓存操作
public interface GuavaCacheService {
    // 存方法
    void setCommonCache(String key,Object value);
    // 取方法
    Object getFromCommonCache(String key);

}
