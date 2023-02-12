package com.miaosha.service.cache.impl;

import com.miaosha.service.cache.RedisCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;

@Service
public class RedisCacheImpl implements RedisCacheService {
    private ExecutorService executorService;
    private static final Object REDIS_NULL=new Object();

    @Autowired
    private RedisTemplate redisTemplate;

    @PostConstruct
    public void init() {
        executorService=Executors.newFixedThreadPool(10);
    }

    @Override
    public void set(String key, Object object) {
        // 异步写入分布式缓存
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                redisTemplate.opsForValue().set(key,object);
                redisTemplate.expire(key,10,TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public Object get(String key) throws InterruptedException, ExecutionException, TimeoutException {
        // 异步定时从redis获取缓存
        Future future=executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                Object valueInRedis=redisTemplate.opsForValue().get(key);
                if(valueInRedis == REDIS_NULL){
                    return null;
                }
                return valueInRedis;
            }
        });
        Object result=future.get(3,TimeUnit.SECONDS);

        return result;
    }
}
