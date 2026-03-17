package com.sky.controller.user;

import com.sky.entity.Category;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/17 17:04
 */

@RestController("userCategoryController")
@RequestMapping("/user/category")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 查询分类功能，type 1 查询 菜品分类 2 查询套餐分类 不传 查询所有分类
     * @param type
     * @return
     */
    @GetMapping("/list")
    public Result<List<Category>> list(@RequestParam(required = false) Integer type){
        log.info("查询分类，type为{}",type);
        return Result.success(categoryService.list(type));
    }




}
