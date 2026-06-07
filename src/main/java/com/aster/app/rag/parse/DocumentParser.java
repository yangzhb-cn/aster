package com.aster.app.rag.parse;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文档解析器。
 */
public interface DocumentParser {
    /**
     * 将源文件解析成可分块的纯文本。
     */
    String parse(Path sourceFile) throws IOException;

    /**
     * 判断当前解析器是否支持该文件。
     */
    boolean supports(String fileName, String contentType);
}
