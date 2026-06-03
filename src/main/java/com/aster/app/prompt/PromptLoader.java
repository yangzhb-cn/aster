package com.aster.app.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 读取 Markdown prompt。
 *
 * <p>教学版先只支持 jar 内置资源，不做外部文件覆盖。
 * 后续如果要支持用户自定义 prompt，可以在这个类外面再包一层 PromptRepository。</p>
 */
public class PromptLoader {
    /**
     * 读取一个 classpath 资源。
     *
     * <p>resourcePath 应该类似 {@code /prompts/agent/system.md}。
     * 不要传 {@code src/main/resources/...}，因为 jar 运行时没有源码目录。</p>
     */
    public String load(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Prompt resource path is blank");
        }

        try (InputStream input = PromptLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Prompt resource not found: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
