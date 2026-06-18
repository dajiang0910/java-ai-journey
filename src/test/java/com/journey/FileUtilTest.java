package com.journey;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileUtilTest {
    @Test
    void readUtf8_file_returnContext() throws Exception {
        Path tmp = Files.createTempFile("test", ".txt");
        Files.writeString(tmp, "line1\nline2", StandardCharsets.UTF_8);
        String result = FileUtil.readUtf8(tmp);
        assertEquals("line1\nline2", result);
    }
}
