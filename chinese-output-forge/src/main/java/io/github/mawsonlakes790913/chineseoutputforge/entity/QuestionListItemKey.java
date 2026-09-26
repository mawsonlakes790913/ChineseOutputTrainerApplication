package io.github.mawsonlakes790913.chineseoutputforge.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.Data;

/**
 * 問題リスト内の問題情報の複合主キーを表すクラス。
 */
@Embeddable
@Data
public class QuestionListItemKey implements Serializable {
	
	private Long listId;
	private Long questionId;
}
