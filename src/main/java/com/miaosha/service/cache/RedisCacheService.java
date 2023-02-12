package com.miaosha.service.cache;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface RedisCacheService {
    void set(String key,Object object);
    Object get(String key) throws InterruptedException, ExecutionException, TimeoutException;
}
