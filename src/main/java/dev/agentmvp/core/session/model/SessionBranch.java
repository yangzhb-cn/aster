package dev.agentmvp.core.session.model;

/**
 * 一条 session 分支。
 *
 * <p>分支不是复制完整消息列表，而是记录 parentBranchId 和 forkSeq。
 * 回放时先回放父分支到 forkSeq，再叠加当前分支自己的消息。</p>
 */
public record SessionBranch(
        String branchId,
        String parentBranchId,
        long forkSeq
) {
}
