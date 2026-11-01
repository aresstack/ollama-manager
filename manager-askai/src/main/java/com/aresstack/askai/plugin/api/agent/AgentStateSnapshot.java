package com.aresstack.askai.plugin.api.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A generic, immutable read-only view of an agent session's state for the UI (composer, status line, a future
 * state visualization). It is deliberately domain-agnostic: phase and run state are opaque display strings, so
 * the generic API never learns research-specific enums. The plugin builds a snapshot from its own state model.
 */
public final class AgentStateSnapshot {

    private final String phaseLabel;
    private final String runStateLabel;
    private final boolean busy;
    private final boolean pendingApproval;
    private final String pendingApprovalId;
    private final long revision;
    private final String statusLine;
    private final List<String> allowedCommandNames;

    private AgentStateSnapshot(Builder b) {
        this.phaseLabel = b.phaseLabel == null ? "" : b.phaseLabel;
        this.runStateLabel = b.runStateLabel == null ? "" : b.runStateLabel;
        this.busy = b.busy;
        this.pendingApproval = b.pendingApproval;
        this.pendingApprovalId = b.pendingApprovalId;
        this.revision = b.revision;
        this.statusLine = b.statusLine == null ? "" : b.statusLine;
        this.allowedCommandNames = Collections.unmodifiableList(
                new ArrayList<String>(b.allowedCommandNames));
    }

    public String getPhaseLabel() {
        return phaseLabel;
    }

    public String getRunStateLabel() {
        return runStateLabel;
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean hasPendingApproval() {
        return pendingApproval;
    }

    /** @return the id of the pending approval, or {@code null} if none is pending. */
    public String getPendingApprovalId() {
        return pendingApprovalId;
    }

    public long getRevision() {
        return revision;
    }

    public String getStatusLine() {
        return statusLine;
    }

    public List<String> getAllowedCommandNames() {
        return allowedCommandNames;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String phaseLabel;
        private String runStateLabel;
        private boolean busy;
        private boolean pendingApproval;
        private String pendingApprovalId;
        private long revision;
        private String statusLine;
        private final List<String> allowedCommandNames = new ArrayList<String>();

        public Builder phaseLabel(String v) {
            this.phaseLabel = v;
            return this;
        }

        public Builder runStateLabel(String v) {
            this.runStateLabel = v;
            return this;
        }

        public Builder busy(boolean v) {
            this.busy = v;
            return this;
        }

        public Builder pendingApproval(boolean v) {
            this.pendingApproval = v;
            return this;
        }

        public Builder pendingApprovalId(String v) {
            this.pendingApprovalId = v;
            return this;
        }

        public Builder revision(long v) {
            this.revision = v;
            return this;
        }

        public Builder statusLine(String v) {
            this.statusLine = v;
            return this;
        }

        public Builder allowedCommandNames(List<String> v) {
            this.allowedCommandNames.clear();
            if (v != null) {
                this.allowedCommandNames.addAll(v);
            }
            return this;
        }

        public AgentStateSnapshot build() {
            return new AgentStateSnapshot(this);
        }
    }
}
