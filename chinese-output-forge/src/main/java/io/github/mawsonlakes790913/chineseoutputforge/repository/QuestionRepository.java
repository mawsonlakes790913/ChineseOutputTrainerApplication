package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {
	
	long countByLanguageVariantAndDifficulty(
			LanguageVariant languageVariant,
			Difficulty difficulty
	);
	
	
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
	
	@Query(value = """
			SELECT COUNT(*)
			FROM question q
			LEFT JOIN study_history sh
			  ON q.question_id = sh.question_id
			 AND sh.user_id = :userId
			WHERE q.difficulty IN (:difficulties)
			  AND sh.question_id IS NULL
			""", nativeQuery = true)
			long countNewQuestions(
				    @Param("userId") Long userId,
				    @Param("difficulties") String difficulties					
					);
	
	@Query(value = """
			SELECT q.*
			FROM question q
			LEFT JOIN study_history sh
			  ON q.question_id = sh.question_id
			 AND sh.user_id = :userId
			WHERE q.difficulty IN (:difficulties)
			  AND sh.question_id IS NULL
			""", nativeQuery = true)
			List<Question> findUnlearnedQuestionsByUserIdAndDifficulty(
				    @Param("userId") Long userId,
				    @Param("difficulties") List<String> difficulties					
					);
}
