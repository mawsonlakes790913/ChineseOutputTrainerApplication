package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AdminUserSortCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminUserSearchDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.AdminUserService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;

/**
 * 管理者用のユーザー管理に関するリクエストを処理するController。
 * ユーザーの一覧表示・凍結・凍結解除・削除を行う。
 */
@Controller
@RequiredArgsConstructor
public class AdminUserController {
	
	private final AdminUserService adminUserService;
	private final PaginationService paginationService;
	private final UserAccountService userAccountService;
	
	/**
	 * 検索条件と並び順に基づいてユーザーを取得し、
	 * 管理者用ユーザー一覧画面を表示する。
	 *
	 * @param searchDto ユーザーの検索条件
	 * @param sortCondition 並び順
	 * @param pageable ページング情報
	 * @param model 画面に渡すデータ
	 * @return 管理者用ユーザー一覧画面のビュー名
	 */
	@GetMapping("/admin/user/list")
	public String getUserList(
	        @ModelAttribute AdminUserSearchDto searchDto,
	        @RequestParam(defaultValue = "LOGIN_ID_ASC")
	        AdminUserSortCondition sortCondition,
	        @PageableDefault(page = 0, size = 50) Pageable pageable,
	        Model model) {

	    // 検索条件と並び順に基づいてユーザー一覧を取得
	    Page<Users> userPage =
	            adminUserService.getUsers(
	                    searchDto,
	                    sortCondition,
	                    pageable);

	    // ページネーション情報を作成
	    PaginationDto pagination =
	            paginationService.createPagination(userPage);

	    // ユーザー一覧とページ情報を画面へ渡す
	    model.addAttribute("userList", userPage.getContent());
	    model.addAttribute("page", userPage);
	    model.addAttribute("pagination", pagination);

	    // 検索条件と並び順を画面へ戻す
	    model.addAttribute("searchDto", searchDto);
	    model.addAttribute("sortCondition", sortCondition);

	    return "/admin/user/list";
	}
	
	/**
	 * 指定されたユーザーのアカウントを凍結する。
	 *
	 * @param userId 凍結するユーザーのID
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return 管理者用ユーザー一覧画面へのリダイレクト先
	 */
	@PostMapping("/admin/user/lock")
	public String postUserLock(
	        @RequestParam Long userId,
	        RedirectAttributes redirectAttributes) {

	    // 指定されたユーザーを凍結
	    adminUserService.lockUser(userId);

	    // 凍結完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "ユーザーを凍結しました。");

	    return "redirect:/admin/user/list";
	}


	/**
	 * 指定されたユーザーのアカウント凍結を解除する。
	 *
	 * @param userId 凍結を解除するユーザーのID
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return 管理者用ユーザー一覧画面へのリダイレクト先
	 */
	@PostMapping("/admin/user/unlock")
	public String postUserUnlock(
	        @RequestParam Long userId,
	        RedirectAttributes redirectAttributes) {

	    // 指定されたユーザーの凍結を解除
	    adminUserService.unlockUser(userId);

	    // 凍結解除完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "ユーザーの凍結を解除しました。");

	    return "redirect:/admin/user/list";
	}
	
	/**
	 * 指定されたユーザーを削除する。
	 *
	 * @param userId 削除するユーザーのID
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @param locale 削除処理で使用する言語・地域情報
	 * @return 管理者用ユーザー一覧画面へのリダイレクト先
	 */
	@PostMapping("/admin/user/delete")
	public String postUserDelete(
	        @RequestParam Long userId,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // 指定されたユーザーを削除
	    userAccountService.deleteUser(userId, locale);

	    // 削除完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "ユーザーを削除しました。");

	    return "redirect:/admin/user/list";
	}

}


