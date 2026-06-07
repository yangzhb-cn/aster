package com.aster.app.rag.parse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 纯文本和 Markdown 解析器。
 */
public class PlainTextDocumentParser implements DocumentParser {
    @Override
    public String parse(Path sourceFile) throws IOException {
        return Files.readString(sourceFile, StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return name.endsWith(".txt")
                || name.endsWith(".md")
                || name.endsWith(".markdown")
                || type.startsWith("text/");
    }
}
