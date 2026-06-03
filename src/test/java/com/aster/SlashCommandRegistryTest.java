package com.aster;

import com.aster.ui.tui.command.SessionCommand;
import com.aster.ui.tui.command.SlashCommandRegistry;
import com.aster.ui.tui.command.StopCommand;
import com.aster.ui.tui.command.TeamCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TUI 斜杠命令注册表测试。
 */
class SlashCommandRegistryTest {
    /**
     * 验证默认命令注册表可以筛选菜单并定位处理命令。
     */
    @Test
    void findsDefaultSlashCommandsAndVisibleOptions() {
        SlashCommandRegistry registry = SlashCommandRegistry.defaults();

        assertTrue(registry.visibleOptions("/").size() >= 4);
        assertTrue(registry.visibleOptions("/ste").stream()
                .anyMatch(option -> option.insertText().equals("/steer ")));
        assertTrue(registry.visibleOptions("/session use").stream()
                .anyMatch(option -> option.insertText().equals("/session use ")));
        assertTrue(registry.visibleOptions("/te").stream()
                .anyMatch(option -> option.insertText().equals("/team ")));

        assertInstanceOf(StopCommand.class, registry.find("/stop").orElseThrow());
        assertInstanceOf(TeamCommand.class, registry.find("/team 探索当前架构").orElseThrow());
        assertInstanceOf(SessionCommand.class, registry.find("/session current").orElseThrow());
        assertEquals(true, registry.find("/unknown").isEmpty());
    }
}
