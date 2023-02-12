package com.miaosha.service.cache.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.service.PromoService;
import com.miaosha.service.cache.LocalCacheService;
import com.miaosha.service.cache.RedisBloomService;
import com.miaosha.service.cache.RedisCacheService;
import com.miaosha.service.model.PromoModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.miaosha.service.cache.util.CacheKeyPrefix.PROMO_OF_ITEM_CACHE_KEY;
import static com.miaosha.service.cache.util.CacheKeyPrefix.PROMO_OF_ITEM_PREFIX;

@Service
public class LocalCacheServiceImpl implements LocalCacheService {

    // 存储所有本地缓存
    private Map<String,LoadingCache<String,Object>> caches=new HashMap<>();

    // 活动本地缓存
    private LoadingCache<String,Object> promoLocalCache=null;
    // 活动NULL对象防止频繁查询数据库
    private static final Object CACHE_NULL=new PromoModel();

    // 失败次数统计
    LoadingCache<String,AtomicInteger> failedCache=null;

    @Autowired
    private PromoService promoService;
    @Autowired
    private RedisCacheService redisCacheService;

    @PostConstruct
    public void init(){
        // 初始化cache，初始容量为10，最大为100（超过则根据LRU策略移除项），写入超过1分钟会失效；
        promoLocalCache = CacheBuilder.newBuilder()
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(1,TimeUnit.MINUTES).recordStats()
                .build(new CacheLoader<String, Object>() {

                    @Override
                    public Object load(String key) throws Exception {
                        // 从分布式缓存取
                        PromoModel promoModel= (PromoModel) redisCacheService.get(key);
                        if(promoModel != null)
                            return promoModel;
                        // 分布式缓存没有，则从sor获取
                        String keyStr=key.replace(PROMO_OF_ITEM_PREFIX,"");
                        Integer id=Integer.valueOf(keyStr);
                        promoModel=promoService.getPromoByItemId(id);
                        if(promoModel == null){
                            return CACHE_NULL;
                        }
                        return promoModel;
                    }

                });
        caches.put(PROMO_OF_ITEM_CACHE_KEY,promoLocalCache);

        // 失败次数统计
        failedCache = CacheBuilder.newBuilder()
                .softValues()
                .maximumSize(1000)
                .build(new CacheLoader<String, AtomicInteger>() {
                    @Override
                    public AtomicInteger load(String key){
                        return new AtomicInteger(0);
                    }
                });

    }

    @Override
    public void set(String cacheKey,String key, Object value) {
        // 获取对应缓存
        LoadingCache<String,Object> loaclCache=caches.get(cacheKey);
        loaclCache.put(key,value);
        // 写入分布式
        redisCacheService.set(key,value);
    }

    @Override
    public Object get(String cacheKey,String key) throws ExecutionException {
        // 获取对应缓存
        LoadingCache<String,Object> loaclCache=caches.get(cacheKey);
        if(loaclCache != null){
            // 从缓存取得对应值
            Object value=loaclCache.get(key);
            // 如果值不为空对象，则返回；否则返回null；
            if(value != CACHE_NULL){
                return value;
            }
        }

        return null;
    }

    @Override
    public int incFailCount(String key) throws ExecutionException {
        return failedCache.get(key).incrementAndGet();
    }

    @Override
    public void clearFailCache(String key) {
        failedCache.invalidate(key);
    }

}
