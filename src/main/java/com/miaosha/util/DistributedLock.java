package com.miaosha.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// 分布式锁
public class DistributedLock {
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 带超时限制的获取锁；
     * @param lockName 一般锁对应key值
     * @param acquireTimeout 获取锁最长限时，超过未获取到则获取锁失败
     * @param lockTimeout 锁超时时间，超过将自动被释放
     * @return 锁对应uuid；未获取到返回空
     */
    public String acquireLockWithTimeout(String lockName, long acquireTimeout, long lockTimeout)
    {
        String identifier = UUID.randomUUID().toString();
        String lockKey = "lock:" + lockName;
        int lockExpire = (int)(lockTimeout / 1000);

        long end = System.currentTimeMillis() + acquireTimeout;
        while (System.currentTimeMillis() < end) {
            if (redisTemplate.opsForValue().setIfAbsent(lockKey, identifier)){
                redisTemplate.expire(lockKey, lockExpire,TimeUnit.SECONDS);
                return identifier;
            }
            // 未获得锁，检查已被获得的锁是否被限时
            if (redisTemplate.getExpire(lockKey)  == -1) {
                redisTemplate.expire(lockKey, lockExpire,TimeUnit.SECONDS);
            }
            // 延时后继续获取锁
            try {
                Thread.sleep(1);
            }catch(InterruptedException ie){
                Thread.currentThread().interrupt();
            }
        }

        // null indicates that the lock was not acquired
        return null;
    }

    /**
     * 锁释放
     * @param lockName 锁名称
     * @param identifier 锁的uuid
     * @return 是否成功；true：成功
     */
    public boolean releaseLock(String lockName, String identifier) {
        String lockKey = "lock:" + lockName;

        while (true){
            redisTemplate.watch(lockKey);
            if (identifier.equals(redisTemplate.opsForValue().get(lockKey))){
                redisTemplate.multi();
                redisTemplate.delete(lockKey);
                List<Object> results = redisTemplate.exec();
                if (results == null){
                    continue;
                }
                return true;
            }

            redisTemplate.unwatch();
            break;
        }

        return false;
    }

}
