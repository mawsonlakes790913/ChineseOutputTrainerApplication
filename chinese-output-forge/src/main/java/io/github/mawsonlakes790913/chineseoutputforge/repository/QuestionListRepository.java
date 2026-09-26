package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListSelectionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;

/**
 * ユーザーが作成した問題リスト情報のDB操作を行うRepository。
 */
public interface QuestionListRepository
extends JpaRepository<QuestionList, Long> {
	
    /**
     * 指定したユーザーに同じ名前の問題リストが存在するか確認する。
     *
     * @param userId ユーザーID
     * @param listName 問題リスト名
     * @return 同じ名前の問題リストが存在する場合はtrue
     */
	boolean existsByUserIdAndListName(Long userId, String listName);

    /**
     * 指定した問題リストが存在するか確認する。
     *
     * @param listId 問題リストID
     * @return 問題リストが存在する場合はtrue
     */
	boolean existsByListId(Long listId);

    /**
     * 指定したユーザーが所有する問題リスト数を取得する。
     *
     * @param userId ユーザーID
     * @return ユーザーが所有する問題リスト数
     */
	int countByUserId(Long userId);

    /**
     * 問題リストIDとユーザーIDから問題リストを取得する。
     *
     * @param listId 問題リストID
     * @param userId ユーザーID
     * @return 条件に一致する問題リスト
     */
	Optional<QuestionList> findByListIdAndUserId(Long listId, Long userId);
	
    /**
     * 指定したユーザーが所有する問題リストを更新日時の降順で取得する。
     *
     * @param userId ユーザーID
     * @return ユーザーが所有する問題リストの一覧
     */
	List<QuestionList> findByUserIdOrderByUpdatedAtDesc(Long userId);
	
    /**
     * 指定したユーザーが所有する問題リストと、指定した問題の登録状態を取得する。
     *
     * @param userId ユーザーID
     * @param questionId 問題ID
     * @return 問題リストと問題の登録状態の一覧
     */
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
			ORDER BY ql.updated_at DESC;
			""", nativeQuery = true)
	List<QuestionListSelectionDto> findQuestionListsWithRegistration(
	        Long userId,
	        Long questionId);
		
}
