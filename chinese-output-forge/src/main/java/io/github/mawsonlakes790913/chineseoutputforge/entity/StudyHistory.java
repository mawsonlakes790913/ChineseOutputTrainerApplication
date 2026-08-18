package io.github.mawsonlakes790913.chineseoutputforge.entity;

import java.time.LocalDateTime;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "study_history")
public class StudyHistory {

    @EmbeddedId
    private StudyHistoryKey studyHistoryKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Evaluation evaluation;

    @Column(nullable = false)
    private LocalDateTime evaluationUpdatedAt;
}