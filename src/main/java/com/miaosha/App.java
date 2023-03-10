package com.miaosha;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
@SpringBootApplication(scanBasePackages = {"com.miaosha"})
@MapperScan("com.miaosha.dao")
public class App
{
    public static void main( String[] args )
    {
        SpringApplication.run(App.class, args);
    }

}
