package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
