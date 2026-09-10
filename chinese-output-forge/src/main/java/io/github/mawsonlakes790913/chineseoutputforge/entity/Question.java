package io.github.mawsonlakes790913.chineseoutputforge.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "question")
public class Question {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "question_id")
	private Long questionId;
	
	@Column(name = "language_variant", nullable = false)
	@Enumerated(EnumType.STRING)
	private LanguageVariant languageVariant;

    @Column(name = "japanese_text", nullable = false)
    private String japaneseText;

    @Column(name = "chinese_text", nullable = false)
    private String chineseText;
    
    @Column(name = "pinyin", nullable = false, columnDefinition = "TEXT")
    private String pinyin;

    @Column(name = "zhuyin", nullable = false, columnDefinition = "TEXT")
    private String zhuyin;

    @Column(name = "alternative_answer")
    private String alternativeAnswer;
    
    @Column(name = "alternative_answer_pinyin")
    private String alternativeAnswerPinyin;
    
    @Column(name = "alternative_answer_zhuyin")
    private String alternativeAnswerZhuyin;

    @Column(name = "condition")
    private String condition;
    
    @ManyToOne
    @JoinColumn(name = "structure_id", nullable = false)
    private Structure structure;

    @Column(name = "difficulty", nullable = false)
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;
    
    @Column(name = "allow_ai_variation", nullable = false)
    private Boolean allowAiVariation;
    
    @Column(name = "template")
    private String template;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = false;

    @ManyToOne
    @JoinColumn(name = "owner_user_id")
    private Users owner;

}
