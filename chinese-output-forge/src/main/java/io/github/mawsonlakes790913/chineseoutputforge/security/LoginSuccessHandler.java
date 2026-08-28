package io.github.mawsonlakes790913.chineseoutputforge.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;


@Component
public class LoginSuccessHandler
extends SavedRequestAwareAuthenticationSuccessHandler {
	
	private final UserAccountService userAccountService;
	
	public LoginSuccessHandler(UserAccountService userAccountService) {
	    this.userAccountService = userAccountService;
	    super.setDefaultTargetUrl("/");
	}
	
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // ログインIDからユーザー情報を取得
		Users user = userAccountService.getUserOne(authentication.getName());
		
		// usersテーブルから学習対象言語と発音記号を取得
        LanguageVariant languageVariant = user.getLanguageVariant();
        PronunciationType pronunciationType = user.getPronunciationType();
        
        // セッションの取得
        HttpSession session = request.getSession();
        
        //  セッションに学習対象言語と発音記号の情報を保存
        session.setAttribute("languageVariant", languageVariant);
        session.setAttribute("pronunciationType", pronunciationType);
        
        // Spring Security標準のログイン成功後処理を実行
        super.onAuthenticationSuccess(request, response, authentication);
        
	}

}
