package com.miaosha.service.cache;

import java.util.concurrent.ExecutionException;

public interface LocalCacheService {
    // 向缓存中存方法
    void set(String cacheKey,String key,Object value);
    // 从缓存中取方法
    Object get(String cacheKey,String key) throws ExecutionException;
    // 增加缓存失败次数;返回失败次数
    int incFailCount(String key) throws ExecutionException;
    // 清空对应key失败次数
    void clearFailCache(String key);
}
