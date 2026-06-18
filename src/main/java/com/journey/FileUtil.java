package com.journey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class FileUtil {
    private FileUtil() {}

    /** 用 try-with-resources 读 UTF-8 文本文件,自动关流 */
    public static String readUtf8(Path path) throws java.io.IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}