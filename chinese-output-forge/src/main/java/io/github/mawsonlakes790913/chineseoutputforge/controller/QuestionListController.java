package io.github.mawsonlakes790913.chineseoutputforge.controller;


import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListItemService;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class QuestionListController {
	
	private final QuestionListService questionListService;
	private final UserAccountService userAccountService;
	private final QuestionListItemService questionListItemService;
	private final MessageSource messageSource;
	
	// ユーザーの問題リスト一覧を表示
	@GetMapping("/user/question-list/list")
	public String getUserQuestionListList(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model) {

	    // ログインユーザーを取得
	    Users user =
	            userAccountService.getUserOne(loginUser.getUsername());

	    // ユーザーが所有するリストをすべて取得
	    List<QuestionList> questionLists =
	            questionListService.getQuestionLists(user);

	    // リスト一覧を画面に渡す
	    model.addAttribute("questionLists", questionLists);

	    return "/user/question-list/list";
	}
	
	// リストを新規作成する
	@PostMapping("/user/question-list/create")
	public String postUserQuestionListCreate(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam String listName,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    try {

	        // 新しいリストを作成
	        questionListService.createQuestionList(
	                user,
	                listName,
	                locale);

	        // 作成完了メッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "successMessage",
	                messageSource.getMessage(
	                        "questionList.success.create",
	                        null,
	                        locale));

	    } catch (IllegalArgumentException e) {

	        // エラーメッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());
	    }

	    // リスト一覧画面へ戻る
	    return "redirect:/user/question-list/list";
	}
	
	// リストの詳細を取得（動作確認用の途中段階）
	@GetMapping("/user/question-list/detail")
	@ResponseBody
	public List<Question> getUserQuestionListDetail(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定したリストに登録されている問題をすべて取得
	    List<Question> listItems =
	            questionListItemService.getQuestionListItems(
	                    user,
	                    listId,
	                    locale);

	    // リストの問題一覧をJSON形式で返す
	    return listItems;
	}
	
	// リストを削除
	@PostMapping("/user/question-list/delete")
	public String postUserQuestionListDelete(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    try {

	        // 指定したリストを削除
	        questionListService.deleteQuestionList(
	                user,
	                listId,
	                locale);

	        // 削除完了メッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "successMessage",
	                messageSource.getMessage(
	                        "questionList.success.delete",
	                        null,
	                        locale));

	    } catch (IllegalArgumentException e) {

	        // エラーメッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());
	    }

	    // リスト一覧画面へ戻る
	    return "redirect:/user/question-list/list";
	}
	
	// リスト名を変更
	@PostMapping("/user/question-list/edit")
	public String postUserQuestionListEdit(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        @RequestParam String listName,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    try {

	        // 指定したリストの名前を変更
	        questionListService.editQuestionList(
	                user,
	                listId,
	                listName,
	                locale);

	        // 変更完了メッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "successMessage",
	                messageSource.getMessage(
	                        "questionList.success.edit",
	                        null,
	                        locale));

	    } catch (IllegalArgumentException e) {

	        // エラーメッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());
	    }

	    // リスト一覧画面へ戻る
	    return "redirect:/user/question-list/list";
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}

}
