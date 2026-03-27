package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.transaction.Transaction;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
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
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private WorkspaceService workspaceService;

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

    /**
     * 用户统计
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        ArrayList<LocalDate> localDateList = new ArrayList<>();
        ArrayList<Integer> totalUserList = new ArrayList<>();
        ArrayList<Integer> newUserList = new ArrayList<>();

        // 1. 获得从开始到结束的所有日期
        LocalDate tempBegin = begin;
        while (!tempBegin.isAfter(end)) {
            localDateList.add(tempBegin);
            tempBegin = tempBegin.plusDays(1);
        }
        // 2. 遍历每天，得到当天的用户总量和新增用户量
        for (LocalDate date : localDateList) {
            // date这天0:00
            LocalDateTime dateLeft = LocalDateTime.of(date, LocalTime.MIN);
            // date这天23:59
            LocalDateTime dateRight = LocalDateTime.of(date, LocalTime.MAX);

            // 当天的用户总量
            Integer sum = userMapper.countUser(null, dateRight);
            totalUserList.add(sum);
            // 当天新增用户总量
            Integer newNum = userMapper.countUser(dateLeft, dateRight);
            newUserList.add(newNum);
        }
        return UserReportVO.builder()
                .dateList(StringUtils.join(localDateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    /**
     * 订单统计
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO ordersStatistics(LocalDate begin, LocalDate end) {
        // 日期列表
        ArrayList<LocalDate> localDateList = new ArrayList<>();
        // 每日订单数
        ArrayList<Integer> orderCountList = new ArrayList<>();
        // 每日完成(有效)订单数
        ArrayList<Integer> validOrderCountList = new ArrayList<>();
        // 订单总数
        Integer totalOrderCount = 0;
        // 有效订单数
        Integer validOrderCount = 0;
        // 订单完成率
        Double orderCompletionRate = 0.0;

        // 1. 获得从开始到结束的所有日期
        LocalDate tempBegin = begin;
        while (!tempBegin.isAfter(end)) {
            localDateList.add(tempBegin);
            tempBegin = tempBegin.plusDays(1);
        }

        // 2. 遍历每天，获取当日数据
        for (LocalDate date : localDateList) {
            // 当日的0:00
            LocalDateTime dateLeft = LocalDateTime.of(date, LocalTime.MIN);
            // 当日的23:59
            LocalDateTime dateRight = LocalDateTime.of(date, LocalTime.MAX);
            // 获取当日的订单数
            Integer orderCountDaily = ordersMapper.countByTime(dateLeft, dateRight, null);
            // 以及有效订单数
            Integer validOrderCountDaily = ordersMapper.countByTime(dateLeft, dateRight, 5);
            orderCountList.add(orderCountDaily);
            validOrderCountList.add(validOrderCountDaily);
        }
        // 3. 获取选定时间内的之前的订单总数
        LocalDateTime beginLeft = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endRight = LocalDateTime.of(end, LocalTime.MAX);
        totalOrderCount = ordersMapper.countByTime(beginLeft, endRight, null);
        // 4. 获取选定时间内的有效订单数
        validOrderCount = ordersMapper.countByTime(beginLeft, endRight, 5);
        // 5. 计算订单完成率
        if (totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }
        // 6. 返回数据
        return OrderReportVO.builder()
                .dateList(StringUtils.join(localDateList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .build();
    }

    /**
     * top10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO top10(LocalDate begin, LocalDate end) {
        ArrayList<String> nameList = new ArrayList<>();
        ArrayList<Integer> numberList = new ArrayList<>();

        // 1. 对指定时间内的已完成的这些订单中的菜品排序
        LocalDateTime beginLeft = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endRight = LocalDateTime.of(end, LocalTime.MAX);
        List<LinkedHashMap<String, Object>> map = orderDetailMapper.top(beginLeft, endRight);
        for (LinkedHashMap<String, Object> linkedHashMap : map) {
            // 1. 获取名称
            nameList.add((String) linkedHashMap.get("name"));
            // 2. 获取数量：先转 String，再转 Integer
            // 这样无论数据库返回的是 BigDecimal 还是 Long，都能成功转换
            Object numberObj = linkedHashMap.get("number");
            Integer number = 0;
            if (numberObj != null) {
                number = Integer.valueOf(numberObj.toString());
            }
            numberList.add(number);
        }
        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();
    }

    /**
     * 导出Excel报表
     */
    @Override
    public void export(HttpServletResponse response) throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        XSSFWorkbook excel = null;
        try {
            if (inputStream != null) {
                excel = new XSSFWorkbook(inputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 1. 查询数据库中营业数据 -- 查询最近30天的数据
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        BusinessDataVO businessData = workspaceService.getBusinessData(begin.atTime(LocalTime.MIN), end.atTime(LocalTime.MAX));

        // 1.1 查询近30天的营业额
        Double turnover = businessData.getTurnover();
        // 1.2 查询有效订单数
        Integer validOrderCount = businessData.getValidOrderCount();
        // 1.3 订单完成率
        Double orderCompletionRate = businessData.getOrderCompletionRate();
        // 1.4 计算平均客单价
        Double averageTransactionPrice = businessData.getUnitPrice();
        // 1.5 查询新增用户数
        Integer newUsers = businessData.getNewUsers();
        // 填充概览数据
        XSSFSheet sheet = null;
        if (excel != null) {
            sheet = excel.getSheetAt(0);
            // 时间
            sheet.getRow(1).getCell(1).setCellValue("时间：" + begin + "至" + end);
            // 营业额
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(turnover);
            row.getCell(4).setCellValue(orderCompletionRate);
            row.getCell(6).setCellValue(newUsers);
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(validOrderCount);
            row.getCell(4).setCellValue(averageTransactionPrice);
        } else {
            throw new RuntimeException("Excel错误！");
        }

        // 1.6 明细数据，每日的以上五个点
        ArrayList<LocalDate> localDateList = new ArrayList<>();
        // 得到中间所有日期
        LocalDate tempBegin = begin;
        while (!tempBegin.isAfter(end)) {
            localDateList.add(tempBegin);
            tempBegin = tempBegin.plusDays(1);
        }
        // 遍历中间日，得到营业数据，并写入Excel
        for (int i = 0; i < localDateList.size(); i++) {
            LocalDate date = localDateList.get(i);
            LocalDateTime dateTimeLeft = date.atTime(LocalTime.MIN);
            LocalDateTime dateTimeRight = date.atTime(LocalTime.MAX);
            BusinessDataVO data = workspaceService.getBusinessData(dateTimeLeft, dateTimeRight);
            // 将这日数据写入Excel
            XSSFRow row = sheet.getRow(i + 7);
            row.getCell(1).setCellValue(String.valueOf(date));
            row.getCell(2).setCellValue(data.getTurnover());
            row.getCell(3).setCellValue(data.getValidOrderCount());
            row.getCell(4).setCellValue(data.getOrderCompletionRate());
            row.getCell(5).setCellValue(data.getUnitPrice());
            row.getCell(6).setCellValue(data.getNewUsers());
        }

        // 2. 使用输出流下载到用户的浏览器上
        excel.write(response.getOutputStream());
        excel.close();
        inputStream.close();
    }
}
