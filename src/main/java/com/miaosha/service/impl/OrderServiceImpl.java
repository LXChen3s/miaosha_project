package com.miaosha.service.impl;

import com.miaosha.dao.OrderDOMapper;
import com.miaosha.dao.StockLogDOMapper;
import com.miaosha.dataobject.OrderDO;
import com.miaosha.dataobject.StockLogDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.mq.MQProducer;
import com.miaosha.service.ItemService;
import com.miaosha.service.OrderService;
import com.miaosha.service.SequenceService;
import com.miaosha.service.UserService;
import com.miaosha.service.model.ItemModel;
import com.miaosha.service.model.OrderModel;
import com.miaosha.service.model.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Service
public class OrderServiceImpl implements OrderService {
    private final Logger logger=LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;
    @Autowired
    private SequenceService sequenceService;

    @Autowired
    private OrderDOMapper orderDOMapper;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Transactional
    @Override
    public OrderModel createOrder(Integer userId, Integer itemId
            ,Integer promoId, Integer amount,String stockLogId) throws BusinessException {
        // 校验下单状态；用户是否合法，商品是否存在，数量是否正确
        ItemModel itemModel= itemService.getItemByIdFromRedis(itemId);
        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品不存在！");
        }
        logger.info("检查商品："+itemModel);

//        UserModel userModel=userService.getUserByIdFromRedis(userId);
//        if(userModel == null){
//            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"用户不存在！");
//        }

        if(amount<=0 || amount >1000){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"数量不合法！");
        }
        // 校验活动信息
//        if(promoId != null){
//            // 1,校验该商品是否存在该活动
//            if(promoId.intValue() != itemModel.getPromoModel().getId()){
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"该商品活动不合法！");
//            }
//            // 2，校验活动是否正在进行
//            else {
//                if(itemModel.getPromoModel().getStatus() != 2){
//                    throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动未在进行！");
//                }
//            }
//        }

        // 落单减库存/支付减库存
        boolean result=false;
        if(promoId != null){
            result=itemService.decreaseStockForPromo(itemId,amount);
        }else {
            result=itemService.decreaseStock(itemId,amount);
        }
        if(!result){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        logger.info("减库存："+result);

        // 订单入库///////
        OrderModel orderModel=new OrderModel();
        orderModel.setItemId(itemId);
        orderModel.setUserId(userId);
        orderModel.setAmount(amount);
        orderModel.setPromoId(promoId);

        // 根据是否参加活动，设置商品价格（普通价格，活动价格）
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        // 根据价格设置商品花费金额
        orderModel.setAmountOfMoney(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        logger.info("开始生成交易流水号："+orderModel);
        // 生成交易流水号
        orderModel.setId(sequenceService.generateOrderNoByRedis());
        logger.info("生成交易流水号："+orderModel);
        OrderDO orderDO=convertFromModel(orderModel);
        // 插入订单表
        orderDOMapper.insertSelective(orderDO);
        logger.info("插入订单："+orderModel);

        // 增加销量/////////
         itemService.increaseSales(itemId,amount);
        logger.info("增加销量！");

        // 设置库存流水状态为扣减成功
        StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO==null){
            throw new BusinessException(EmBusinessError.UNKNOW_ERROR,"订单异常！");
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
        logger.info("更新流水："+stockLogDO);

        // 返回前端////////
        return null;
    }

    private OrderDO convertFromModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO=new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        orderDO.setAmountOfMoney(orderModel.getAmountOfMoney().doubleValue());
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        return orderDO;
    }


}
