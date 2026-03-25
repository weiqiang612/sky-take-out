package com.sky.service.impl;

import com.sky.mapper.OrdersMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/25 13:06
 */

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrdersMapper ordersMapper;

    /**
     * 营业额统计
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        // 1. 用于存放最终结果的容器
        List<LocalDate> dateList = new ArrayList<>();
        List<Double> turnoverList = new ArrayList<>();

        // 2. 调用 Mapper 获取大 Map
        // 结构如：{2026-03-22={amount=13.00, orderDate=2026-03-22}}
        Map<String, Map<String, Object>> statsMap = ordersMapper.turnoverStatistics(begin, end);

        // 3. 核心遍历：从 begin 循环到 end
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);
            String dateStr = date.toString(); // 生成 "2026-03-22"
            if (statsMap.containsKey(dateStr)) {
                Object amountObj = statsMap.get(dateStr).get("amount");
                turnoverList.add(Double.valueOf(amountObj.toString()));
            } else {
                // 不包含该日期营业额就设为0
                turnoverList.add(0.0);
            }
        }

        // 4. 使用 StringJoiner 或 StringUtils 拼接成逗号分隔的字符串

        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }
}
