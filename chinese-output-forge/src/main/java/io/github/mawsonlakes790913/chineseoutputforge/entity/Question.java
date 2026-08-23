package io.github.mawsonlakes790913.chineseoutputforge.entity;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.SubjectType;
import io.github.mawsonlakes790913.chineseoutputforge.constant.VerbVariation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
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
    
    @Column(name = "structure", nullable = false)
    private String structure;

    @Column(name = "difficulty", nullable = false)
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;
    
    @Column(name = "allow_ai_variation", nullable = false)
    private Boolean allowAiVariation;
    
    @Column(name = "template")
    private String template;
    
    @Column(name = "subject_type")
    @Enumerated(EnumType.STRING)
    private SubjectType subjectType;
    
    @Column(name = "verb_variation")
    @Enumerated(EnumType.STRING)
    private VerbVariation verbVariation;

}
