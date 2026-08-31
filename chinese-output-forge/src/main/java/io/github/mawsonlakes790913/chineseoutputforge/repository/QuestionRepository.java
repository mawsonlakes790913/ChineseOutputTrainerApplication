package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.UserQuestionListDto;
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
	
	@Query(value = """

	        SELECT
	            q.question_id         AS questionId,
	            q.japanese_text       AS japaneseText,
	            q.chinese_text        AS chineseText,
	            q.alternative_answer  AS alternativeAnswer,
	            s.name                AS structureName,
				s.description_zh_cn AS structureDescriptionZhCn,
				s.description_zh_tw AS structureDescriptionZhTw,
	            q.difficulty          AS difficulty,
	            sh.evaluation         AS evaluation,

	            CASE
	                WHEN f.question_id IS NOT NULL THEN TRUE
	                ELSE FALSE
	            END AS favorite,
	            q.pinyin                    AS pinyin,
				q.zhuyin                    AS zhuyin,
				q.alternative_answer_pinyin AS alternativeAnswerPinyin,
				q.alternative_answer_zhuyin AS alternativeAnswerZhuyin

	        FROM question q

	        JOIN structure s
	        ON q.structure_id = s.structure_id

	        LEFT JOIN study_history sh
	        ON (
	            q.question_id = sh.question_id
	            AND sh.user_id = :userId
	        )

	        LEFT JOIN favorite f
	        ON (
	            q.question_id = f.question_id
	            AND f.user_id = :userId
	        )

	        WHERE q.difficulty IN (:difficulties)

	        AND (
	            :studyCondition = 'ALL'

	            OR (
	                :studyCondition = 'LEARNED_ONLY'
	                AND sh.question_id IS NOT NULL
	                AND sh.evaluation IN (:evaluations)
	            )

	            OR (
	                :studyCondition = 'UNLEARNED_ONLY'
	                AND sh.question_id IS NULL
	            )
	        )

	        AND (
	            :favoriteCondition = 'ALL'

	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )

	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )

	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant IN (:languageVariants)
	        AND (
	            :japaneseKeyword = ''
	            OR LOWER(q.japanese_text)
	                LIKE LOWER(CONCAT('%', :japaneseKeyword, '%'))
	        )

	        AND (
	            :chineseKeyword = ''
	            OR LOWER(q.chinese_text)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	            OR LOWER(q.alternative_answer)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	        )

	        ORDER BY q.question_id ASC

	        """,

	        countQuery = """

	        SELECT COUNT(*)

	        FROM question q

	        JOIN structure s
	        ON q.structure_id = s.structure_id

	        LEFT JOIN study_history sh
	        ON (
	            q.question_id = sh.question_id
	            AND sh.user_id = :userId
	        )

	        LEFT JOIN favorite f
	        ON (
	            q.question_id = f.question_id
	            AND f.user_id = :userId
	        )

	        WHERE q.difficulty IN (:difficulties)

	        AND (
	            :studyCondition = 'ALL'

	            OR (
	                :studyCondition = 'LEARNED_ONLY'
	                AND sh.question_id IS NOT NULL
	                AND sh.evaluation IN (:evaluations)
	            )

	            OR (
	                :studyCondition = 'UNLEARNED_ONLY'
	                AND sh.question_id IS NULL
	            )
	        )

	        AND (
	            :favoriteCondition = 'ALL'

	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )

	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )

	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant IN (:languageVariants)
	        AND (
	            :japaneseKeyword = ''
	            OR LOWER(q.japanese_text)
	                LIKE LOWER(CONCAT('%', :japaneseKeyword, '%'))
	        )

	        AND (
	            :chineseKeyword = ''
	            OR LOWER(q.chinese_text)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	            OR LOWER(q.alternative_answer)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	        )

	        """,
	        nativeQuery = true)

	Page<UserQuestionListDto> findFilteredUserQuestionList(

	        @Param("userId")
	        long userId,

	        @Param("difficulties")
	        List<String> difficulties,

	        @Param("evaluations")
	        List<String> evaluations,

	        @Param("studyCondition")
	        String studyCondition,

	        @Param("favoriteCondition")
	        String favoriteCondition,

	        @Param("structureIds")
	        List<Long> structureIds,
	        
	        @Param("languageVariants")
	        List<String> languageVariants,

	        @Param("japaneseKeyword")
	        String japaneseKeyword,

	        @Param("chineseKeyword")
	        String chineseKeyword,

	        Pageable pageable
	);
	
	@Query(value = """

	        SELECT COUNT (*)
	        FROM question q
	        JOIN study_history sh
	        ON (
	            q.question_id = sh.question_id
	            AND sh.user_id = :userId
	        )
	        LEFT JOIN favorite f
	        ON (
	            q.question_id = f.question_id
	            AND f.user_id = :userId
	        )
	        WHERE q.difficulty IN (:difficulties)
	        AND sh.evaluation IN (:evaluations)
	        AND (
	            :favoriteCondition = 'ALL'
	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )
	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant = :languageVariant
	        AND q.allow_ai_variation = true
	        """,
	        nativeQuery = true)

	Long countAiGenerationSourceQuestions(

	        @Param("userId")
	        long userId,

	        @Param("difficulties")
	        List<String> difficulties,

	        @Param("evaluations")
	        List<String> evaluations,

	        @Param("favoriteCondition")
	        String favoriteCondition,

	        @Param("structureIds")
	        List<Long> structureIds,
	        
	        @Param("languageVariant")
	        String languageVariant

	);
	
	@Query(value = """

	        SELECT q.*
	        FROM question q
	        JOIN study_history sh
	        ON (
	            q.question_id = sh.question_id
	            AND sh.user_id = :userId
	        )
	        LEFT JOIN favorite f
	        ON (
	            q.question_id = f.question_id
	            AND f.user_id = :userId
	        )
	        WHERE q.difficulty IN (:difficulties)
	        AND sh.evaluation IN (:evaluations)
	        AND (
	            :favoriteCondition = 'ALL'
	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )
	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant = :languageVariant
	        AND q.allow_ai_variation = true
	        ORDER BY RANDOM()
			LIMIT 50
	        """,
	        nativeQuery = true)

	List<Question> findAiGenerationSourceQuestions(

	        @Param("userId")
	        long userId,

	        @Param("difficulties")
	        List<String> difficulties,

	        @Param("evaluations")
	        List<String> evaluations,

	        @Param("favoriteCondition")
	        String favoriteCondition,

	        @Param("structureIds")
	        List<Long> structureIds,
	        
	        @Param("languageVariant")
	        String languageVariant

	);
	
	
}
