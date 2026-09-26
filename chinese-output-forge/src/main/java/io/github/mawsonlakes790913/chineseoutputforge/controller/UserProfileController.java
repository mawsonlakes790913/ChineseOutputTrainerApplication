package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.exception.CurrentPasswordMismatchException;
import io.github.mawsonlakes790913.chineseoutputforge.exception.DuplicateSignupException;
import io.github.mawsonlakes790913.chineseoutputforge.exception.PasswordSameException;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditEmailForm;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditLoginIdForm;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditPasswordForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * ユーザープロフィール管理に関するリクエストを処理するController。
 * プロフィールの表示、ユーザーID・メールアドレス・パスワードの変更、退会処理を行う。
 */
@Controller
@RequiredArgsConstructor
public class UserProfileController {
	
	private final UserAccountService userAccountService;
	
	/**
	 * ログインユーザーのプロフィール画面を表示する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param model 画面に渡すデータ
	 * @return ユーザープロフィール画面のビュー名
	 */
	@GetMapping("/user/profile")
	public String getUserProfile(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model) {

	    // ログインユーザーを取得して画面へ渡す
	    Users user = getLoginUser(loginUser);
	    model.addAttribute("user", user);

	    return "user/profile";
	}
	
	/**
	 * ユーザーID変更画面を表示する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param model 画面に渡すデータ
	 * @param form ユーザーID変更フォーム
	 * @return ユーザーID変更画面のビュー名
	 */
	@GetMapping("/user/edit/loginId")
	public String getEditLoginId(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model,
	        EditLoginIdForm form) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 現在のユーザーIDを画面へ渡す
	    model.addAttribute(
	            "currentLoginId",
	            user.getLoginId());

	    // ユーザーID変更フォームを画面へ渡す
	    model.addAttribute(
	            "editLoginIdForm",
	            form);

	    return "user/edit/loginId";
	}
	
	/**
	 * 入力された内容を検証し、ログインユーザーのユーザーIDを変更する。
	 * 変更成功後は認証情報を削除し、再ログインを要求する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param session セッション情報
	 * @param model 画面に渡すデータ
	 * @param form ユーザーID変更フォーム
	 * @param bindingResult バリデーション結果
	 * @param locale 現在の言語・地域情報
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return ユーザーID変更画面またはログイン画面へのリダイレクト先
	 */
	@PostMapping("/user/edit/loginId")
	public String postEditLoginId(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpSession session,
	        Model model,
	        @Validated EditLoginIdForm form,
	        BindingResult bindingResult,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラーがある場合は入力画面を再表示
	    if (bindingResult.hasErrors()) {
	        return getEditLoginId(loginUser, model, form);
	    }

	    // ユーザーIDを変更
	    try {
	        userAccountService.updateLoginId(
	                loginUser.getUsername(),
	                form.getLoginId(),
	                locale);
	    } catch (DuplicateSignupException e) {
	        // 重複エラーをフォームへ設定して入力画面を再表示
	        bindingResult.rejectValue(
	                e.getField(),
	                "duplicate",
	                e.getMessage());

	        return getEditLoginId(loginUser, model, form);
	    } catch (IllegalArgumentException e) {
	        // 変更前と同じユーザーIDの場合はエラーを設定して入力画面を再表示
	        bindingResult.rejectValue(
	                "loginId",
	                "same",
	                e.getMessage());

	        return getEditLoginId(loginUser, model, form);
	    }

	    // ユーザーID変更後に認証状態を解除
	    clearAuthentication(session);

	    // 変更完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.loginId.changed");

	    return "redirect:/login";
	}
	
	/**
	 * メールアドレス変更画面を表示する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param model 画面に渡すデータ
	 * @param form メールアドレス変更フォーム
	 * @return メールアドレス変更画面のビュー名
	 */
	@GetMapping("/user/edit/email")
	public String getEditEmail(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model,
	        EditEmailForm form) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 現在のメールアドレスを画面へ渡す
	    model.addAttribute("currentEmail", user.getEmail());

	    // メールアドレス変更フォームを画面へ渡す
	    model.addAttribute("editEmailForm", form);

	    return "user/edit/email";
	}
	
	/**
	 * 入力された内容を検証し、ログインユーザーのメールアドレスを変更する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param session セッション情報
	 * @param model 画面に渡すデータ
	 * @param form メールアドレス変更フォーム
	 * @param bindingResult バリデーション結果
	 * @param locale 現在の言語・地域情報
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return メールアドレス変更画面またはプロフィール画面へのリダイレクト先
	 */
	@PostMapping("/user/edit/email")
	public String postEditEmail(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model,
	        @Validated EditEmailForm form,
	        BindingResult bindingResult,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラーがある場合は入力画面を再表示
	    if (bindingResult.hasErrors()) {
	        return getEditEmail(loginUser, model, form);
	    }

	    // メールアドレスを変更
	    try {
	        userAccountService.updateEmail(
	                loginUser.getUsername(),
	                form.getEmail(),
	                locale);
	    } catch (DuplicateSignupException e) {
	        // 重複エラーをフォームへ設定して入力画面を再表示
	        bindingResult.rejectValue(
	                e.getField(),
	                "duplicate",
	                e.getMessage());

	        return getEditEmail(loginUser, model, form);
	    } catch (IllegalArgumentException e) {
	        // 変更前と同じメールアドレスの場合はエラーを設定して入力画面を再表示
	        bindingResult.rejectValue(
	                "email",
	                "same",
	                e.getMessage());

	        return getEditEmail(loginUser, model, form);
	    }

	    // 変更完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.email.changed");

	    return "redirect:/user/profile";
	}
	
	/**
	 * パスワード変更画面を表示する。
	 *
	 * @param model 画面に渡すデータ
	 * @param form パスワード変更フォーム
	 * @return パスワード変更画面のビュー名
	 */
	@GetMapping("/user/edit/password")
	public String getEditPassword(
	        Model model,
	        EditPasswordForm form) {

	    // パスワード変更フォームを画面へ渡す
	    model.addAttribute("editPasswordForm", form);

	    return "user/edit/password";
	}
	
	/**
	 * 入力された内容を検証し、ログインユーザーのパスワードを変更する。
	 * 変更成功後は認証情報を削除し、再ログインを要求する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param session セッション情報
	 * @param model 画面に渡すデータ
	 * @param form パスワード変更フォーム
	 * @param bindingResult バリデーション結果
	 * @param locale 現在の言語・地域情報
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return パスワード変更画面またはログイン画面へのリダイレクト先
	 */
	@PostMapping("/user/edit/password")
	public String postEditPassword(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpSession session,
	        Model model,
	        @Validated EditPasswordForm form,
	        BindingResult bindingResult,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラーがある場合は入力画面を再表示
	    if (bindingResult.hasErrors()) {
	        return getEditPassword(model, form);
	    }

	    // パスワードを変更
	    try {
	        userAccountService.updatePassword(
	                loginUser.getUsername(),
	                form.getCurrentPassword(),
	                form.getNewPassword(),
	                locale);
	    } catch (CurrentPasswordMismatchException e) {
	        // 現在のパスワードが一致しない場合はエラーを設定して入力画面を再表示
	        bindingResult.rejectValue(
	                "currentPassword",
	                "invalid",
	                e.getMessage());

	        return getEditPassword(model, form);
	    } catch (IllegalArgumentException e) {
	        // ユーザーが見つからない場合はエラーを設定して入力画面を再表示
	        bindingResult.reject(
	                "userNotFound",
	                e.getMessage());

	        return getEditPassword(model, form);
	    } catch (PasswordSameException e) {
	        // 新しいパスワードが現在と同じ場合はエラーを設定して入力画面を再表示
	        bindingResult.rejectValue(
	                "newPassword",
	                "same",
	                e.getMessage());

	        return getEditPassword(model, form);
	    }

	    // パスワード変更後に認証状態を解除
	    clearAuthentication(session);

	    // 変更完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.password.changed");

	    return "redirect:/login";
	}
	
	/**
	 * ログインユーザーの退会処理を行い、ログアウトする。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param request HTTPリクエスト
	 * @param locale 現在の言語・地域情報
	 * @return 退会完了画面へのリダイレクト先
	 * @throws ServletException ログアウト処理に失敗した場合
	 */
	@PostMapping("/user/delete")
	public String postUserDelete(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpServletRequest request,
	        Locale locale)
	        throws ServletException {

	    // ログインユーザーの退会処理
	    userAccountService.cancelMembership(
	            loginUser.getUsername(),
	            locale);

	    // 退会後にログアウト
	    request.logout();

	    return "redirect:/user/canceled";
	}
	
	/**
	 * 退会完了画面を表示する。
	 *
	 * @return 退会完了画面のビュー名
	 */
	@GetMapping("/user/canceled")
	public String getCanceled() {
		return "user/canceled";
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
	
	/**
	 * Spring Securityの認証情報をクリアし、ログアウト状態にする。
	 *
	 * @param session セッション情報
	 */
	private void clearAuthentication(HttpSession session) {

	    // Spring Securityの現在の認証情報をクリア
	    SecurityContextHolder.clearContext();

	    // セッションに保存されているSecurityContextを削除
	    session.removeAttribute(
	            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
	}

}
