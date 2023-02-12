package com.miaosha.service.impl;

import com.miaosha.dao.SequenceDOMapper;
import com.miaosha.dataobject.SequenceDO;
import com.miaosha.service.SequenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class SequenceServiceImpl implements SequenceService {
    private final Logger logger=LoggerFactory.getLogger(SequenceServiceImpl.class);

    @Autowired
    SequenceDOMapper sequenceDOMapper;
    @Autowired
    RedisTemplate redisTemplate;


    /**
     * 订单流水号简单示例;暂时不可循环;
     * @return 订单号
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderNo(){
        logger.info("进入流水号生成");

        StringBuilder stringBuilder =new StringBuilder();
        // 订单号总共16位
        // 前8位为年月日
        LocalDate now=LocalDate.now();
        String nowData=now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        stringBuilder.append(nowData);

        // 中间六位为当日对应自增唯一数字
        int sequence=0;
        logger.info("查询当前流水号");
        SequenceDO sequenceDO=sequenceDOMapper.selectByName("order_info");
        sequence=sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequence+sequenceDO.getStep());
        logger.info("更新流水号");
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);

        String sequenceStr=String.valueOf(sequence);
        for(int i=0;i<6-sequenceStr.length();i++){
            stringBuilder.append("0");
        }
        stringBuilder.append(sequenceStr);

        // 后两位为分库分表位
        stringBuilder.append("00");

        return stringBuilder.toString();
    }

    public String generateOrderNoByRedis(){
        if(logger.isDebugEnabled()){
            logger.info("进入Redis流水号生成");
        }
        StringBuilder stringBuilder =new StringBuilder();
        // 订单号总共16位
        // 前8位为年月日
        LocalDate now=LocalDate.now();
        String nowData=now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        stringBuilder.append(nowData);

        // redis自增
        long sequence=redisTemplate.opsForValue().increment("");

        String sequenceStr=String.valueOf(sequence);
        for(int i=0;i<6-sequenceStr.length();i++){
            stringBuilder.append("0");
        }
        stringBuilder.append(sequenceStr);

        // 后两位为分库分表位
        stringBuilder.append("00");


        return stringBuilder.toString();
    }

}
