package com.aster.app.schedule.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * schedules.json 根对象。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleDocument(
        List<ScheduledUserMessage> schedules
) {
    public ScheduleDocument {
        schedules = schedules == null ? List.of() : List.copyOf(schedules);
    }
}
