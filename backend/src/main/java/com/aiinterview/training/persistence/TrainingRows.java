package com.aiinterview.training.persistence;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

public final class TrainingRows {
    @TableName("training_session")
    public static class Session {
        @TableId(type = IdType.INPUT) public String id;
        public String ownerId;
        public String mode;
        public Long roleId;
        public String questionSnapshot;
        public Long sourceQuestionId;
        public String sourceAnswerId;
        public String sourceStrategy;
        public int position;
        public int roundNumber;
        public String state;
        public String phase;
        public long effectiveMs;
        public long roundMs;
        public long pausedMs;
        public long asrMs;
        public long evaluationMs;
        public long activeAt;
        public long heartbeatAt;
        public long pausedAt;
        public long asrAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) public String asrTicket;
        public String draft;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) public String draftAudioId;
        public long createdAt;
        public long endedAt;
    }

    @TableName("training_answer")
    public static class Answer {
        @TableId(type = IdType.INPUT) public String id;
        public String sessionId;
        public Long questionId;
        public int position;
        public int roundNumber;
        public String requestKey;
        public String answerText;
        public String audioId;
        public long effectiveMs;
        public String evaluationState;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) public String evaluationTicket;
        public long evaluationStartedAt;
        public String feedbackJson;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) public String failureReason;
        public long createdAt;
    }
}
