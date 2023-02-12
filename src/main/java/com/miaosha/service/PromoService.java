package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.PromoModel;

import java.util.concurrent.ExecutionException;

public interface PromoService {

    // 根据商品id获取即将进行或正在进行的秒杀活动
    PromoModel getPromoByItemId(Integer itemId);

    /**
     * 从redis中根据商品id获取即将进行或正在进行的秒杀活动
     * @param itemId
     * @return
     */
    PromoModel getPromoByItemIdFromRedis(Integer itemId);

    /**
     * 从redis获取活动信息；如果redis没有，回库mysql，并存入redis，设置过期时间为1分钟
     * @param promoId  活动id
     * @return  活动信息
     */
    PromoModel getPromoByPromoIdFromRedis(Integer promoId);

    // 从缓存中根据商品id获取即将进行或正在进行的秒杀活动
    PromoModel getPromoByItemIdFromCache(Integer itemId) throws ExecutionException;

    // 发布活动
    void publishPromo(Integer id);

    /**
     * 获取秒杀令牌；
     * 从redis中获取活动、商品、用户信息，并校验是否合法；
     * redis设置有发放令牌数，校验是否已发放完毕；
     * redis中设置有商品是否售罄标志，校验商品是否已售罄；
     * 利用UUID生成用户秒杀令牌，存入redis，并设置过期时间为5分钟；
     * @param promoId  活动id
     * @param itemId  商品id
     * @param userId  用户id
     * @return  秒删令牌
     * @throws BusinessException  活动异常，商品异常
     */
    String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) throws BusinessException;

}
