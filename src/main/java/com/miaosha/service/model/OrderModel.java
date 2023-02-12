package com.miaosha.service.model;

import java.math.BigDecimal;

/**
 * 下单交易模型
 */
public class OrderModel {

    // 订单号；遵循一定规则
    private String id;

    private Integer userId;

    private Integer itemId;

    // 购买物品数量
    private Integer amount;

    // 若非空则表示秒杀商品
    private Integer promoId;

    // 购买商品单价；若promoId非空，则表示秒杀商品价格；
    private BigDecimal itemPrice;

    // 购买金额
    private BigDecimal amountOfMoney;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public BigDecimal getAmountOfMoney() {
        return amountOfMoney;
    }

    public void setAmountOfMoney(BigDecimal amountOfMoney) {
        this.amountOfMoney = amountOfMoney;
    }

    public Integer getAmount() {
        return amount;
    }

    public BigDecimal getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(BigDecimal itemPrice) {
        this.itemPrice = itemPrice;
    }

    public Integer getPromoId() {
        return promoId;
    }

    public void setPromoId(Integer promoId) {
        this.promoId = promoId;
    }
}
