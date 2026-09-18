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

@Controller
@RequiredArgsConstructor
public class PasswordResetController {
	
	private final PasswordResetService passwordResetService;
	
	@GetMapping("/forgot-password")
	public String getForgotPassword(
	        ForgotPasswordForm form,
	        Model model) {

	    // パスワードリセット要求フォームをModelに登録
	    model.addAttribute("forgotPasswordForm", form);

	    // メールアドレス入力画面を表示
	    return "forgot-password";
	}
	
	@PostMapping("/forgot-password")
	public String postForgotPassword(
	        @Validated ForgotPasswordForm form,
	        BindingResult bindingResult,
	        Model model,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラー確認
	    if (bindingResult.hasErrors()) {
	        return getForgotPassword(form, model);
	    }

	    // パスワードリセットトークンを作成してメールを送信
	    passwordResetService.createPasswordResetToken(
	            form.getEmail(),
	            locale);

	    // 次の画面でメールアドレスを表示する
	    redirectAttributes.addAttribute(
	            "email",
	            form.getEmail());

	    // メール送信完了画面へリダイレクト
	    return "redirect:/forgot-password/sent";
	}
	
	@GetMapping("/forgot-password/sent")
	public String getForgotPasswordSent(
	        @RequestParam String email,
	        Model model) {

	    // メールアドレスをModelに登録
	    model.addAttribute("email", email);

	    // メール送信完了画面を表示
	    return "forgot-password-sent";
	}

	@GetMapping("/reset-password")
	public String getResetPassword(
	        @RequestParam String token,
	        Model model,
	        ResetPasswordForm form,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    // トークンを検証
	    try {
	        passwordResetService.validateToken(token, locale);

	    } catch (IllegalArgumentException e) {

	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                e.getMessage());

	        return "redirect:/login";
	    }
	    
	    // トークンをフォームへ引き継ぐ
	    model.addAttribute("token", token);
	    
	    // パスワード変更フォームをModelに登録
	    model.addAttribute("resetPasswordForm", form);

	    // パスワードリセットフォームを表示
	    return "reset-password";
	}
	
	@PostMapping("/reset-password")
	public String postResetPassword(
			@RequestParam String token,
			Model model,
			@Validated ResetPasswordForm form,
			BindingResult bindingResult,
			Locale locale,
			RedirectAttributes redirectAttributes) {
		
		// 通常のバリデーションエラー確認
	    if (bindingResult.hasErrors()) {
	        return getResetPassword(token, model, form, locale, redirectAttributes);
	    }
	    
	    // トークンを検証してパスワードを変更
	    passwordResetService.resetPassword(
	            token,
	            form.getNewPassword(),
	            locale);
	    
	    // 変更完了メッセージ
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "password.reset.success"
	    );
	    
	    return "redirect:/login";
		
	}
}
