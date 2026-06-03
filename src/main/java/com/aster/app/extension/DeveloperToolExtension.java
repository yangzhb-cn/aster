package com.aster.app.extension;

import com.aster.app.tool.developer.DeveloperTools;

import java.nio.file.Path;

/**
 * 开发者工具扩展。
 *
 * <p>负责把 ls、glob、grep、subagent、web_fetch、web_search 注册到运行时工具表。
 * 这些工具是扩展能力，不属于四个基础内置工具。</p>
 */
public class DeveloperToolExtension implements AsterRuntimeExtension {
    /**
     * 按当前项目的 RuntimeExtension 方式注册开发者工具。
     */
    @Override
    public void registerTools(RuntimeExtensionContext context) {
        DeveloperTools.registerAll(
                context.toolRegistry(),
                Path.of("."),
                context.objectMapper(),
                context.httpClient(),
                context.provider(),
                context.streamingChatClient(),
                true
        );
    }
}
