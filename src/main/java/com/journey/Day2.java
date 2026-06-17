package com.journey;

import java.util.*;

public class Day2 {

    // 模拟知识库里的文档:标题 + 所属分类
    record Doc(String title, String category) {}

    public static void main(String[] args) {
        var docs = List.of(
                new Doc("报销流程", "财务"),
                new Doc("差旅审批", "财务"),
                new Doc("入职指南", "人事"),
                new Doc("年假规则", "人事"),
                new Doc("报销流程", "财务")   // 故意重复,后面用 Set 体会去重
        );

        // 1) Map 统计:每个分类有几篇文档
        var countByCategory = new HashMap<String, Integer>();
        for (var d : docs) {
            // TODO: 用 getOrDefault 累加。提示:put(key, getOrDefault(key,0)+1)
            countByCategory.put(d.category, countByCategory.getOrDefault(d.category, 0) + 1);
        }
        System.out.println("各分类文档数:" + countByCategory);

        // 2) Set 去重:一共有哪些不重复的标题
        var titles = new HashSet<String>();
        for (var d : docs) {
            titles.add(d.title());
        }
        System.out.println("去重后的标题:" + titles);

        // 3) Optional:按标题查找第一篇文档,可能查不到
        System.out.println(findByTitle(docs, "报销流程").map(Doc::category).orElse("未找到"));
        System.out.println(findByTitle(docs, "不存在的").map(Doc::category).orElse("未找到"));
    }

    static Optional<Doc> findByTitle(List<Doc> docs, String title) {
        for (var d : docs) {
            if (d.title().equals(title)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();   // 没找到,返回空 Optional,而不是 null
    }
}