package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.OrderModel;

public interface OrderService {

    /**
     * 创建订单；
     * 从redis获取商品信息，校验数量是否合法；
     * 用户参与活动，对redis进行减库存操作；
     * 从redis获取订单流水号；
     * 订单数据插入订单表；
     * 更新商品表销量；
     * 获取库存流水，并更新为已完成状态（2）；
     * @param userId  用户id
     * @param itemId  商品id
     * @param promoId  活动id
     * @param amount  出售数量
     * @param stockLogId  库存流水id
     * @return  订单信息
     * @throws BusinessException
     */
    OrderModel createOrder(Integer userId,Integer itemId,Integer promoId,Integer amount,String stockLogId) throws BusinessException;

}
