package io.github.mawsonlakes790913.chineseoutputforge.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class StudyHistoryKey implements Serializable {

    private Long userId;

    private Long questionId;
}
