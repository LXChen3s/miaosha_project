package com.miaosha.service;

public interface SequenceService {

    String generateOrderNo();

    /**
     * 订单流水号简单示例(使用Redis的incr);暂时不可循环;
     * @return 订单号
     */
    String generateOrderNoByRedis();

}
