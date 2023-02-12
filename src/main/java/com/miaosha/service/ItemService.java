package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.ItemModel;

import java.util.List;

public interface ItemService {

    // 创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;

    // 商品列表浏览
    List<ItemModel> listItem();

    // 商品详情页浏览
    ItemModel getItemById(Integer id);

    /**
     * mysql库存扣减；根据商品id更新商品库存表
     * @param itemId  商品id
     * @param amount  出售数量
     * @return  减库存是否成功
     * @throws BusinessException
     */
    boolean decreaseStock(Integer itemId,Integer amount) throws BusinessException;

    /**
     * 用户参与秒杀活动进行redis减库存操作（非原子操作，存在多扣库存风险）
     * @param itemId  商品id
     * @param amount  出售数量
     * @return  减库存是否成功
     * @throws BusinessException
     */
    boolean decreaseStockForPromo(Integer itemId,Integer amount) throws BusinessException;

    /**
     * 根据商品id更新商品销量
     * @param itemId  商品主键id
     * @param amount  商品数量
     * @return  是否成功
     * @throws BusinessException
     */
    boolean increaseSales(Integer itemId,Integer amount) throws BusinessException;

    /**
     * 从redis获取商品信息；如果没有，回库mysql
     * @param id  商品主键id
     * @return  商品信息
     */
    ItemModel getItemByIdFromRedis(Integer id);

    /**
     * 初始化库存流水；记录插入库存流水表；
     * 库存流水状态：1位初始化创建状态；
     * @param itemId  商品id
     * @param amount  商品数量
     * @return  库存流水记录主键
     */
    String initStockLog(Integer itemId,Integer amount);

}
