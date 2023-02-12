package com.miaosha.service.impl;

import com.miaosha.dao.UserDOMapper;
import com.miaosha.dao.UserPasswordDOMapper;
import com.miaosha.dataobject.UserDO;
import com.miaosha.dataobject.UserPasswordDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EmBusinessError;
import com.miaosha.service.UserService;
import com.miaosha.service.model.UserModel;
import com.miaosha.validator.ValidationResult;
import com.miaosha.validator.ValidatorImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;
    @Autowired
    UserPasswordDOMapper userPasswordDOMapper;

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public UserModel getUserById(Integer id) {
        UserDO userDO=userDOMapper.selectByPrimaryKey(id);
        if(userDO == null){
            return null;
        }
        // 根据用户id获取用户密码
        UserPasswordDO userPasswordDO=userPasswordDOMapper.selectByUserId(userDO.getId());

        return convertFromUserDO(userDO,userPasswordDO);
    }

    @Override
    public UserModel getUserByIdFromRedis(Integer id) {
        UserModel userModel= (UserModel) redisTemplate.opsForValue().get("user_"+id);
        if(userModel == null){
            userModel=getUserById(id);
            redisTemplate.opsForValue().set("user_"+id,userModel);
            redisTemplate.expire("user_"+id,10,TimeUnit.MINUTES);
        }
        return userModel;
    }

    // 用户注册事务
    @Override
    @Transactional
    public void register(UserModel userModel) throws BusinessException {
        // 数据校验
        if(userModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        ValidationResult validationResult=validator.validate(userModel);
        if(validationResult.isHasErr()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,validationResult.getErrMsg());
        }
        // 插入注册信息
        UserDO userDO=convertFromUserModel(userModel);

        userDOMapper.insertSelective(userDO);
        // 获取自增主键
        userModel.setId(userDO.getId());

        UserPasswordDO userPasswordDO=convertUserPasswordFromUserModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);

    }

    @Override
    public UserModel login(String telphone, String encrptPassword) throws BusinessException {
        // 通过手机号获取用户信息
        UserDO userDO=userDOMapper.selectByTelphone(telphone);
        if(userDO == null){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        UserPasswordDO userPasswordDO=userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel=convertFromUserDO(userDO,userPasswordDO);
        // 比对密码
        if(!com.alibaba.druid.util.StringUtils.equals(userModel.getEncrptPassword(),encrptPassword)){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        return userModel;
    }

    private UserPasswordDO convertUserPasswordFromUserModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserPasswordDO userPasswordDO=new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }

    private UserDO convertFromUserModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserDO userDO=new UserDO();
        BeanUtils.copyProperties(userModel,userDO);

        return userDO;
    }

    private UserModel convertFromUserDO(UserDO userDO, UserPasswordDO userPasswordDO){
        if(userDO == null){
            return null;
        }
        UserModel userModel=new UserModel();
        BeanUtils.copyProperties(userDO,userModel);

        if(userPasswordDO != null)
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());

        return userModel;
    }

}
