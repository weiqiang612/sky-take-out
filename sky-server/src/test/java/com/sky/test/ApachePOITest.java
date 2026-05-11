package com.sky.test;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/26 17:45
 */


public class ApachePOITest {

    /**
     * 通过POI创建Excel文件并写入
     * @throws Exception
     */
    public static void write() throws Exception {
        // 1. 在内存中创建一个Excel文件
        XSSFWorkbook excel = new XSSFWorkbook();
        // 2. 创建一个sheet
        XSSFSheet sheet = excel.createSheet("info");
        // 3. 创建一个row (索引是从0开始的)
        XSSFRow row = sheet.createRow(0);
        // 4. 创建一个cell (索引是从0开始的)
        row.createCell(0).setCellValue("姓名");
        row.createCell(1).setCellValue("性别");

        row = sheet.createRow(1);
        row.createCell(0).setCellValue("袁志刚");
        row.createCell(1).setCellValue("男");

        // 5. 写入
        FileOutputStream outputStream = new FileOutputStream("D:\\info.xlsx");
        // 6. 输出到外存中
        excel.write(outputStream);
        // 7. 关闭资源
        excel.close();
        outputStream.close();
    }

    /**
     * 通过POI读取Excel
     * @throws Exception
     */
    public static void read() throws Exception {
        FileInputStream inputStream = new FileInputStream("D:\\info.xlsx");
        XSSFWorkbook excel = new XSSFWorkbook(inputStream);
        XSSFSheet sheet = excel.getSheetAt(0);
        for (int row = 0; row <= sheet.getLastRowNum(); row++) {
            String s1 = sheet.getRow(row).getCell(0).getStringCellValue();
            String s2 = sheet.getRow(row).getCell(1).getStringCellValue();
            System.out.println(s1 + " " + s2);
        }

        excel.close();
        inputStream.close();
    }


    public static void main(String[] args) throws Exception {
        read();
    }

}
