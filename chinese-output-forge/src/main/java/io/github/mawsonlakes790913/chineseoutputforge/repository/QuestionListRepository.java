package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
		
}
