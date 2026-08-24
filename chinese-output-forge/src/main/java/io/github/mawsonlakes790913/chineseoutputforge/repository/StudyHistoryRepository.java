package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistory;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistoryKey;

public interface StudyHistoryRepository extends JpaRepository<StudyHistory, StudyHistoryKey> {
	
	Optional<StudyHistory> findByStudyHistoryKey(StudyHistoryKey studyHistoryKey);
	
	@Query(value = """
	        SELECT COUNT(*)
	        FROM study_history sh
	        JOIN question q
	        ON sh.question_id = q.question_id
	        LEFT JOIN favorite f
	        ON sh.user_id = f.user_id
	        AND sh.question_id = f.question_id
	        WHERE sh.user_id = :userId
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
	        AND q.structure_id IN (:structureIds)
	        """, nativeQuery = true)
	long countReviewQuestions(
	        @Param("userId") Long userId,
	        @Param("evaluations") List<String> evaluations,
	        @Param("difficulties") List<String> difficulties,
	        @Param("favoriteCondition") String favoriteCondition,
	        @Param("structureIds") List<Long> structureIds
	);
	
	@Query(value = """
	        SELECT q.*
	        FROM study_history sh
	        JOIN question q
	          ON sh.question_id = q.question_id
	        LEFT JOIN favorite f
	          ON sh.user_id = f.user_id
	         AND sh.question_id = f.question_id
	        WHERE sh.user_id = :userId
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
	        """, nativeQuery = true)
	List<Question> findReviewQuestions(
	        @Param("userId") Long userId,
	        @Param("evaluations") List<String> evaluations,
	        @Param("difficulties") List<String> difficulties,
	        @Param("favoriteCondition") String favoriteCondition
	);

	
}	
