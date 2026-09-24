package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListSelectionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;

public interface QuestionListRepository
extends JpaRepository<QuestionList, Long> {
	
	// 指定したユーザーに同じ名前のリストが存在するか確認
	boolean existsByUserIdAndListName(Long userId, String listName);

	// 指定したリストが存在するか確認
	boolean existsByListId(Long listId);

	// 指定したユーザーが所有するリスト数を取得
	int countByUserId(Long userId);

	// リストIDとユーザーIDからリストを取得
	Optional<QuestionList> findByListIdAndUserId(Long listId, Long userId);

	// 指定したユーザーが所有するリストをすべて取得
	List<QuestionList> findByUserId(Long userId);
	
	// ユーザーが所有するリストと指定した問題の登録状態を取得
	@Query(value = """
			SELECT ql.list_id AS listId,
				   ql.list_name AS listName,
			       CASE
			           WHEN qli.question_id IS NOT NULL THEN true
			           ELSE false
			       END AS registered
			FROM question_list ql
			LEFT JOIN question_list_item qli
			    ON ql.list_id = qli.list_id
			    AND qli.question_id = :questionId
			WHERE ql.user_id = :userId
			ORDER BY ql.created_at DESC;
			""", nativeQuery = true)
	List<QuestionListSelectionDto> findQuestionListsWithRegistration(
	        Long userId,
	        Long questionId);
		
}
