package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/13 17:37
 */

@Slf4j
@RestController
@RequestMapping("/admin/common")
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 利用阿里云OSS服务完成文件的上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("文件上传：{}", file.getOriginalFilename());
        // 在Bucket上的储存路径
        // 目录
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 文件名 ： UUID + 文件后缀名
        String newFileName = UUID.randomUUID() + file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
        String objectName = dir + "/" + newFileName;
        try {
            String upload = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(upload);
        } catch (IOException e) {
            log.error("文件上传失败，{}",e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}
