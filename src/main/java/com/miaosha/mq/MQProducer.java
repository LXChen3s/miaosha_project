package com.miaosha.mq;

import com.alibaba.fastjson.JSON;
import com.miaosha.dao.StockLogDOMapper;
import com.miaosha.dataobject.StockLogDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class MQProducer {
    @Autowired
    private OrderService orderService;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    private DefaultMQProducer defaultMQProducer;

    private TransactionMQProducer transactionMQProducer;

    private Logger logger;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;

    @PostConstruct
    public void init() throws MQClientException {
        defaultMQProducer=new DefaultMQProducer("producer-group");
        defaultMQProducer.setNamesrvAddr(nameAddr);
        defaultMQProducer.start();

        transactionMQProducer=new TransactionMQProducer("transaction-producer-group");
        transactionMQProducer.setNamesrvAddr(nameAddr);

        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {
                Map<String,Object> argMap= (Map<String, Object>) o;
                Integer userId= (Integer) argMap.get("userId");
                Integer itemId= (Integer) argMap.get("itemId");
                Integer promoId= (Integer) argMap.get("promoId");
                Integer amount= (Integer) argMap.get("amount");
                String stockLogId= (String) argMap.get("stockLogId");

                // 事务型消息，本地执行事务
                try {
                    logger.info(Thread.currentThread().getName()+"开始执行本地事务！");

                    orderService.createOrder(userId,itemId,promoId,amount,stockLogId);

                    logger.info(Thread.currentThread().getName()+"执行本地事务成功！");
                } catch (BusinessException e) {
//                    e.printStackTrace();
                    logger.info(Thread.currentThread().getName()+"执行本地事务失败！");

                    StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    if(stockLogDO == null){
                        return LocalTransactionState.UNKNOW;
                    }
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);

                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }catch (Exception ex){
                    logger.info("执行本地事务失败！抛出了其他异常！");
                    ex.printStackTrace();
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }

                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                String msgStr=new String(messageExt.getBody(),Charset.forName("utf-8"));
                Map<String,Object> bodyMap=JSON.parseObject(msgStr);
                Integer itemId= (Integer) bodyMap.get("itemId");
                Integer amount= (Integer) bodyMap.get("amount");
                String stockLogId= (String) bodyMap.get("stockLogId");

                logger.info(Thread.currentThread().getName()+"本地事务回调检查！");

                StockLogDO stockLogDO=stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if(stockLogDO == null){
                    return LocalTransactionState.UNKNOW;
                }
                if(stockLogDO.getStatus() == 2){
                    return LocalTransactionState.COMMIT_MESSAGE;
                }else if(stockLogDO.getStatus() == 1){
                    return LocalTransactionState.UNKNOW;
                }
                return LocalTransactionState.ROLLBACK_MESSAGE;

            }
        });

        transactionMQProducer.start();

        logger=LoggerFactory.getLogger(MQProducer.class);
    }

    /**
     * 利用事务型消息同步库存扣减
     * @param userId  用户id
     * @param itemId  商品id
     * @param promoId  活动id
     * @param amount  商品购买数量
     * @param stockLogId  库存流水id
     * @return  消息是否发送成功
     */
    public boolean transactionalAsyncReduceStock(Integer userId,Integer itemId
            ,Integer promoId,Integer amount,String stockLogId) {
        Map<String,Object> bodyMap=new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        bodyMap.put("stockLogId",stockLogId);

        Map<String,Object> argMap=new HashMap<>();
        argMap.put("itemId",itemId);
        argMap.put("amount",amount);
        argMap.put("userId",userId);
        argMap.put("promoId",promoId);
        argMap.put("stockLogId",stockLogId);

        Message message=new Message(topicName,"reduce_stock"
                , JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("utf-8")));

        TransactionSendResult sendResult=null;
        try {
            logger.info(Thread.currentThread().getName()+"开始发送消息！");

            sendResult=transactionMQProducer.sendMessageInTransaction(message,argMap);

            logger.info(Thread.currentThread().getName()+"发送消息成功！发送结果为："+sendResult.getLocalTransactionState());
        } catch (MQClientException e) {
            e.printStackTrace();
            logger.info(Thread.currentThread().getName()+"发送消息失败！");

            return false;
        }
        if(sendResult.getLocalTransactionState()==LocalTransactionState.COMMIT_MESSAGE){
            return true;
        }else {
            return false;
        }

    }

    // 同步库存扣减消息
    public boolean asyncReduceStock(Integer itemId,Integer amount) {
        Map<String,Object> map=new HashMap<>();
        map.put("itemId",itemId);
        map.put("amount",amount);
        Message message=new Message(topicName,"reduce_stock"
                , JSON.toJSON(map).toString().getBytes(Charset.forName("utf-8")));
        try {
            defaultMQProducer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
