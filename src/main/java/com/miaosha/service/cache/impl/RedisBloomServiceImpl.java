package com.miaosha.service.cache.impl;

import com.miaosha.service.cache.RedisBloomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class RedisBloomServiceImpl implements RedisBloomService {
    @Autowired
    private RedisTemplate redisTemplate;

    // 初始化128m空间
    private static final int SIZE=128*1024*1024*8;


//    private Client client = null;
//
//    @Value("${redisbloom.host}")
//    private String host;
//    @Value("${redisbloom.port}")
//    private Integer port;

    @PostConstruct
    public void init(){
//        client=new Client(host, port);

        redisTemplate.opsForValue().setBit("bloomFliter",SIZE,false);
    }

    @Override
    public boolean add(String key) {

        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                // 进行8次hash
                for(int i=0;i<8;i++){
                    int seed=getBKDRSeed(i+1);
                    int hash=bkdrHash(key,seed);
                    long offset=hash%SIZE;
                    connection.setBit("bloomFliter".getBytes(),offset,true);
                }
                return null;
            }
        },redisTemplate.getValueSerializer());

//        return client.add("newFliter",key);
        return false;
    }

    @Override
    public boolean exists(String key) {

        List list=redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                // 进行8次hash
                for(int i=0;i<8;i++){
                    int seed=getBKDRSeed(i+1);
                    int hash=bkdrHash(key,seed);
                    long offset=hash%SIZE;
                    connection.getBit("bloomFliter".getBytes(),offset);
                }
                return null;
            }
        },redisTemplate.getValueSerializer());

        for (Object o:list){
            if(!(Boolean)o){
                return false;
            }
        }

//        return client.exists("newFliter",key);
        return true;
    }

    /**
     * BKDRHash算法
     */
    private static int bkdrHash(String str, int seed) {
        int hash = 0;
        for (int i = 0; i < str.length(); i++) {
            hash = (hash * seed) + str.charAt(i);
        }
        return (hash & 0x7FFFFFFF);
    }

    /**
     * 获取第n次hash的seed;13,131,1313,13131,...
     * @return      seed
     */
    private static int getBKDRSeed(int n){
        StringBuilder stringBuilder=new StringBuilder("1");
        for (int i=0;i<n;i++){
            if(i%2 ==0){
                stringBuilder.append("3");
            }else{
                stringBuilder.append("1");
            }
        }
        return Integer.valueOf(stringBuilder.toString());
    }

    public static void main(String args[]){



//        RedisClient client = RedisClient.create("redis://192.168.43.8:6379");
//        StatefulRedisConnection<String, String> connection = client.connect();
//
//        RedisCommands sync = connection.sync();
//
//        RedisCodec<String, String> codec = StringCodec.UTF8;
//
//        Object res=((RedisCommands) sync).dispatch(RedisBloomCommandType.ADD,new IntegerOutput(codec),new CommandArgs<>(codec)
//                .addKey("newFliter")
//                .addValue("foo1"));
//
//        System.out.println(res);

    }

}
