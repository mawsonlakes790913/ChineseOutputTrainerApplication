package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Role;
import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListSelectionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionListService {
	
	private final QuestionListRepository questionListRepository;
	private final MessageSource messageSource;
	
	// リスト新規追加
	public void createQuestionList(
			Users user,
			String listName,
			Locale locale) {
		
		// リスト作成数上限に達していないか確認
		boolean isAdmin = user.getRole() == Role.ADMIN;
		int maxLists = isAdmin ? 1000 : 30;
		
		if(questionListRepository.countByUserId(user.getId()) >= maxLists) {
			throw new IllegalArgumentException(
		            messageSource.getMessage(
		                    "questionList.error.limitExceeded",
		                    null,
		                    locale));
		}
		
		// 同じ名前のリストがあるか確認
		if(questionListRepository.existsByUserIdAndListName(user.getId(), listName)) {
			throw new IllegalArgumentException(
		            messageSource.getMessage(
		                    "questionList.error.duplicateName",
		                    null,
		                    locale));
		}
		
		// リスト作成
		QuestionList questionList = new QuestionList();
		
		questionList.setUser(user);
		questionList.setListName(listName);
		
		questionListRepository.save(questionList);
		
        log.debug("リスト追加 listId={}, userId={}",
        		questionList.getListId(), questionList.getUser().getId());
		
		
	}
	
	// リスト名変更
	public void editQuestionList(
			Users user,
			Long listId,
			String listName,
			Locale locale) {
				
		// 既存のリストを取得
		QuestionList questionList =
		        questionListRepository
		                .findByListIdAndUserId(listId, user.getId())
		                .orElseThrow(() -> new IllegalArgumentException(
		                        messageSource.getMessage(
		                                "questionList.error.notFound",
		                                null,
		                                locale)));
		
		// 同じ名前のリストがあるか確認
		if(questionListRepository.existsByUserIdAndListName(user.getId(), listName)) {
			throw new IllegalArgumentException(
		            messageSource.getMessage(
		                    "questionList.error.duplicateName",
		                    null,
		                    locale));
		}
		
		// リスト名変更
		questionList.setListName(listName);
		questionListRepository.save(questionList);
		
        log.debug("リスト名変更 listId={}, userId={}, listName={}",
        		questionList.getListId(), 
        		questionList.getUser().getId(),
        		questionList.getListName());
		
	}
	
	// リスト削除
	@Transactional
	public void deleteQuestionList(
			Users user,
			Long listId,
			Locale locale) {
		
		// 既存のリストを取得
		QuestionList questionList =
		        questionListRepository
		                .findByListIdAndUserId(listId, user.getId())
		                .orElseThrow(() -> new IllegalArgumentException(
		                        messageSource.getMessage(
		                                "questionList.error.notFound",
		                                null,
		                                locale)));
		
		// リストを削除
		questionListRepository.delete(questionList);
		
        log.debug("リスト削除 listId={}, userId={}",
        		questionList.getListId(), 
        		questionList.getUser().getId());
		
	}
	
	// リスト一覧取得
	public List<QuestionList> getQuestionLists(Users user) {

	    List<QuestionList> questionLists =
	            questionListRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());

	    return questionLists;
	}
	
	// ユーザーが所有するリストと指定した問題の登録状態を取得
	public List<QuestionListSelectionDto> getQuestionListSelection(
	        Users user,
	        Long questionId) {
		
		return questionListRepository
		        .findQuestionListsWithRegistration(
		                user.getId(),
		                questionId);
		
	}

}
