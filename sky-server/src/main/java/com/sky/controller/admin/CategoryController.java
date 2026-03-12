package com.sky.controller.admin;

import com.sky.dto.CategoryPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.ws.Action;

/**
 * @author 袁志刚
 * @version 1.0
 * @Date 2026/3/12 17:10
 */

@Slf4j
@RestController
@RequestMapping("/admin/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/page")
    public Result<PageResult> page(CategoryPageQueryDTO categoryPageQueryDTO) {
        log.info("分类分页查询：参数为{}", categoryPageQueryDTO);
        PageResult result = categoryService.page(categoryPageQueryDTO);
        return Result.success(result);
    }
}
