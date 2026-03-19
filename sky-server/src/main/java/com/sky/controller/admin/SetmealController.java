package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/18 16:52
 */

@RestController
@RequestMapping("/admin/setmeal")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("套餐分页查询：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.page(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据套餐ID查询套餐
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<SetmealVO> getSetmealById(@PathVariable Long id) {
        log.info("根据套餐ID查询套餐：{}",id);
        SetmealVO setmealVO = setmealService.getSetmealById(id);
        return Result.success(setmealVO);
    }

    /**
     * 新增套餐
     * @param setmealDTO
     * @return
     */
    @PostMapping
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        setmealService.saveWithSetmealDishes(setmealDTO);
        return Result.success();
    }

    /**
     * 修改套餐，并修改套餐菜品映射表
     * @param setmealDTO
     * @return
     */
    @PutMapping
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐：{}", setmealDTO);
        setmealService.updateWithDishes(setmealDTO);
        return Result.success();
    }

    /**
     * 修改套餐起售停售状态
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    public Result updateStatus(@PathVariable Integer status,@RequestParam Long id){
        log.info("修改套餐售卖状态为：{}",status == 1 ? "起售" : "停售");
        setmealService.updateStatus(status,id);
        return Result.success();
    }

    /**
     * 批量删除
     * @param ids
     * @return
     */
    @DeleteMapping
    public Result removeBatch(@RequestParam List<Long> ids){
        log.info("管理端进行批量删除操作，ids:{}",ids);
        setmealService.removeBatch(ids);
        return Result.success();
    }

}
