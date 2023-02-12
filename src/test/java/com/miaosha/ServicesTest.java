package com.miaosha;

import com.miaosha.dao.ItemDOMapper;
import com.miaosha.error.BusinessException;
import com.miaosha.service.OrderService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest(classes = App.class,properties = "application.properties",webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServicesTest {
    private final Logger logger=LoggerFactory.getLogger(ServicesTest.class);

    @Autowired
    private OrderService orderService;
    @Autowired
    private ItemDOMapper itemDOMapper;

    @Test
    public void testCreateOrder(){
        long startTime=System.currentTimeMillis();

        CyclicBarrier cyclicBarrier=new CyclicBarrier(1);

        ExecutorService executorService=Executors.newCachedThreadPool();
        for(int i=0;i<1;i++){
            executorService.submit(new Runnable() {
                @Override
                public void run() {

                    try {
                        cyclicBarrier.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (BrokenBarrierException e) {
                        e.printStackTrace();
                    }

                    try {
                        orderService.createOrder(6000,
                                1,1,
                                1,"025a380c6d26442eba48f5f2b8101fc8");
                    } catch (BusinessException e) {
                        e.printStackTrace();
                    }

                }
            });
        }

        long endTime=System.currentTimeMillis();

        logger.info("消耗时间："+(endTime-startTime));
    }

}
