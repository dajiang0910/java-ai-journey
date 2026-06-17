package com.journey;

import java.util.*;
import java.util.stream.*;

public class Day3 {

    record Doc(String title, String category) {}

    public static void main(String[] args) {
        var docs = List.of(
                new Doc("报销流程", "财务"),
                new Doc("差旅审批", "财务"),
                new Doc("入职指南", "人事"),
                new Doc("年假规则", "人事"),
                new Doc("报销流程", "财务")
        );

        // 1) filter + map + collect:取出"财务"分类的所有标题
        List<String> financeTitles = docs.stream()
                .filter(d -> d.category().equals("财务"))   // 筛
                .map(Doc::title)                            // 变换:Doc -> String
                .distinct()                                 // 去重
                .toList();              // 收集成 List
        System.out.println("财务文档标题:" + financeTitles);

        // 2) groupingBy + counting:一行实现昨天的"各分类文档数"
        Map<String, Long> countByCategory = docs.stream()
                .collect(Collectors.groupingBy(Doc::category, Collectors.counting()));
        System.out.println("各分类文档数:" + countByCategory);

        // 3) joining:把所有标题拼成一句话(模拟给 LLM 拼上下文)
        String context = docs.stream()
                .map(Doc::title)
                .distinct()
                .collect(Collectors.joining("、"));
        System.out.println("知识库目录:" + context);

        // 4) 你来写:统计一共有多少个不重复的分类
        // TODO: docs.stream().map(...).distinct().count()  → long
        long categoryCount = docs.stream().map(Doc::category).distinct().count(); // 改成你的 Stream 写法
        System.out.println("分类数量:" + categoryCount);
    }
}
