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

/**
 * ユーザーの問題リスト管理に関する業務処理を行うService。
 * 問題リストの作成・編集・削除・取得を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionListService {
	
	private final QuestionListRepository questionListRepository;
	private final MessageSource messageSource;
	
	/**
	 * ユーザーの問題リストを新しく作成する。
	 * 作成可能数の上限と問題リスト名の重複を確認した上で登録する。
	 *
	 * @param user ユーザー
	 * @param listName 問題リスト名
	 * @param locale 現在の言語・地域情報
	 */
	public void createQuestionList(
	        Users user,
	        String listName,
	        Locale locale) {

	    // ユーザーの権限に応じてリスト作成数の上限を設定
	    boolean isAdmin = user.getRole() == Role.ADMIN;
	    int maxLists = isAdmin ? 1000 : 30;

	    // リスト作成数が上限に達していないか確認
	    if (questionListRepository.countByUserId(user.getId()) >= maxLists) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "questionList.error.limitExceeded",
	                        null,
	                        locale));
	    }

	    // 同じ名前のリストが存在しないか確認
	    if (questionListRepository.existsByUserIdAndListName(
	            user.getId(),
	            listName)) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "questionList.error.duplicateName",
	                        null,
	                        locale));
	    }

	    // 新しい問題リストを作成してリスト情報を設定
	    QuestionList questionList = new QuestionList();
	    questionList.setUser(user);
	    questionList.setListName(listName);

	    // 問題リストを保存
	    questionListRepository.save(questionList);

	    // 問題リスト追加をログに記録
	    log.debug(
	            "リスト追加 listId={}, userId={}",
	            questionList.getListId(),
	            questionList.getUser().getId());
	}
	
	/**
	 * ユーザーが所有する問題リストの名前を変更する。
	 *
	 * @param user ユーザー
	 * @param listId 問題リストID
	 * @param listName 新しい問題リスト名
	 * @param locale 現在の言語・地域情報
	 */
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
	/**
	 * ユーザーが所有する問題リストを削除する。
	 *
	 * @param user ユーザー
	 * @param listId 問題リストID
	 * @param locale 現在の言語・地域情報
	 */
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
	
	/**
	 * ユーザーが所有する問題リストを取得する。
	 *
	 * @param user ユーザー
	 * @return ユーザーが所有する問題リストの一覧
	 */
	public List<QuestionList> getQuestionLists(Users user) {

	    List<QuestionList> questionLists =
	            questionListRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());

	    return questionLists;
	}
	
	/**
	 * ユーザーが所有する問題リストと指定した問題の登録状態を取得する。
	 *
	 * @param user ユーザー
	 * @param questionId 問題ID
	 * @return 問題リストと問題の登録状態の一覧
	 */
	public List<QuestionListSelectionDto> getQuestionListSelection(
	        Users user,
	        Long questionId) {
		
		return questionListRepository
		        .findQuestionListsWithRegistration(
		                user.getId(),
		                questionId);
		
	}

}
