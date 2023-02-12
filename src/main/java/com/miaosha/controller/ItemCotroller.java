package com.miaosha.controller;

import com.miaosha.controller.viewobject.ItemVO;
import com.miaosha.error.BusinessException;
import com.miaosha.response.CommenReturnType;
import com.miaosha.service.GuavaCacheService;
import com.miaosha.service.ItemService;
import com.miaosha.service.PromoService;
import com.miaosha.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/item")
@CrossOrigin(allowCredentials="true",allowedHeaders="*")
public class ItemCotroller {

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private GuavaCacheService guavaCacheService;
    @Autowired
    private PromoService promoService;

    // 商品列表浏览
    @RequestMapping(value = "/list",method = RequestMethod.GET)
    @ResponseBody
    public CommenReturnType listItem(){
        List<ItemModel> itemModels=itemService.listItem();
        List<ItemVO> itemVOS=itemModels.stream().map(itemModel -> {
            ItemVO itemVO=convertFromItemModel(itemModel);
            return itemVO;
        }).collect(Collectors.toList());

        return CommenReturnType.create(itemVOS);
    }

    //
    @RequestMapping(value = "/publishPromo",method = RequestMethod.GET)
    @ResponseBody
    public CommenReturnType publishPromo(@RequestParam("id") Integer id) {
        promoService.publishPromo(id);

        return CommenReturnType.create(null);
    }

    // 商品详情浏览
    @RequestMapping(value = "/get",method = RequestMethod.GET)
    @ResponseBody
    public CommenReturnType getItem(@RequestParam("id") Integer id) {
        // 从本地缓存取
        ItemModel itemModel = (ItemModel) guavaCacheService.getFromCommonCache("item_"+id);
        if(itemModel == null){
            // 根据商品id从redis中获取
            itemModel= (ItemModel) redisTemplate.opsForValue().get("item_"+id);
            // 如果redis中没有，则从service中获取,并存入redis
            if(itemModel == null){
                itemModel=itemService.getItemById(id);
                redisTemplate.opsForValue().set("item_"+id,itemModel);
                redisTemplate.expire("item_"+id,10,TimeUnit.MINUTES);
            }
            // 加入本地缓存
            guavaCacheService.setCommonCache("item_"+id,itemModel);
        }

        ItemVO itemVO=convertFromItemModel(itemModel);

        return CommenReturnType.create(itemVO);
    }

    // 创建商品接口
    @RequestMapping(value = "/createItem",method = RequestMethod.POST,consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public CommenReturnType createItem(@RequestParam("title") String title,
                                       @RequestParam("price") BigDecimal price,
                                       @RequestParam("stock") Integer stock,
                                       @RequestParam("description") String description,
                                       @RequestParam("imgUrl") String imgUrl) throws BusinessException {
        ItemModel itemModel=new ItemModel();
        itemModel.setStock(stock);
        itemModel.setPrice(price);
        itemModel.setDescription(description);
        itemModel.setImgUrl(imgUrl);
        itemModel.setTitle(title);

        ItemModel itemModel1Return=itemService.createItem(itemModel);

        ItemVO itemVO=convertFromItemModel(itemModel1Return);

        return CommenReturnType.create(itemVO);
    }

    private ItemVO convertFromItemModel(ItemModel itemModel){
        if (itemModel == null){
            return null;
        }
        ItemVO itemVO=new ItemVO();
        BeanUtils.copyProperties(itemModel,itemVO);

        if(itemModel.getPromoModel() != null){
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            // 时间格式转化
            itemVO.setPromoStartTime(itemModel.getPromoModel().getStartTime().
                    toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));

            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else {
            itemVO.setPromoStatus(0);
        }

        return itemVO;
    }



}
