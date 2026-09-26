package io.github.mawsonlakes790913.chineseoutputforge.controller;


import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.exception.DuplicateSignupException;
import io.github.mawsonlakes790913.chineseoutputforge.form.SignupForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.SignupService;
import lombok.RequiredArgsConstructor;

/**
 * ユーザー新規登録に関するリクエストを処理するController。
 * 新規登録画面の表示とユーザー登録を行う。
 */
@Controller
@RequiredArgsConstructor
public class SignupController {
	
	private final SignupService signupService;
	private final MessageSource messageSource;
	
	/**
	 * ユーザー新規登録画面を表示する。
	 *
	 * @param form ユーザー新規登録フォーム
	 * @return ユーザー新規登録画面のビュー名
	 */
	@GetMapping("/signup")
	public String getSignup(@ModelAttribute SignupForm form) {
		return "signup/signup";
	}
	
	/**
	 * 入力された内容を検証し、新しいユーザーを登録する。
	 * バリデーションエラーまたは登録情報の重複がある場合は新規登録画面を再表示する。
	 *
	 * @param form ユーザー新規登録フォーム
	 * @param bindingResult バリデーション結果
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @param locale 現在の言語・地域情報
	 * @return ユーザー新規登録画面またはログイン画面へのリダイレクト先
	 */
	@PostMapping("/signup")
	public String postSignup(
	        @ModelAttribute @Validated SignupForm form,
	        BindingResult bindingResult,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // バリデーションエラーがある場合は入力画面を再表示
	    if (bindingResult.hasErrors()) {
	        return getSignup(form);
	    }

	    // ユーザー登録
	    try {
	        signupService.signup(form, locale);
	    } catch (DuplicateSignupException e) {
	        // 重複エラーをフォームへ設定して入力画面を再表示
	        bindingResult.rejectValue(
	                e.getField(),
	                "duplicate",
	                e.getMessage());

	        return getSignup(form);
	    }

	    // 登録完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "signupSuccess",
	            messageSource.getMessage(
	                    "signup.success",
	                    null,
	                    locale));

	    return "redirect:/login";
	}
}
