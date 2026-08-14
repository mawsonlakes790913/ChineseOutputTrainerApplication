package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {
	
	
	@Query(value = """
			SELECT *
			FROM question
			WHERE language_variant = :languageVariant
			AND difficulty = :difficulty
			ORDER BY question_id
			LIMIT 100 OFFSET :offset
			""", nativeQuery = true)
	List<Question> findQuestionsByLanguageVariantAndDifficulty(
			@Param("languageVariant") String languageVariant,
			@Param("difficulty") String difficulty,
			@Param("offset") int offset
	);
}
