package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.form.ForgotPasswordForm;
import io.github.mawsonlakes790913.chineseoutputforge.form.ResetPasswordForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.PasswordResetService;
import lombok.RequiredArgsConstructor;

/**
 * パスワードリセット機能に関するリクエストを処理するController。
 * リセット用メールの送信、トークンの検証、パスワードの変更を行う。
 */
@Controller
@RequiredArgsConstructor
public class PasswordResetController {
	
	private final PasswordResetService passwordResetService;
	
	/**
	 * パスワードリセット要求画面を表示する。
	 *
	 * @param form パスワードリセット要求フォーム
	 * @param model 画面に渡すデータ
	 * @return パスワードリセット要求画面のビュー名
	 */
	@GetMapping("/forgot-password")
	public String getForgotPassword(
	        ForgotPasswordForm form,
	        Model model) {

	    // パスワードリセット要求フォームをModelに登録
	    model.addAttribute("forgotPasswordForm", form);

	    return "forgot-password";
	}
	
	/**
	 * 入力されたメールアドレスを検証し、パスワードリセット用メールを送信する。
	 * バリデーションエラーがある場合は入力画面を再表示する。
	 *
	 * @param form パスワードリセット要求フォーム
	 * @param bindingResult バリデーション結果
	 * @param model 画面に渡すデータ
	 * @param locale 現在の言語・地域情報
	 * @param redirectAttributes リダイレクト後に渡す属性
	 * @return パスワードリセット要求画面またはメール送信完了画面へのリダイレクト先
	 */
	@PostMapping("/forgot-password")
	public String postForgotPassword(
	        @Validated ForgotPasswordForm form,
	        BindingResult bindingResult,
	        Model model,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラーがある場合は入力画面を再表示
	    if (bindingResult.hasErrors()) {
	        return getForgotPassword(form, model);
	    }

	    // パスワードリセットトークンを作成してメールを送信
	    passwordResetService.createPasswordResetToken(
	            form.getEmail(),
	            locale);

	    // メール送信完了画面で表示するメールアドレスを設定
	    redirectAttributes.addAttribute(
	            "email",
	            form.getEmail());

	    return "redirect:/forgot-password/sent";
	}
	
	/**
	 * パスワードリセット用メールの送信完了画面を表示する。
	 *
	 * @param email 送信先のメールアドレス
	 * @param model 画面に渡すデータ
	 * @return メール送信完了画面のビュー名
	 */
	@GetMapping("/forgot-password/sent")
	public String getForgotPasswordSent(
	        @RequestParam String email,
	        Model model) {

	    // メールアドレスをModelに登録
	    model.addAttribute("email", email);

	    return "forgot-password-sent";
	}

	/**
	 * パスワードリセットトークンを検証し、パスワード変更画面を表示する。
	 * トークンが無効な場合はエラーメッセージを設定してログイン画面へ戻る。
	 *
	 * @param token パスワードリセットトークン
	 * @param model 画面に渡すデータ
	 * @param form パスワード変更フォーム
	 * @param locale 現在の言語・地域情報
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return パスワード変更画面またはログイン画面へのリダイレクト先
	 */
	@GetMapping("/reset-password")
	public String getResetPassword(
	        @RequestParam String token,
	        Model model,
	        ResetPasswordForm form,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // パスワードリセットトークンを検証
	    try {
	        passwordResetService.validateToken(token, locale);
	    } catch (IllegalArgumentException e) {
	        // トークンが無効な場合はエラーメッセージを設定してログイン画面へ戻る
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());

	        return "redirect:/login";
	    }

	    // 検証済みのトークンを画面へ渡す
	    model.addAttribute("token", token);

	    // パスワード変更フォームを画面へ渡す
	    model.addAttribute("resetPasswordForm", form);

	    return "reset-password";
	}
	
	/**
	 * 入力された新しいパスワードを検証し、パスワードを変更する。
	 * バリデーションエラーがある場合はパスワード変更画面を再表示する。
	 *
	 * @param token パスワードリセットトークン
	 * @param model 画面に渡すデータ
	 * @param form パスワード変更フォーム
	 * @param bindingResult バリデーション結果
	 * @param locale 現在の言語・地域情報
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return パスワード変更画面またはログイン画面へのリダイレクト先
	 */
	@PostMapping("/reset-password")
	public String postResetPassword(
	        @RequestParam String token,
	        Model model,
	        @Validated ResetPasswordForm form,
	        BindingResult bindingResult,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラーがある場合はパスワード変更画面を再表示
	    if (bindingResult.hasErrors()) {
	        return getResetPassword(
	                token,
	                model,
	                form,
	                locale,
	                redirectAttributes);
	    }

	    // トークンを検証してパスワードを変更
	    passwordResetService.resetPassword(
	            token,
	            form.getNewPassword(),
	            locale);

	    // パスワード変更完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "password.reset.success");

	    return "redirect:/login";
	}
}
