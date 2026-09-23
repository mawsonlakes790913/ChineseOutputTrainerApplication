package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListItemService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class QuestionListItemController {
	
	private final UserAccountService userAccountService;
	private final QuestionListItemService questionListItemService;
	
	// リストに問題を追加
	@PostMapping("/user/question-list/item/add")
	public String postQuestionListItemAdd(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        @RequestParam Long questionId,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定した問題をリストに追加
	    questionListItemService.addQuestionToList(
	            user,
	            listId,
	            questionId,
	            locale);

	    // リスト一覧画面へ戻る
	    return "redirect:/user/question-list/list";
	}
	
	// リストから問題を削除
	@PostMapping("/user/question-list/item/delete")
	public String postQuestionListItemDelete(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        @RequestParam Long questionId,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定した問題をリストから削除
	    questionListItemService.deleteQuestionFromList(
	            user,
	            listId,
	            questionId,
	            locale);

	    // リスト一覧画面へ戻る
	    return "redirect:/user/question-list/list";
	}
	
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}
}
