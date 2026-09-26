package io.github.mawsonlakes790913.chineseoutputforge.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 問題リストと問題の関連を保持するエンティティ。
 */
@Data
@Entity
@Table(name = "question_list_item")
public class QuestionListItem {
	
	@EmbeddedId
	private QuestionListItemKey questionListItemKey;
	
	@ManyToOne
	@MapsId("listId")
	@JoinColumn(name = "list_id", nullable = false)
	private QuestionList questionList;

	@ManyToOne
	@MapsId("questionId")
	@JoinColumn(name = "question_id", nullable = false)
	private Question question;
	
	@CreationTimestamp
	@Column(name = "added_at", nullable = false, updatable = false)
	private LocalDateTime addedAt;
}
