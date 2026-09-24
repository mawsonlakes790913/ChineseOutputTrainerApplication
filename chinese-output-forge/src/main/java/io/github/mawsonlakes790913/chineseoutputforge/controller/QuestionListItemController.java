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
	
	// チェック状態に合わせて問題のリスト登録状態を更新
	@PostMapping("/user/question-list/item/update")
	@ResponseBody
	public void postUserQuestionListUpdate(
			@AuthenticationPrincipal UserDetails loginUser,
			@RequestParam(required = false) List<Long> selectedListIds,
		    @RequestParam Long questionId,
	        Locale locale
			) {
	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    
	    // 全チェック解除の場合は空リストとして扱う
	    if (selectedListIds == null) {
	        selectedListIds = List.of();
	    }
	    
	    // チェック状態に合わせてリストへの追加・削除を行う
	    questionListItemService.updateQuestionLists(
	    		user,
	    		questionId,
	    		selectedListIds,
	    		locale
	    		);
	}
	
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}
}
