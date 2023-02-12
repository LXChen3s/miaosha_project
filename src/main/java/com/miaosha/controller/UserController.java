package com.miaosha.controller;

import com.alibaba.druid.util.StringUtils;
import com.miaosha.controller.viewobject.UserVO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.response.CommenReturnType;
import com.miaosha.service.UserService;
import com.miaosha.service.model.UserModel;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Encoder;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CrossOrigin注解 解决跨域问题；前端ajax请求要加上 xhrFields:{withCredentials:true}
 */
@Controller
@RequestMapping("/user")
@CrossOrigin(allowCredentials="true",allowedHeaders="*")
public class UserController {

    @Autowired
    UserService userService;
    //  运用了ThreadLocal，获取的是当前线程的用户请求
    @Autowired
    HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    // 用户登录接口
    @RequestMapping(value = "/login",method = RequestMethod.POST,consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public CommenReturnType login(@RequestParam(name = "telphone") String telphone,
                                  @RequestParam(name = "password") String password) throws BusinessException,
            UnsupportedEncodingException, NoSuchAlgorithmException {
        // 入参校验
        if(org.apache.commons.lang3.StringUtils.isEmpty(telphone)
                || org.apache.commons.lang3.StringUtils.isEmpty(password)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        // 校验登录
        UserModel userModel=userService.login(telphone,this.encodeByMd5(password));

        // 将登录成功凭证加入到用户session；分布式会话redis
        // httpServletRequest.getSession().setAttribute("IS_LOGIN",true);
        // httpServletRequest.getSession().setAttribute("LOGIN_USER",userModel);

        // 将登录凭证、登录信息存入redis
        // 生成登录凭证token；UUID
        String uuidToken=UUID.randomUUID().toString();
        uuidToken=uuidToken.replace("-","");

        // 建立token与用户登录态关系
        redisTemplate.opsForValue().set(uuidToken,userModel);
        redisTemplate.expire(uuidToken,1,TimeUnit.HOURS);

        // 下发token
        return CommenReturnType.create(uuidToken);
    }

    // 用户注册接口
    @RequestMapping(value = "/register",method = RequestMethod.POST,consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public CommenReturnType register(@RequestParam(name = "telphone") String telphone,
                                     @RequestParam(name = "otpCode") String optCode,
                                     @RequestParam(name = "password") String password,
                                     @RequestParam(name = "age") Integer age,
                                     @RequestParam(name = "name") String name,
                                     @RequestParam(name = "gender") Byte gender) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        // 校验验证码与手机号一致
        String otpCodeInSession= (String) httpServletRequest.getSession().getAttribute(telphone);
        if(!StringUtils.equals(optCode,otpCodeInSession)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR.setErrorMsg("验证码错误！"));
        }
        // 注册流程
        UserModel userModel=new UserModel();
        userModel.setTelphone(telphone);
        userModel.setAge(age);
        userModel.setGender(gender);
        userModel.setName(name);

        userModel.setRegisterMode("by phone");
        userModel.setThirdId("none");

        userModel.setEncrptPassword(encodeByMd5(password));

        userService.register(userModel);

        return CommenReturnType.create(null);
    }

    // 密码md5加密
    public String encodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest messageDigest=MessageDigest.getInstance("md5");
        BASE64Encoder base64Encoder=new BASE64Encoder();
        return base64Encoder.encode(messageDigest.digest(str.getBytes("utf-8")));
    }

    // 用户修改个人信息接口
    public CommenReturnType updateUserInfo(){

        return CommenReturnType.create(null);
    }

    // 获取短信验证码
    @RequestMapping(value = "/getotp",method = RequestMethod.POST,consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public CommenReturnType getOtp(@RequestParam(name = "telphone") String telphone, HttpServletResponse httpServletResponse){
        // 随机生成opt短信验证码
        Random random=new Random();
        int randomInt=random.nextInt(99999);
        randomInt+=10000;
        String otpCode=String.valueOf(randomInt);

        // 存储验证码与手机号关系；可以利用redis
        HttpSession httpSession=httpServletRequest.getSession();
        httpSession.setAttribute(telphone,otpCode);

        // 将验证码通过短信发送给用户
        System.out.println("telphone = "+telphone+"& optCode = "+otpCode);

        return CommenReturnType.create(null);
    }

    @RequestMapping("/get")
    @ResponseBody
    public CommenReturnType getUser(@RequestParam(name = "id") Integer id) throws BusinessException {
        // 根据id获取对应用户
        UserModel userModel=userService.getUserById(id);

        // 若用户不存在
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
        }

        UserVO userVO=convertFromUserModel(userModel);
        return CommenReturnType.create(userVO);
    }

    private UserVO convertFromUserModel(UserModel userModel){
        if(userModel == null)
            return null;
        UserVO userVO=new UserVO();
        BeanUtils.copyProperties(userModel,userVO);
        return userVO;
    }

}
