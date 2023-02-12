package com.miaosha.service.impl;

import com.miaosha.dao.ItemDOMapper;
import com.miaosha.dao.ItemStockDOMapper;
import com.miaosha.dao.StockLogDOMapper;
import com.miaosha.dataobject.ItemDO;
import com.miaosha.dataobject.ItemStockDO;
import com.miaosha.dataobject.StockLogDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.mq.MQProducer;
import com.miaosha.service.ItemService;
import com.miaosha.service.PromoService;
import com.miaosha.service.model.ItemModel;
import com.miaosha.service.model.PromoModel;
import com.miaosha.validator.ValidationResult;
import com.miaosha.validator.ValidatorImpl;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ItemDOMapper itemDOMapper;
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private PromoService promoService;

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MQProducer mqProducer;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    private ItemDO convertFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemDO itemDO=new ItemDO();
        BeanUtils.copyProperties(itemModel,itemDO);
        // double存在精度问题；
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }
    private ItemStockDO convertStockFromModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemStockDO itemStockDO=new ItemStockDO();
        itemStockDO.setStock(itemModel.getStock());
        itemStockDO.setItemId(itemModel.getId());

        return itemStockDO;
    }

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        // 校验入参
        ValidationResult validationResult=validator.validate(itemModel);
        if(validationResult.isHasErr()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        // model->dataObject
        ItemDO itemDO=convertFromItemModel(itemModel);

        // 写入数据库
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO=convertStockFromModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);

        // 返回创建对象
        return getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOS=itemDOMapper.listItem();

        // 此处应使用批量查询
        List<ItemModel> itemModels = itemDOS.stream().map(itemDO -> {
            ItemStockDO itemStockDO=itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel=covertModelFromDO(itemStockDO,itemDO);
            return itemModel;
        }).collect(Collectors.toList());

        return itemModels;
    }

    /**
     * 从mysql获取商品信息；查询item，item_stock表
     * @param id  商品id
     * @return  商品信息
     */
    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO=itemDOMapper.selectByPrimaryKey(id);
        if(itemDO == null){
            return null;
        }
        ItemStockDO itemStockDO=itemStockDOMapper.selectByItemId(itemDO.getId());

        ItemModel itemModel=covertModelFromDO(itemStockDO,itemDO);
        // 从redis获取活动信息
        PromoModel promoModel=promoService.getPromoByItemIdFromRedis(id);
        if(promoModel != null && promoModel.getStatus().intValue() != 3){
            itemModel.setPromoModel(promoModel);
        }

        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        int affectRow=itemStockDOMapper.decreaseStock(itemId,amount);
        // 根据数据库影响行数判断扣减是否成功
        return affectRow > 0;
    }

    @Override
    @Transactional
    public boolean decreaseStockForPromo(Integer itemId, Integer amount) {
        if(amount == null){
            return false;
        }
        Long stock=redisTemplate.opsForValue().increment("promo_item_stock_"+itemId, amount *-1);
        if(stock != null && stock > 0){
            return true;
        }else if(stock == 0){
            // 在redis中设置产品售罄标志
            redisTemplate.opsForValue().set("promo_item_stock_invalid_"+itemId,"true");

            return true;
        }else {
            // amount过多，扣减失败，应重新加回去
            redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean increaseSales(Integer itemId, Integer amount) throws BusinessException {
        int affectRow=itemDOMapper.increaseSales(itemId,amount);
        return affectRow>0;
    }

    @Override
    public ItemModel getItemByIdFromRedis(Integer id) {
        ItemModel itemModel= (ItemModel) redisTemplate.opsForValue().get("item_"+id);
        if(itemModel == null){
            itemModel=getItemById(id);
            redisTemplate.opsForValue().set("item_"+id,itemModel);
            redisTemplate.expire("item_"+id,1,TimeUnit.MINUTES);
        }

        return itemModel;
    }

    @Override
    @Transactional
    public String initStockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO=new StockLogDO();
        stockLogDO.setAmount(amount);
        stockLogDO.setItemId(itemId);
        stockLogDO.setStatus(1);
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));
        stockLogDOMapper.insertSelective(stockLogDO);

        return stockLogDO.getStockLogId();
    }

    private ItemModel covertModelFromDO(ItemStockDO itemStockDO,ItemDO itemDO){
        ItemModel itemModel=new ItemModel();
        BeanUtils.copyProperties(itemDO,itemModel);
        itemModel.setPrice(BigDecimal.valueOf(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());

        return itemModel;
    }

}
