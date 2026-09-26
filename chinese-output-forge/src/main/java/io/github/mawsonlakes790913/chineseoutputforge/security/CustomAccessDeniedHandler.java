package io.github.mawsonlakes790913.chineseoutputforge.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * アクセス権限がないリクエストを処理するAccessDeniedHandler。
 * アクセス拒否情報をログに記録し、403エラーを返す。
 */
@Component
@Slf4j
public class CustomAccessDeniedHandler
implements AccessDeniedHandler {

    /**
     * アクセス拒否時の処理を行う。
     * ログインIDとアクセス先URIをログに記録し、403エラーを返す。
     *
     * @param request HTTPリクエスト
     * @param response HTTPレスポンス
     * @param accessDeniedException アクセス拒否時に発生した例外
     * @throws IOException レスポンスの送信に失敗した場合
     * @throws ServletException Servlet処理に失敗した場合
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        // 認証情報を取得
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        // ログインIDを取得
        String loginId = authentication != null
                ? authentication.getName()
                : "anonymous";

        // アクセス拒否を記録
        log.warn(
                "Access denied: loginId={}, uri={}",
                loginId,
                request.getRequestURI());

        // 403エラーを返す
        response.sendError(
                HttpServletResponse.SC_FORBIDDEN);
    }
}