package com.aster.app.room;

import java.util.regex.Pattern;

/**
 * 房间模块使用的文件名安全校验。
 */
final class RoomIdValidator {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,96}");

    private RoomIdValidator() {
    }

    /**
     * 校验 ID，避免路径穿越和难读文件名。
     */
    static void requireSafeId(String id, String name) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " 只能包含字母、数字、点、下划线、短横线，长度 1 到 96");
        }
    }
}
