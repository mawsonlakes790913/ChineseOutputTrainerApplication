package io.github.mawsonlakes790913.chineseoutputforge.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class QuestionListItemKey implements Serializable {
	
	private Long listId;
	private Long questionId;

}
