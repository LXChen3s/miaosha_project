package com.miaosha.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.mq.MQProducer;
import com.miaosha.response.CommenReturnType;
import com.miaosha.service.ItemService;
import com.miaosha.service.OrderService;
import com.miaosha.service.PromoService;
import com.miaosha.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequestMapping("/order")
@CrossOrigin(allowCredentials="true",allowedHeaders="*")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MQProducer mqProducer;

    @Autowired
    private ItemService itemService;
    @Autowired
    private PromoService promoService;

    private ExecutorService executorService;

    private RateLimiter orderCreateRateLimiter;

    private Logger logger;

    @PostConstruct
    public void init(){
        ThreadFactory threadFactory=new ThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            private final ThreadGroup group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(group, r,"pool-do-future-"+threadNumber.incrementAndGet(),0);
                if (t.isDaemon())
                    t.setDaemon(false);
                if (t.getPriority() != Thread.NORM_PRIORITY)
                    t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };
        // newFixedThreadPool????????????????????????
        executorService= Executors.newFixedThreadPool(20,threadFactory);

        orderCreateRateLimiter=RateLimiter.create(700);

        logger=LoggerFactory.getLogger(OrderController.class);
    }

    // ??????????????????????????????????????????????????????????????????????????????????????????redis?????????
    @RequestMapping(value = "/generateToken",method = RequestMethod.POST,consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public CommenReturnType generateToken(@RequestParam("itemId") Integer itemId,
                                        @RequestParam("promoId") Integer promoId) throws BusinessException {
        // ????????????????????????
        String token=httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }
        // ???redis??????????????????
        UserModel userModel= (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }

        String secondKillToken=promoService.generateSecondKillToken(promoId,itemId,userModel.getId());

        if(secondKillToken == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"??????????????????!");
        }

        return CommenReturnType.create(secondKillToken);
    }

    // ??????????????????
    @RequestMapping(value = "/create",method = RequestMethod.POST,consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public CommenReturnType createOrder(@RequestParam("itemId") Integer itemId,
                                        @RequestParam("amount") Integer amount,
                                        @RequestParam(value = "promoId",required = false) Integer promoId,
                                        @RequestParam(value = "secondKillToken",required = false) String secondKillToken)
            throws BusinessException {
        // ????????????id
        String token=httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }
        // ??????id???redis??????????????????
        UserModel userModel= (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }

        // ???????????????????????????
        if(!orderCreateRateLimiter.tryAcquire()){
            throw new BusinessException(EmBusinessError.UNKNOW_ERROR,"??????????????????");
        }

        if(promoId != null){
            // ??????????????????
            String secondKillTokenInRedis= (String) redisTemplate.opsForValue()
                    .get("promoId_"+promoId+"itemId_"+itemId+"userId_"+userModel.getId());
            if(secondKillTokenInRedis == null){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"?????????????????????");
            }
            if(!StringUtils.equals(secondKillToken,secondKillTokenInRedis)){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"?????????????????????");
            }
        }

        // ????????????
        // ??????????????????????????????????????????????????????20????????????????????????future???get??????????????????????????????
        Future future=executorService.submit(new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                logger.info(Thread.currentThread().getName()+"????????????");

                // ??????????????????init??????
                String stockLogId=itemService.initStockLog(itemId,amount);

                // ????????????
                boolean result=mqProducer.transactionalAsyncReduceStock(userModel.getId(),itemId,promoId,amount,stockLogId);
                if(!result){
                    logger.info(Thread.currentThread().getName()+"???????????????");

                    throw new BusinessException(EmBusinessError.UNKNOW_ERROR,"???????????????");
                }

                logger.info(Thread.currentThread().getName()+"???????????????");

                return null;
            }
        });

        // orderService.createOrder(userModel.getId(),itemId,promoId,amount);

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOW_ERROR,"???????????????");
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOW_ERROR,"???????????????");
        }

        return CommenReturnType.create(null);
    }

    @Autowired
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private DataSource dataSource;

    @RequestMapping(value = "/test",method = RequestMethod.GET)
    @ResponseBody
    public CommenReturnType test(){
        logger.info("????????????????????????"+platformTransactionManager.getClass().getName());

        long startTime=System.currentTimeMillis();

        int num=9;
        CyclicBarrier cyclicBarrier=new CyclicBarrier(num);
        CountDownLatch countDownLatch=new CountDownLatch(num);

        ExecutorService executorService=Executors.newCachedThreadPool();



        for(int i=0;i<num;i++){
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
                    } catch (Throwable ex){
                        ex.printStackTrace();
                    }

                    countDownLatch.countDown();

                }
            });
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long endTime=System.currentTimeMillis();

        logger.info("???????????????"+(endTime-startTime));

        return CommenReturnType.create(null);
    }

}
