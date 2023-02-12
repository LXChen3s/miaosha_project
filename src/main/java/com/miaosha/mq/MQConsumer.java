package com.miaosha.mq;

import com.alibaba.fastjson.JSON;
import com.miaosha.dao.ItemStockDOMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class MQConsumer {

    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    private DefaultMQPushConsumer defaultMQPushConsumer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;

    @PostConstruct
    public void init() throws MQClientException {
        defaultMQPushConsumer=new DefaultMQPushConsumer("stock-consumer");
        defaultMQPushConsumer.setNamesrvAddr(nameAddr);
        // 消费订阅主题
        defaultMQPushConsumer.subscribe(topicName,"*");
        defaultMQPushConsumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list,
                                                            ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                // 同步到数据库
                Message msg=list.get(0);
                String msgStr=new String(msg.getBody(),Charset.forName("utf-8"));
                Map<String,Object> map=JSON.parseObject(msgStr);
                Integer itemId= (Integer) map.get("itemId");
                Integer amount= (Integer) map.get("amount");
                String stockLogId= (String) map.get("stockLogId");

                // 幂等控制
                Object result=redisTemplate.opsForValue().get("decStockId_"+stockLogId);
                if (null != result){
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }

                // 库存表扣减库存
                itemStockDOMapper.decreaseStock(itemId,amount);
                redisTemplate.opsForValue().set("decStockId_"+stockLogId,true);
                redisTemplate.expire("decStockId_"+stockLogId,6,TimeUnit.HOURS);

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        defaultMQPushConsumer.start();

    }



}
