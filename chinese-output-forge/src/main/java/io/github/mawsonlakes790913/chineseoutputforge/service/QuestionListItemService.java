package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListItemDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListSelectionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionListItem;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionListItemKey;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionListItemRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionListRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionListItemService {
	
	private final QuestionListRepository questionListRepository;
	private final QuestionListItemRepository questionListItemRepository;
	private final QuestionRepository questionRepository;
	private final MessageSource messageSource;
	private final QuestionListService questionListService;
	
	
	
	public void addQuestionToList(
			Users user,
			Long listId,
			Long questionId,
			Locale locale) {
		
		// ユーザーが所有するリストを取得
		QuestionList questionList =
		        questionListRepository
		                .findByListIdAndUserId(listId, user.getId())
		                .orElseThrow(() -> new IllegalArgumentException(
		                        messageSource.getMessage(
		                                "questionList.error.notFound",
		                                null,
		                                locale)));
		
		// リスト内の問題数が上限に達していないか確認
		if (questionListItemRepository
		        .countByQuestionListItemKeyListId(listId) >= 1000) {

		    throw new IllegalArgumentException(
		            messageSource.getMessage(
		                    "questionListItem.error.limitExceeded",
		                    null,
		                    locale));
		}    
		
		// 追加対象の問題を取得
		Question question =
		        questionRepository
		                .findByQuestionId(questionId)
		                .orElseThrow(() -> new IllegalArgumentException(
		                        messageSource.getMessage(
		                                "question.error.notFound",
		                                null,
		                                locale)));
		
		// 同じ問題がリストに登録済みでないか確認
		if (questionListItemRepository
		        .existsByQuestionListItemKeyListIdAndQuestionListItemKeyQuestionId(
		                listId,
		                questionId)) {

		    throw new IllegalArgumentException(
		            messageSource.getMessage(
		                    "questionListItem.error.duplicateItem",
		                    null,
		                    locale));
		}
		
		// リストIDと問題IDから複合主キーを作成
		QuestionListItemKey key = new QuestionListItemKey();
		key.setListId(listId);
		key.setQuestionId(questionId);
		
		// リストと問題の紐付けを作成
		QuestionListItem questionListItem = new QuestionListItem();
		questionListItem.setQuestionListItemKey(key);
		questionListItem.setQuestionList(questionList);
		questionListItem.setQuestion(question);
		
		// リストに問題を追加
		questionListItemRepository.save(questionListItem);

		// リストの更新日時を更新
		questionList.setUpdatedAt(LocalDateTime.now());
		questionListRepository.save(questionList);
		
		log.debug("問題をリストへ追加 listId={}, userId={}, questionId={}",
				questionList.getListId(), 
				questionList.getUser().getId(),
				question.getQuestionId());
		
	}
	
	@Transactional
	public void deleteQuestionFromList(
			Users user,
			Long listId,
			Long questionId,
			Locale locale) {
		
	    // ユーザーが所有するリストか確認
		QuestionList questionList =
		    questionListRepository
		            .findByListIdAndUserId(listId, user.getId())
		            .orElseThrow(() -> new IllegalArgumentException(
		                    messageSource.getMessage(
		                            "questionList.error.notFound",
		                            null,
		                            locale)));
		
		// リストIDと問題IDから複合主キーを作成
		QuestionListItemKey key = new QuestionListItemKey();
		key.setListId(listId);
		key.setQuestionId(questionId);
		
		// リストから問題を削除
		questionListItemRepository.deleteByQuestionListItemKey(key);
		
		// リストの更新日時を更新
		questionList.setUpdatedAt(LocalDateTime.now());
		questionListRepository.save(questionList);
		
	    log.debug("問題をリストから削除 listId={}, userId={}, questionId={}",
	            listId,
	            user.getId(),
	            questionId);
		
	}
	
	public List<QuestionListItemDto> getQuestionListItems(
	        Users user,
	        Long listId,
	        Locale locale) {

	    // ユーザーが所有するリストか確認
	    questionListRepository
	            .findByListIdAndUserId(listId, user.getId())
	            .orElseThrow(() -> new IllegalArgumentException(
	                    messageSource.getMessage(
	                            "questionList.error.notFound",
	                            null,
	                            locale)));

	    // リストに登録されている問題をDTOで取得
	    List<QuestionListItemDto> questionListItems =
	            questionListItemRepository.findQuestionListItems(
	                    listId,
	                    user.getId());

	    return questionListItems;
	}
	
	@Transactional
	public void updateQuestionLists(
	        Users user,
	        Long questionId,
	        List<Long> selectedListIds,
	        Locale locale) {

	    // 現在のリスト登録状態を取得
	    List<QuestionListSelectionDto> questionListSelections =
	            questionListService.getQuestionListSelection(
	                    user,
	                    questionId);

	    for (QuestionListSelectionDto questionListSelection : questionListSelections) {

	        // 未登録からチェックありになった場合は追加
	        if (!questionListSelection.getRegistered()
	                && selectedListIds.contains(questionListSelection.getListId())) {

	            addQuestionToList(
	                    user,
	                    questionListSelection.getListId(),
	                    questionId,
	                    locale);
	        }

	        // 登録済みからチェックなしになった場合は削除
	        if (questionListSelection.getRegistered()
	                && !selectedListIds.contains(questionListSelection.getListId())) {

	            deleteQuestionFromList(
	                    user,
	                    questionListSelection.getListId(),
	                    questionId,
	                    locale);
	        }
	    }
	}
}
