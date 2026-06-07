package com.aster.core.session;

import com.aster.core.context.ToolProtocolValidator;
import com.aster.llm.model.Message;
import com.aster.core.session.model.SessionBranch;
import com.aster.core.session.model.SessionEvent;
import com.aster.core.session.model.SessionEventType;
import com.aster.core.session.model.SessionMessageRecord;
import com.aster.core.session.model.SessionReplayResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 JSONL 事件日志回放成某个分支上的消息历史。
 *
 * <p>Session 文件保存的是事件，不是最终 messages。
 * 回放时根据 branch_created 事件找到分支父子关系，再把目标分支的可见消息还原出来。</p>
 */
public class SessionReplayer {
    public static final String MAIN_BRANCH = "main";

    /**
     * 回放目标分支。
     */
    public SessionReplayResult replay(List<SessionEvent> events, String branchId) {
        List<SessionEvent> sortedEvents = events.stream()
                .sorted(Comparator.comparingLong(SessionEvent::seq))
                .toList();
        Map<String, SessionBranch> branches = collectBranches(sortedEvents);
        List<SessionEvent> selectedMessageEvents = selectMessageEvents(
                sortedEvents,
                branches,
                branchId,
                Long.MAX_VALUE,
                new HashSet<>()
        );

        return validateOrTrim(selectedMessageEvents);
    }

    /**
     * 从事件里收集所有分支定义。
     */
    private Map<String, SessionBranch> collectBranches(List<SessionEvent> events) {
        Map<String, SessionBranch> branches = new HashMap<>();
        branches.put(MAIN_BRANCH, new SessionBranch(MAIN_BRANCH, null, 0));

        for (SessionEvent event : events) {
            if (SessionEventType.BRANCH_CREATED.value().equals(event.type())) {
                branches.put(
                        event.branchId(),
                        new SessionBranch(
                                event.branchId(),
                                event.parentBranchId(),
                                event.forkSeq() == null ? 0 : event.forkSeq()
                        )
                );
            }
        }

        return branches;
    }

    /**
     * 选择某个分支在 untilSeq 之前可见的 message_appended 事件。
     */
    private List<SessionEvent> selectMessageEvents(
            List<SessionEvent> events,
            Map<String, SessionBranch> branches,
            String branchId,
            long untilSeq,
            Set<String> visiting
    ) {
        SessionBranch branch = branches.get(branchId);
        if (branch == null) {
            throw new IllegalArgumentException("未知 session branch: " + branchId);
        }
        if (!visiting.add(branchId)) {
            throw new IllegalStateException("session branch 出现循环继承: " + branchId);
        }

        List<SessionEvent> result = new ArrayList<>();
        if (branch.parentBranchId() != null) {
            result.addAll(selectMessageEvents(
                    events,
                    branches,
                    branch.parentBranchId(),
                    branch.forkSeq(),
                    visiting
            ));
        }

        events.stream()
                .filter(event -> event.seq() <= untilSeq)
                .filter(event -> branchId.equals(event.branchId()))
                .filter(event -> SessionEventType.MESSAGE_APPENDED.value().equals(event.type()))
                .forEach(result::add);

        visiting.remove(branchId);
        return result;
    }

    /**
     * 校验工具协议；如果尾部是不完整的 tool_call，就回退到最后一个合法点。
     */
    private SessionReplayResult validateOrTrim(List<SessionEvent> messageEvents) {
        List<SessionMessageRecord> records = toRecords(messageEvents, messageEvents.size());
        List<Message> messages = toMessages(records);
        try {
            ToolProtocolValidator.validate(messages);
            return new SessionReplayResult(
                    messages,
                    records,
                    messageEvents.isEmpty() ? 0 : messageEvents.getLast().seq(),
                    false,
                    null
            );
        } catch (RuntimeException error) {
            for (int end = messageEvents.size() - 1; end >= 0; end--) {
                List<SessionMessageRecord> candidateRecords = toRecords(messageEvents, end);
                List<Message> candidate = toMessages(candidateRecords);
                try {
                    ToolProtocolValidator.validate(candidate);
                    long lastSeq = end == 0 ? 0 : messageEvents.get(end - 1).seq();
                    return new SessionReplayResult(candidate, candidateRecords, lastSeq, true, error.getMessage());
                } catch (RuntimeException ignored) {
                    // 继续向前找最后一个合法协议点。
                }
            }
            return new SessionReplayResult(List.of(), List.of(), 0, true, error.getMessage());
        }
    }

    private List<SessionMessageRecord> toRecords(List<SessionEvent> messageEvents, int endExclusive) {
        List<SessionMessageRecord> records = new ArrayList<>();
        for (int i = 0; i < endExclusive; i++) {
            SessionEvent event = messageEvents.get(i);
            Message message = event.message();
            if (message != null) {
                records.add(new SessionMessageRecord(event.seq(), event.hash(), message));
            }
        }
        return List.copyOf(records);
    }

    private List<Message> toMessages(List<SessionMessageRecord> records) {
        return records.stream()
                .map(SessionMessageRecord::message)
                .toList();
    }
}
