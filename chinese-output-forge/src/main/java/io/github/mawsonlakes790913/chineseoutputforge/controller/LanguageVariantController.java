package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 学習対象言語の変更に関するリクエストを処理するController。
 */
@Controller
@RequiredArgsConstructor
public class LanguageVariantController {
	
	private final UserAccountService userAccountService;

	/**
	 * 学習対象言語を変更する。
	 * ログイン中の場合はユーザー情報を更新し、
	 * 言語変更に伴って中断中の学習データを破棄する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param languageVariant 変更後の学習対象言語
	 * @param redirect 変更後のリダイレクト先
	 * @param locale 現在の言語・地域情報
	 * @param session セッション情報
	 * @return 言語変更後のリダイレクト先
	 */
	@PostMapping("/language-variant")
	public String postLanguageVariantChange(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam LanguageVariant languageVariant,
	        @RequestParam(required = false) String redirect,
	        Locale locale,
	        HttpSession session) {

	    // セッションから現在の学習対象言語を取得
	    LanguageVariant currentLanguageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");

	    // 同じ学習対象言語の場合は変更せず元の画面へ戻る
	    if (languageVariant == currentLanguageVariant) {
	        return redirect != null
	                ? "redirect:" + redirect
	                : "redirect:/";
	    }

	    // ログイン中の場合はDBの学習対象言語を更新
	    if (loginUser != null) {
	        userAccountService.updateLanguageVariant(
	                loginUser.getUsername(),
	                languageVariant,
	                locale);
	    }

	    // 学習対象言語の変更に伴い、中断中の通常学習データを破棄
	    session.removeAttribute("practiceQuestions");
	    session.removeAttribute("practiceCurrentPage");

	    // 変更後の学習対象言語をセッションに保存
	    session.setAttribute("languageVariant", languageVariant);

	    // 戻り先が指定されている場合は元の画面へ戻る
	    if (redirect != null) {
	        return "redirect:" + redirect;
	    }

	    // 戻り先が指定されていない場合はトップ画面へ戻る
	    return "redirect:/";
	}
}	