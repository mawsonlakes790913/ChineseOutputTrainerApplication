package io.github.mawsonlakes790913.chineseoutputforge.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.Data;

/**
 * 学習履歴情報の複合主キーを表すクラス。
 */
@Embeddable
@Data
public class StudyHistoryKey implements Serializable {

    private Long userId;
    private Long questionId;
}
