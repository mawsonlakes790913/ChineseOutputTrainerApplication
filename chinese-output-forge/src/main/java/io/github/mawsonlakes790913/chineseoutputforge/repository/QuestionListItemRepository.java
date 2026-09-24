package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListItemDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionListItem;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionListItemKey;

public interface QuestionListItemRepository
extends JpaRepository<QuestionListItem, QuestionListItemKey> {
	
	// 指定したリストに問題が登録されているか確認
	boolean existsByQuestionListItemKeyListIdAndQuestionListItemKeyQuestionId(
	        Long listId,
	        Long questionId);

	// リストIDと問題IDからリスト項目を取得
	QuestionListItem findByQuestionListItemKeyListIdAndQuestionListItemKeyQuestionId(
	        Long listId,
	        Long questionId);
	
	// 指定したリストに登録されている問題をすべて取得
	List<QuestionListItem> findByQuestionListItemKeyListId(Long listId);

	// 複合主キーを指定してリストから問題を削除
	void deleteByQuestionListItemKey(QuestionListItemKey key);
	
	// 指定したリストに登録されている問題数を取得
	int countByQuestionListItemKeyListId(Long listId);
	
	// 指定した問題リストに登録されている問題を取得
	@Query(value = """
	        SELECT
	            q.question_id                  AS questionId,
	            q.chinese_text                 AS chineseText,
	            q.japanese_text                AS japaneseText,
	            q.difficulty                   AS difficulty,
	            sh.evaluation                  AS evaluation,
	            CASE
	                WHEN f.question_id IS NOT NULL THEN TRUE
	                ELSE FALSE
	            END                            AS favorite,
	            q.ai_generated                 AS aiGenerated,
	            q.pinyin                       AS pinyin,
	            q.zhuyin                       AS zhuyin,
	            q.alternative_answer           AS alternativeAnswer,
	            q.alternative_answer_pinyin    AS alternativeAnswerPinyin,
	            q.alternative_answer_zhuyin    AS alternativeAnswerZhuyin,
	            s.name                         AS structureName

	        FROM question_list_item qli

	        JOIN question q
	            ON qli.question_id = q.question_id

	        JOIN structure s
	            ON q.structure_id = s.structure_id

	        LEFT JOIN study_history sh
	            ON q.question_id = sh.question_id
	            AND sh.user_id = :userId

	        LEFT JOIN favorite f
	            ON q.question_id = f.question_id
	            AND f.user_id = :userId

	        WHERE qli.list_id = :listId

	        ORDER BY qli.added_at DESC
	        """,
	        nativeQuery = true)
	List<QuestionListItemDto> findQuestionListItems(
	        @Param("listId") Long listId,
	        @Param("userId") Long userId);
}
