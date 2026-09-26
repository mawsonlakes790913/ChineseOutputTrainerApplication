package io.github.mawsonlakes790913.chineseoutputforge.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * ログイン成功後の処理を行うAuthenticationSuccessHandler。
 * ユーザー設定をセッションに保存し、ログイン成功情報を設定する。
 */
@Component
@Slf4j
public class LoginSuccessHandler
extends SavedRequestAwareAuthenticationSuccessHandler {
	
	private final UserAccountService userAccountService;
	
    /**
     * LoginSuccessHandlerを生成し、ログイン成功後のデフォルト遷移先を設定する。
     *
     * @param userAccountService ユーザーアカウント管理Service
     */
	public LoginSuccessHandler(UserAccountService userAccountService) {
	    this.userAccountService = userAccountService;
	    super.setDefaultTargetUrl("/");
	}
	
    /**
     * ログイン成功後の処理を行う。
     * ユーザーの学習設定をセッションに保存し、
     * ログイン成功メッセージを設定した後に画面遷移を行う。
     *
     * @param request HTTPリクエスト
     * @param response HTTPレスポンス
     * @param authentication 認証情報
     * @throws IOException レスポンス処理に失敗した場合
     * @throws ServletException Servlet処理に失敗した場合
     */
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
        
        // ログイン成功を記録
        log.info("Login succeeded: loginId={}", authentication.getName());
        
		// FlashMapを作成
		FlashMap flashMap = new FlashMap();

		// ログイン成功メッセージのキーを設定
		flashMap.put("messageKey", "login.success");
		
		// ログインIDを設定
		flashMap.put("loginId", user.getLoginId());

		// FlashMapManagerを作成
		FlashMapManager flashMapManager =
		        new SessionFlashMapManager();

		// FlashMapを保存
		flashMapManager.saveOutputFlashMap(
		        flashMap,
		        request,
		        response);

		// Spring Security標準のログイン成功後処理を実行
		super.onAuthenticationSuccess(
		        request,
		        response,
		        authentication);
        
	}

}
