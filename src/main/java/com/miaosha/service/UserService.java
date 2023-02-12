package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.UserModel;

public interface UserService {

    /**
     * 根据用户主键获取用户信息；查询用户表，用户密码表
     * @param id  用户主键id
     * @return  用户信息
     */
    UserModel getUserById(Integer id);

    /**
     * 从redis中通过用户id获取用户对象；没有则回库mysql
     * @param id  用户id
     * @return  用户信息
     */
    UserModel getUserByIdFromRedis(Integer id);

    void register(UserModel userModel) throws BusinessException;

    UserModel login(String telphone,String encrptPassword) throws BusinessException;

}
