package io.github.mawsonlakes790913.chineseoutputforge.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class UserSessionRestorationFilter extends OncePerRequestFilter {

    private final UserAccountService userAccountService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // 現在の認証情報を取得
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        // 認証済みユーザーの場合のみ処理
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {

            // 既存のHTTPセッションを取得（なければnull）
            HttpSession session = request.getSession(false);

            // セッションまたはユーザー設定が存在しない場合
            if (session == null
                    || session.getAttribute("languageVariant") == null
                    || session.getAttribute("pronunciationType") == null) {

                // DBからユーザー設定を取得
                Users user = userAccountService.getUserOne(
                        authentication.getName());

                // セッションを取得（存在しなければ新規作成）
                session = request.getSession(true);

                // ユーザー設定をセッションに復元
                session.setAttribute(
                        "languageVariant", user.getLanguageVariant());

                session.setAttribute(
                        "pronunciationType", user.getPronunciationType());

                log.info("User session preferences restored: {}",
                        authentication.getName());
            }
        }

        // 後続のFilter・Controllerへ処理を渡す
        filterChain.doFilter(request, response);
    }
}