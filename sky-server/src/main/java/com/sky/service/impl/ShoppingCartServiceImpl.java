package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/19 17:58
 */

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     */
    @Transactional
    @Override
    public void save(ShoppingCartDTO shoppingCartDTO) {
        // 同时传入菜品和套餐，这种情况不可能，不可以一次添加两个
        if (shoppingCartDTO.getDishId() != null && shoppingCartDTO.getSetmealId() != null) {
            throw new ShoppingCartBusinessException("不可以一次添加多个菜品或套餐！");
        }
        Long userId = BaseContext.getCurrentId();
        // 1. 判断添加的菜品或套餐是否已经添加过
        ShoppingCart shoppingCart1 = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart1);
        shoppingCart1.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart1);
        // 2. 当前种类菜品或套餐已经存在，则只更新数量即可
        if (list != null && !list.isEmpty()) {
            ShoppingCart cart = list.get(0);
            // 若已存在套餐或菜品，则可按其id添加
            shoppingCartMapper.updateNumberForGoods(cart.getNumber() + 1, cart.getId());
            return;
        }

        // 2. 当前种类菜品或套餐数量大于0，则只更新数量即可
        // 3. 当前购物车中没有当前添加的套餐或菜品，则新插入表
        // 3.1 添加的是菜品，则按需添加菜品口味(条件插入即可)
        ShoppingCart shoppingCart = null;
        // 填充公共字段
        ShoppingCart.ShoppingCartBuilder builder = ShoppingCart.builder()
                .number(1)
                .createTime(LocalDateTime.now());
        if (shoppingCartDTO.getDishId() != null) {
            DishVO dishVO = dishMapper.getById(shoppingCartDTO.getDishId());
            shoppingCart = builder
                    .dishFlavor(shoppingCartDTO.getDishFlavor())
                    .dishId(shoppingCartDTO.getDishId())
                    .name(dishVO.getName())
                    .image(dishVO.getImage())
                    .userId(userId)
                    .amount(dishVO.getPrice())
                    .build();
        } else if (shoppingCartDTO.getSetmealId() != null) {  // 3.2 添加的是套餐
            SetmealVO setmealVO = setmealMapper.getById(shoppingCartDTO.getSetmealId());
            shoppingCart = builder
                    .name(setmealVO.getName())
                    .image(setmealVO.getImage())
                    .userId(userId)
                    .setmealId(shoppingCartDTO.getSetmealId())
                    .amount(setmealVO.getPrice())
                    .build();
        }
        if (shoppingCart != null) {
            shoppingCartMapper.save(shoppingCart);
        }
    }
}
