package com.miaosha.service.impl;

import com.miaosha.dao.PromoDOMapper;
import com.miaosha.dataobject.PromoDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.service.ItemService;
import com.miaosha.service.PromoService;
import com.miaosha.service.UserService;
import com.miaosha.service.cache.LocalCacheService;
import com.miaosha.service.cache.RedisBloomService;
import com.miaosha.service.cache.util.CacheKeyPrefix;
import com.miaosha.service.model.ItemModel;
import com.miaosha.service.model.PromoModel;
import com.miaosha.service.model.UserModel;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.miaosha.service.cache.util.CacheKeyPrefix.PROMO_OF_ITEM_CACHE_KEY;

@Service
public class PromoServiceImpl implements PromoService {
    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;

    @Autowired
    private LocalCacheService localCacheService;
    @Autowired
    private RedisBloomService redisBloomService;


    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        // 获取对应商品活动
        PromoDO promoDO=promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel=convertFromPromoDO(promoDO);

        // 判断活动状态
        if(promoModel == null)
            return null;
        updatePromoState(promoModel);

        return promoModel;
    }

    @Override
    public PromoModel getPromoByItemIdFromRedis(Integer itemId) {
        PromoModel promoModel= (PromoModel) redisTemplate.opsForValue().get("promo_"+itemId);
        if(promoModel == null){
            // 布隆过滤
            boolean res=redisBloomService.exists("promo_"+itemId);
            // LoggerFactory.getLogger("test").info("测试结果"+res);

            promoModel = getPromoByItemId(itemId);
            redisTemplate.opsForValue().set("promo_"+itemId,promoModel);
            redisTemplate.expire("promo_"+itemId,1,TimeUnit.MINUTES);
        }
        return promoModel;
    }

    @Override
    public PromoModel getPromoByPromoIdFromRedis(Integer promoId) {
        PromoModel promoModel= (PromoModel) redisTemplate.opsForValue().get("promo_by_id_"+promoId);
        if(promoModel == null){
            PromoDO promoDO=promoDOMapper.selectByPrimaryKey(promoId);
            promoModel=convertFromPromoDO(promoDO);
            redisTemplate.opsForValue().set("promo_by_id_"+promoId,promoModel);
            redisTemplate.expire("promo_by_id_"+promoId,1,TimeUnit.MINUTES);
        }
        return promoModel;
    }

    @Override
    public PromoModel getPromoByItemIdFromCache(Integer itemId) throws ExecutionException {
        PromoModel promoModel= (PromoModel) localCacheService.get(PROMO_OF_ITEM_CACHE_KEY,String.valueOf(itemId));
        return promoModel;
    }

    @Override
    public void publishPromo(Integer id) {
        PromoDO promoDO=promoDOMapper.selectByPrimaryKey(id);
        if(promoDO == null || promoDO.getItemId() == 0){
            return;
        }
        ItemModel itemModel=itemService.getItemById(promoDO.getItemId());

        // 将活动商品同步到redis
        redisTemplate.opsForValue().set("promo_item_stock_"+promoDO.getItemId(),itemModel.getStock());

        // 将令牌发放上限写入redis
        redisTemplate.opsForValue().set("promo_door_count_"+promoDO.getId(),itemModel.getStock()*3);

    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) throws BusinessException {
        // 从redis获取活动信息；如果redis没有，回库mysql，并存入redis，设置过期时间为1分钟
        PromoModel promoModel=this.getPromoByPromoIdFromRedis(promoId);

        if(promoModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动不存在！");
        }

        // 更新活动状态,判断活动是否进行中
        updatePromoState(promoModel);
        if(promoModel.getStatus() != 2){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动未开始！");
        }

        // 校验下单状态；用户是否合法，商品是否存在，数量是否正确
        ItemModel itemModel= itemService.getItemByIdFromRedis(itemId);
        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品不存在！");
        }

        UserModel userModel=userService.getUserByIdFromRedis(userId);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }

        // 校验令牌发放上限是否已超过
        Long result=redisTemplate.opsForValue().increment("promo_door_count_"+promoId,-1);
        if(result < 0){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH,"商品已售罄！");
        }

        // 校验商品库存是否已售罄
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH,"商品已售罄！");
        }

        String uuidToken=UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("promoId_"+promoId+"itemId_"+itemId+"userId_"+userId,uuidToken);
        redisTemplate.expire("promoId_"+promoId+"itemId_"+itemId+"userId_"+userId,5,TimeUnit.MINUTES);

        return uuidToken;
    }

    /**
     * 根据当前主机时间判断活动状态：
     * 设置为1：活动未开始
     * 设置为2：活动进行中
     * 设置为3：活动已结束
     * （当前主机时间可能存在异常！！！）
     * @param promoModel  活动信息
     */
    private void updatePromoState(PromoModel promoModel){
        if(promoModel.getStartTime().isAfterNow()){
            promoModel.setStatus(1);
        }
        else if(promoModel.getEndTime().isBeforeNow()){
            promoModel.setStatus(3);
        }
        else {
            promoModel.setStatus(2);
        }
    }

    private PromoModel convertFromPromoDO(PromoDO promoDO){
        if(promoDO == null)
            return null;
        PromoModel promoModel=new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartTime(new DateTime(promoDO.getStartTime()));
        promoModel.setEndTime(new DateTime(promoDO.getEndTime()));
        return promoModel;
    }

}
