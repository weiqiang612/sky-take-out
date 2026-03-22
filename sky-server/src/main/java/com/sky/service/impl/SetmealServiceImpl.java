package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/17 18:10
 */

@Service
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 条件查询
     *
     * @param setmeal
     * @return
     */
    @Override
    @Cacheable(cacheNames = "setmealCache",
            key = "#setmeal.categoryId",
            condition = "#setmeal.categoryId != null", // 只有传了分类ID才缓存
            unless = "#result == null || #result.size() == 0") // 如果没查到结果不缓存
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据套餐ID查询相关菜品
     *
     * @param id
     * @return
     */
    @Override
    public List<DishItemVO> listDishItem(Long id) {
        List<DishItemVO> dishItemVOS = setmealDishMapper.listSetmealDish(id);
        return dishItemVOS;
    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult page(SetmealPageQueryDTO setmealPageQueryDTO) {
        // 开启分页查询
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> result = setmealMapper.page(setmealPageQueryDTO);
        return new PageResult(result.getTotal(), result.getResult());
    }

    /**
     * 新增套餐和套餐对应菜品
     *
     * @param setmealDTO
     */
    // 开启缓存
    @CacheEvict(cacheNames = "setmealCache", key = "#setmealDTO.categoryId")
    @Transactional
    @Override
    public void saveWithSetmealDishes(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 1. 插入套餐表
        setmealMapper.save(setmeal);
        Long setmealId = setmeal.getId();
        // 2. 主键回填
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && setmealDishes.size() > 0) {
            for (SetmealDish setmealDish : setmealDishes) {
                setmealDish.setSetmealId(setmealId);
            }
        }
        // 3. 将对应菜品加入套餐菜品表
        setmealDishMapper.saveSetmealDishes(setmealDishes);
    }

    /**
     * 修改套餐，并修改套餐菜品映射表
     *
     * @param setmealDTO
     */
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    @Transactional
    @Override
    public void updateWithDishes(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 修改套餐表
        setmealMapper.update(setmeal);
        // 删除套餐原有菜品
        List<Long> ids = Collections.singletonList(setmealDTO.getId());
        setmealDishMapper.deleteSetmealDishesBySetmealIds(ids);
        // 新增套餐菜品
        // 手动给套餐对应菜品套餐ID赋值
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && setmealDishes.size() > 0) {
            for (SetmealDish setmealDish : setmealDishes) {
                setmealDish.setSetmealId(setmealDTO.getId());
            }
        }
        setmealDishMapper.saveSetmealDishes(setmealDishes);
    }

    /**
     * 根据套餐ID查询套餐
     *
     * @param id
     * @return
     */
    @Override
    public SetmealVO getSetmealById(Long id) {
        // 查询套餐基本信息
        SetmealVO setmealVO = setmealMapper.getById(id);
        // 查询套餐下的菜品基本信息
        List<SetmealDish> setmealDishes = setmealDishMapper.listDetailed(id);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 修改套餐起售停售状态
     *
     * @param status
     * @param id
     */
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    @Override
    public void updateStatus(Integer status, Long id) {
        Setmeal setmeal = Setmeal.builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 批量删除套餐
     */
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    @Transactional
    @Override
    public void removeBatch(List<Long> ids) {
        // 1. 对于套餐的删除操作，首先要确保套餐在停售状态，否则不予删除
        Integer count = setmealMapper.countEnabledSetmealByIds(ids);
        if (count > 0) {
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        }

        // 2. 删除套餐之后，还需要相应的把相应的套餐菜品映射删除

        // 先删除菜品套餐映射，再删除套餐表
        setmealDishMapper.deleteSetmealDishesBySetmealIds(ids);
        setmealMapper.deleteBatch(ids);

    }
}
