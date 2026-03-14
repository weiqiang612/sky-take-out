package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.CategoryNotExistException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/13 16:44
 */

@Slf4j
@Service
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    /**
     * 新增菜品和对应口味
     * @param dishDTO
     * @return
     */
    @Transactional
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO,dish);
        // 创建人、创建时间、更新人、更新时间赋值已被AOP增强
        // 1. 向菜品表插入一条数据
        int rows = dishMapper.save(dish);

        if (rows != 1) {
            throw new RuntimeException("菜品插入失败！"); // 假设你有错误常量
        }

        // 2. 获取insert后返回的 ID
        Long dishId = dish.getId();

        // 如果商家将菜品的口味也填写了的话，需要将信息写到口味表，所以开启事务
        List<DishFlavor> flavors = dishDTO.getFlavors();
        int affectedFlavors = 0;
        if(flavors!=null && !flavors.isEmpty()){
            // 建立逻辑外键关联
            flavors.forEach(f -> f.setDishId(dishId));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult page(DishPageQueryDTO dishPageQueryDTO) {
        // 开启分页查询
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());

        Page<DishVO> dishPage = dishMapper.page(dishPageQueryDTO);
        return new PageResult(dishPage.getTotal(),dishPage.getResult());
    }
}
