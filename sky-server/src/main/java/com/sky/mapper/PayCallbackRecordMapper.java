package com.sky.mapper;

import com.sky.entity.PayCallbackRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayCallbackRecordMapper {

    int insert(PayCallbackRecord record);
}
