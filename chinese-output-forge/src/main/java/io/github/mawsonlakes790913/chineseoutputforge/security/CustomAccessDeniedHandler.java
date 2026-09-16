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

@Component
@Slf4j
public class CustomAccessDeniedHandler
implements AccessDeniedHandler {

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