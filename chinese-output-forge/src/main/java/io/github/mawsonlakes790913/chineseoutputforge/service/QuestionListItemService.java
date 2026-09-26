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

/**
 * 問題リストへの問題の登録管理に関する業務処理を行うService。
 * 問題の追加・削除・取得、および複数の問題リストへの登録状態の更新を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionListItemService {
	
	private final QuestionListRepository questionListRepository;
	private final QuestionListItemRepository questionListItemRepository;
	private final QuestionRepository questionRepository;
	private final MessageSource messageSource;
	private final QuestionListService questionListService;
	
	/**
	 * 指定した問題をユーザーが所有する問題リストに追加する。
	 * 問題数の上限および重複登録を確認した上で追加し、
	 * 問題リストの更新日時を更新する。
	 *
	 * @param user ユーザー
	 * @param listId 問題リストID
	 * @param questionId 問題ID
	 * @param locale 現在の言語・地域情報
	 */
	public void addQuestionToList(
			Users user,
			Long listId,
			Long questionId,
			Locale locale) {
		
		// ユーザーが所有する問題リストを取得
		QuestionList questionList =
		        questionListRepository
		                .findByListIdAndUserId(listId, user.getId())
		                .orElseThrow(() -> new IllegalArgumentException(
		                        messageSource.getMessage(
		                                "questionList.error.notFound",
		                                null,
		                                locale)));
		
		// 問題リスト内の問題数が上限に達していないか確認
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
		
		// 同じ問題が問題リストに登録済みでないか確認
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
		
		// 問題リストと問題の紐付けを作成
		QuestionListItem questionListItem = new QuestionListItem();
		questionListItem.setQuestionListItemKey(key);
		questionListItem.setQuestionList(questionList);
		questionListItem.setQuestion(question);
		
		// 問題リストに問題を追加
		questionListItemRepository.save(questionListItem);

		// 問題リストの更新日時を更新
		questionList.setUpdatedAt(LocalDateTime.now());
		questionListRepository.save(questionList);
		
		log.debug("問題をリストへ追加 listId={}, userId={}, questionId={}",
				questionList.getListId(), 
				questionList.getUser().getId(),
				question.getQuestionId());
		
	}
	
	/**
	 * 指定した問題をユーザーが所有する問題リストから削除する。
	 * 削除後、問題リストの更新日時を更新する。
	 *
	 * @param user ユーザー
	 * @param listId 問題リストID
	 * @param questionId 問題ID
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void deleteQuestionFromList(
			Users user,
			Long listId,
			Long questionId,
			Locale locale) {
		
	    // ユーザーが所有する問題リストか確認
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
		
		// 問題リストから問題を削除
		questionListItemRepository.deleteByQuestionListItemKey(key);
		
		// 問題リストの更新日時を更新
		questionList.setUpdatedAt(LocalDateTime.now());
		questionListRepository.save(questionList);
		
	    log.debug("問題をリストから削除 listId={}, userId={}, questionId={}",
	            listId,
	            user.getId(),
	            questionId);
		
	}
	
	/**
	 * 指定した問題リストに登録されている問題を取得する。
	 *
	 * @param user ユーザー
	 * @param listId 問題リストID
	 * @param locale 現在の言語・地域情報
	 * @return 問題リストに登録されている問題の一覧
	 */
	public List<QuestionListItemDto> getQuestionListItems(
	        Users user,
	        Long listId,
	        Locale locale) {

	    // ユーザーが所有する問題リストか確認
	    questionListRepository
	            .findByListIdAndUserId(listId, user.getId())
	            .orElseThrow(() -> new IllegalArgumentException(
	                    messageSource.getMessage(
	                            "questionList.error.notFound",
	                            null,
	                            locale)));

	    // 問題リストに登録されている問題をDTOで取得
	    List<QuestionListItemDto> questionListItems =
	            questionListItemRepository.findQuestionListItems(
	                    listId,
	                    user.getId());

	    return questionListItems;
	}
	
	/**
	 * 指定した問題の複数の問題リストへの登録状態を更新する。
	 * 現在の登録状態と選択された問題リストを比較し、
	 * 必要に応じて問題の追加・削除を行う。
	 *
	 * @param user ユーザー
	 * @param questionId 問題ID
	 * @param selectedListIds 登録対象として選択された問題リストIDの一覧
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void updateQuestionLists(
	        Users user,
	        Long questionId,
	        List<Long> selectedListIds,
	        Locale locale) {

	    // 現在の問題リスト登録状態を取得
	    List<QuestionListSelectionDto> questionListSelections =
	            questionListService.getQuestionListSelection(
	                    user,
	                    questionId);
	    // 各問題リストの選択状態に応じて登録状態を更新
	    for (QuestionListSelectionDto questionListSelection : questionListSelections) {
	    	// 新たに選択された問題リストに追加
	        if (!questionListSelection.getRegistered()
	                && selectedListIds.contains(questionListSelection.getListId())) {
	            addQuestionToList(
	                    user,
	                    questionListSelection.getListId(),
	                    questionId,
	                    locale);
	        }

	        // 選択解除された問題リストから削除
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
