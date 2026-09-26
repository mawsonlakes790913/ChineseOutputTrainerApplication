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

/**
 * ユーザーの問題ごとの学習履歴情報のDB操作を行うRepository。
 */
public interface StudyHistoryRepository extends JpaRepository<StudyHistory, StudyHistoryKey> {
	
    /**
     * 指定した学習履歴情報を取得する。
     *
     * @param studyHistoryKey 学習履歴情報の複合主キー
     * @return 学習履歴情報
     */
	Optional<StudyHistory> findByStudyHistoryKey(StudyHistoryKey studyHistoryKey);

    /**
     * 指定した問題の学習履歴をすべて削除する。
     *
     * @param questionId 問題ID
     */
	void deleteByStudyHistoryKeyQuestionId(Long questionId);


    /**
     * 指定した復習条件に一致する問題数を取得する。
     *
     * @param userId ユーザーID
     * @param languageVariants 学習対象言語の検索条件
     * @param evaluations 理解度の検索条件
     * @param difficulties 難易度の検索条件
     * @param favoriteCondition お気に入りの検索条件
     * @param sourceCondition 問題の生成元の検索条件
     * @param structureIds 文法・構造の検索条件
     * @return 復習条件に一致する問題数
     */
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

    /**
     * 指定した復習条件に一致する問題をランダムに最大50件取得する。
     *
     * @param userId ユーザーID
     * @param languageVariants 学習対象言語の検索条件
     * @param evaluations 理解度の検索条件
     * @param difficulties 難易度の検索条件
     * @param favoriteCondition お気に入りの検索条件
     * @param sourceCondition 問題の生成元の検索条件
     * @param structureIds 文法・構造の検索条件
     * @return 復習条件に一致する問題の一覧
     */
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
	
    /**
     * 指定した復習条件に一致する問題をすべて取得する。
     *
     * @param userId ユーザーID
     * @param languageVariants 学習対象言語の検索条件
     * @param evaluations 理解度の検索条件
     * @param difficulties 難易度の検索条件
     * @param favoriteCondition お気に入りの検索条件
     * @param sourceCondition 問題の生成元の検索条件
     * @param structureIds 文法・構造の検索条件
     * @return 復習条件に一致するすべての問題
     */
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

    /**
     * 指定したユーザーの学習履歴をすべて削除する。
     *
     * @param userId ユーザーID
     */
	@Modifying
	@Query(value = """
	        DELETE FROM study_history
	        WHERE user_id = :userId
	        """, nativeQuery = true)
	void deleteByUserId(@Param("userId") Long userId);

}	
