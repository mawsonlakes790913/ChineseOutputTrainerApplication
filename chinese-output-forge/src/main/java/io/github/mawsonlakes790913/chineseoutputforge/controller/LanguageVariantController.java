package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LanguageVariantController {
	
	private final UserAccountService userAccountService;

	@GetMapping("/language-variant")
	public String changeLanguageVariant(
			@AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam LanguageVariant languageVariant,
	        @RequestParam(required = false) String redirect,
	        Locale locale,
	        HttpSession session) {

	    LanguageVariant current =
	            (LanguageVariant) session.getAttribute("languageVariant");

	    // 同じ言語なら変更処理をしない
	    if (languageVariant == current) {
	        return redirect != null
	                ? "redirect:" + redirect
	                : "redirect:/";
	    }
	    // ログインしていればDBの学習対象言語情報を更新
	    if (loginUser != null) {
	    	userAccountService.updateLanguageVariant(loginUser.getUsername(), 
	    											 languageVariant, 
	    											 locale);
	    }

	    // 中断中の通常学習データを破棄(今後復習も同じように破棄される)
	    session.removeAttribute("practiceQuestions");
	    session.removeAttribute("practiceCurrentPage");

	    // 学習対象言語をSessionに保存
	    session.setAttribute("languageVariant", languageVariant);

	    // Practiceメニューまたはsetting画面から変更した場合
	    if (redirect != null) {
	        return "redirect:" + redirect;
	    }

	    // その他のページ
	    return "redirect:/";
	}
}	