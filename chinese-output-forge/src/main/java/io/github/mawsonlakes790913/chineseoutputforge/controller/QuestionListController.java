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

import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListItemDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.QuestionListSelectionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListItemService;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;

/**
 * ユーザーの問題リスト管理に関するリクエストを処理するController。
 * 問題リストの一覧表示・作成・編集・削除・詳細表示・問題の登録状態取得を行う。
 */
@Controller
@RequiredArgsConstructor
public class QuestionListController {
	
	private final QuestionListService questionListService;
	private final UserAccountService userAccountService;
	private final QuestionListItemService questionListItemService;
	private final MessageSource messageSource;
	
	/**
	 * ログインユーザーが所有する問題リストの一覧を表示する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param model 画面に渡すデータ
	 * @return 問題リスト一覧画面のビュー名
	 */
	@GetMapping("/user/question-list/list")
	public String getUserQuestionListList(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // ユーザーが所有する問題リストを取得
	    List<QuestionList> questionLists =
	            questionListService.getQuestionLists(user);

	    // 問題リスト一覧を画面へ渡す
	    model.addAttribute("questionLists", questionLists);

	    return "/user/question-list/list";
	}
	
	/**
	 * ログインユーザーの問題リストを新規作成する。
	 * 作成できない場合はエラーメッセージを設定して一覧画面へ戻る。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param listName 作成するリスト名
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @param locale 現在の言語・地域情報
	 * @return 問題リスト一覧画面へのリダイレクト先
	 */
	@PostMapping("/user/question-list/create")
	public String postUserQuestionListCreate(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam String listName,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 新しい問題リストを作成
	    try {
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
	        // 作成エラーメッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());
	    }

	    return "redirect:/user/question-list/list";
	}
	
	/**
	 * 問題画面のモーダルからログインユーザーの問題リストを新規作成する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param listName 作成するリスト名
	 * @param locale 現在の言語・地域情報
	 */
	@PostMapping("/user/question-list/create-modal")
	@ResponseBody
	public void postUserQuestionListCreateModal(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam String listName,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 新しい問題リストを作成
	    questionListService.createQuestionList(
	            user,
	            listName,
	            locale);
	}
	
	/**
	 * 指定された問題リストに登録されている問題を取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param listId 対象リストのID
	 * @param locale 現在の言語・地域情報
	 * @return リストに登録されている問題の一覧
	 */
	@GetMapping("/user/question-list/detail")
	@ResponseBody
	public List<QuestionListItemDto> getUserQuestionListDetail(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定した問題リストに登録されている問題を取得
	    List<QuestionListItemDto> listItems =
	            questionListItemService.getQuestionListItems(
	                    user,
	                    listId,
	                    locale);

	    // 問題リストの問題一覧を返す
	    return listItems;
	}
	
	/**
	 * 指定された問題リストを削除する。
	 * 削除できない場合はエラーメッセージを設定して一覧画面へ戻る。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param listId 削除するリストのID
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @param locale 現在の言語・地域情報
	 * @return 問題リスト一覧画面へのリダイレクト先
	 */
	@PostMapping("/user/question-list/delete")
	public String postUserQuestionListDelete(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定した問題リストを削除
	    try {
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
	        // 削除エラーメッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());
	    }

	    return "redirect:/user/question-list/list";
	}
	
	/**
	 * 指定された問題リストの名前を変更する。
	 * 変更できない場合はエラーメッセージを設定して一覧画面へ戻る。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param listId 変更するリストのID
	 * @param listName 変更後のリスト名
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @param locale 現在の言語・地域情報
	 * @return 問題リスト一覧画面へのリダイレクト先
	 */
	@PostMapping("/user/question-list/edit")
	public String postUserQuestionListEdit(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        @RequestParam String listName,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定した問題リストの名前を変更
	    try {
	        questionListService.editQuestionList(
	                user,
	                listId,
	                listName,
	                locale);

	        // 名前変更完了メッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "successMessage",
	                messageSource.getMessage(
	                        "questionList.success.edit",
	                        null,
	                        locale));
	    } catch (IllegalArgumentException e) {
	        // 名前変更エラーメッセージを設定
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());
	    }

	    return "redirect:/user/question-list/list";
	}
	
	/**
	 * ログインユーザーが所有する問題リストと、
	 * 指定された問題が各リストに登録されているかどうかを取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param questionId 登録状態を確認する問題のID
	 * @return 各問題リストと指定された問題の登録状態
	 */
	@GetMapping("/user/question-list/selection")
	@ResponseBody
	public List<QuestionListSelectionDto> getQuestionListSelection(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long questionId) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // ユーザーが所有する問題リストと指定した問題の登録状態を取得して返す
	    return questionListService.getQuestionListSelection(
	            user,
	            questionId);
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
