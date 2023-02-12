package com.miaosha.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.miaosha.service.GuavaCacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
public class GuavaCacheServiceImpl implements GuavaCacheService {

    private Cache<String,Object> commonCache=null;

    @PostConstruct
    public void init(){
        // 初始化cache，初始容量为10，最大为100（超过则根据LRU策略移除项），写入超过1分钟会失效；
        commonCache = CacheBuilder.newBuilder()
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(1,TimeUnit.MINUTES)
                .build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
