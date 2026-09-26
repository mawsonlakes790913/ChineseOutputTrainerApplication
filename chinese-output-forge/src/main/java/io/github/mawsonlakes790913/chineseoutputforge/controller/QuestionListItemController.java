package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListItemService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;

/**
 * 問題リストへの問題の登録管理に関するリクエストを処理するController。
 * 問題の追加・削除、および複数の問題リストへの登録状態の更新を行う。
 */
@Controller
@RequiredArgsConstructor
public class QuestionListItemController {
	
	private final UserAccountService userAccountService;
	private final QuestionListItemService questionListItemService;
	
	/**
	 * 指定された問題を問題リストから削除する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param listId 削除元のリストID
	 * @param questionId 削除する問題のID
	 * @param locale 現在の言語・地域情報
	 * @return 問題リスト一覧画面へのリダイレクト先
	 */
	@PostMapping("/user/question-list/item/delete")
	public String postQuestionListItemDelete(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        @RequestParam Long questionId,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定した問題を問題リストから削除
	    questionListItemService.deleteQuestionFromList(
	            user,
	            listId,
	            questionId,
	            locale);

	    return "redirect:/user/question-list/list";
	}
	
	/**
	 * 指定された問題のリスト登録状態を、選択されたリストに合わせて更新する。
	 * リストが1つも選択されていない場合は、すべてのリストから登録を解除する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param selectedListIds 問題を登録するリストのID一覧
	 * @param questionId 登録状態を更新する問題のID
	 * @param locale 現在の言語・地域情報
	 */
	@PostMapping("/user/question-list/item/update")
	@ResponseBody
	public void postUserQuestionListUpdate(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(required = false) List<Long> selectedListIds,
	        @RequestParam Long questionId,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 全てのチェックが外れている場合は空のリストとして扱う
	    if (selectedListIds == null) {
	        selectedListIds = List.of();
	    }

	    // 選択状態に合わせて問題リストへの追加・削除を行う
	    questionListItemService.updateQuestionLists(
	            user,
	            questionId,
	            selectedListIds,
	            locale);
	}
	
	/**
	 * ログインユーザー情報からUsersを取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @return ログイン中のUsers
	 */
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}
}
