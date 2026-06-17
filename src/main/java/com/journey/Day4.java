package com.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Day4 {

    record Doc(String title, String category) {}

    private static final Logger log = LoggerFactory.getLogger(Day4.class);

    public static void main(String[] args) {
        try {
            List<Doc> docs = loadDocs(Path.of("src/main/resources/docs.json"));
            log.info("成功加载 {} 篇文档", docs.size());
            docs.forEach(d -> log.debug("  - {} ({})", d.title(), d.category()));
        } catch (DataLoadException e) {
            log.error("加载文档失败:{}", e.getMessage(), e);  // 第2个参是异常堆栈
        }
    }

    static List<Doc> loadDocs(Path path) throws DataLoadException {
        // try-with-resources:流自动关,出错也关,不用 finally
        try (var reader = Files.newBufferedReader(path)) {
            return new ObjectMapper().readValue(reader, new TypeReference<List<Doc>>() {});
        } catch (Exception e) {   // 统一转成业务异常
            throw new DataLoadException("无法读取/解析文件: " + path, e);
        }
    }

    // 自定义业务异常:承载原始异常,上层统一处理
    static class DataLoadException extends Exception {
        DataLoadException(String msg, Throwable cause) { super(msg, cause); }
    }
}