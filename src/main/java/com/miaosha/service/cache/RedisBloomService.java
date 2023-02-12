package com.miaosha.service.cache;

/**
 * 利用redis的布隆过滤器防止缓存穿透
 */
public interface RedisBloomService {
    // 将条目加入过滤器
    boolean add(String key);
    // 检查条目是否存在
    boolean exists(String key);
}
