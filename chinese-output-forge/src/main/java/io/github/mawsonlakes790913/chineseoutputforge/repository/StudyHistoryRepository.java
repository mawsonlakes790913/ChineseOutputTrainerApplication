package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistory;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistoryKey;

public interface StudyHistoryRepository extends JpaRepository<StudyHistory, StudyHistoryKey> {
	
	// 指定した学習履歴情報を取得
	Optional<StudyHistory> findByStudyHistoryKey(
	        StudyHistoryKey studyHistoryKey);

	// 指定した問題の学習履歴をすべて削除
	void deleteByStudyHistoryKeyQuestionId(
	        Long questionId);


	// 復習条件に一致する問題数を取得
	@Query(value = """
	        SELECT COUNT(*)
	        FROM study_history sh
	        JOIN question q
	        ON sh.question_id = q.question_id
	        LEFT JOIN favorite f
	        ON sh.user_id = f.user_id
	        AND sh.question_id = f.question_id
	        WHERE sh.user_id = :userId
	        AND q.language_variant IN (:languageVariants)
	        AND sh.evaluation IN (:evaluations)
	        AND q.difficulty IN (:difficulties)
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
	        AND (
	            (:sourceCondition = 'ALL'
	                AND (
	                    q.owner_user_id IS NULL
	                    OR q.owner_user_id = :userId
	                )
	            )
	            OR (
	                :sourceCondition = 'ORIGINAL_ONLY'
	                AND q.ai_generated = false
	                AND (
	                    q.owner_user_id IS NULL
	                    OR q.owner_user_id = :userId
	                )
	            )
	            OR (
	                :sourceCondition = 'GENERATED_ONLY'
	                AND q.ai_generated = true
	                AND q.owner_user_id = :userId
	            )
	        )
	        AND q.structure_id IN (:structureIds)
	        """, nativeQuery = true)
	long countReviewQuestions(
	        @Param("userId") Long userId,
	        @Param("languageVariants") List<String> languageVariants,
	        @Param("evaluations") List<String> evaluations,
	        @Param("difficulties") List<String> difficulties,
	        @Param("favoriteCondition") String favoriteCondition,
	        @Param("sourceCondition") String sourceCondition,
	        @Param("structureIds") List<Long> structureIds
	);

	// 復習条件に一致する問題をランダムに最大50件取得
	@Query(value = """
	        SELECT q.*
	        FROM study_history sh
	        JOIN question q
	          ON sh.question_id = q.question_id
	        LEFT JOIN favorite f
	          ON sh.user_id = f.user_id
	         AND sh.question_id = f.question_id
	        WHERE sh.user_id = :userId
	          AND q.language_variant IN (:languageVariants)
	          AND sh.evaluation IN (:evaluations)
	          AND q.difficulty IN (:difficulties)
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
	          AND (
	              (:sourceCondition = 'ALL'
	                  AND (
	                      q.owner_user_id IS NULL
	                      OR q.owner_user_id = :userId
	                  )
	              )
	              OR (
	                  :sourceCondition = 'ORIGINAL_ONLY'
	                  AND q.ai_generated = false
	                  AND (
	                      q.owner_user_id IS NULL
	                      OR q.owner_user_id = :userId
	                  )
	              )
	              OR (
	                  :sourceCondition = 'GENERATED_ONLY'
	                  AND q.ai_generated = true
	                  AND q.owner_user_id = :userId
	              )
	          )
	          AND q.structure_id IN (:structureIds)
	        ORDER BY RANDOM()
	        LIMIT 50
	        """, nativeQuery = true)
	List<Question> findReviewQuestions(
	        @Param("userId") Long userId,
	        @Param("languageVariants") List<String> languageVariants,
	        @Param("evaluations") List<String> evaluations,
	        @Param("difficulties") List<String> difficulties,
	        @Param("favoriteCondition") String favoriteCondition,
	        @Param("sourceCondition") String sourceCondition,
	        @Param("structureIds") List<Long> structureIds
	);
	
	// 復習条件に一致する問題をすべて取得
	@Query(value = """
	        SELECT q.*
	        FROM study_history sh
	        JOIN question q
	          ON sh.question_id = q.question_id
	        LEFT JOIN favorite f
	          ON sh.user_id = f.user_id
	         AND sh.question_id = f.question_id
	        WHERE sh.user_id = :userId
	          AND q.language_variant IN (:languageVariants)
	          AND sh.evaluation IN (:evaluations)
	          AND q.difficulty IN (:difficulties)
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
	          AND (
	              (:sourceCondition = 'ALL'
	                  AND (
	                      q.owner_user_id IS NULL
	                      OR q.owner_user_id = :userId
	                  )
	              )
	              OR (
	                  :sourceCondition = 'ORIGINAL_ONLY'
	                  AND q.ai_generated = false
	                  AND (
	                      q.owner_user_id IS NULL
	                      OR q.owner_user_id = :userId
	                  )
	              )
	              OR (
	                  :sourceCondition = 'GENERATED_ONLY'
	                  AND q.ai_generated = true
	                  AND q.owner_user_id = :userId
	              )
	          )
	          AND q.structure_id IN (:structureIds)
	        """, nativeQuery = true)
	List<Question> findAllReviewQuestions(
	        @Param("userId") Long userId,
	        @Param("languageVariants") List<String> languageVariants,
	        @Param("evaluations") List<String> evaluations,
	        @Param("difficulties") List<String> difficulties,
	        @Param("favoriteCondition") String favoriteCondition,
	        @Param("sourceCondition") String sourceCondition,
	        @Param("structureIds") List<Long> structureIds
	);

	// 指定したユーザーの学習履歴をすべて削除
	@Modifying
	@Query(value = """
	        DELETE FROM study_history
	        WHERE user_id = :userId
	        """, nativeQuery = true)
	void deleteByUserId(
	        @Param("userId") Long userId);

	
}	
