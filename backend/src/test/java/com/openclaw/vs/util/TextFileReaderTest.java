package com.openclaw.vs.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFileReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsUtf8Chinese() throws Exception {
        Path f = tempDir.resolve("utf8.md");
        String text = "今日日记：你好，长期记忆。";
        Files.writeString(f, text);
        assertEquals(text, TextFileReader.readString(f));
    }

    @Test
    void readsGbkWhenNotUtf8() throws Exception {
        Path f = tempDir.resolve("gbk.md");
        String text = "中文记忆 GBK 日记内容";
        Files.write(f, text.getBytes(Charset.forName("GBK")));
        assertEquals(text, TextFileReader.readString(f));
    }

    @Test
    void picksUtf8FileWhenSavedAsUtf8() throws Exception {
        Path f = tempDir.resolve("diary.md");
        String text = "关于老板：测试日记内容\n\n- 条目一";
        Files.writeString(f, text);
        assertEquals(text, TextFileReader.readString(f));
    }
}
