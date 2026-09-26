package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.PasswordResetToken;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;

/**
 * パスワード再設定トークン情報のDB操作を行うRepository。
 */
public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    /**
     * 指定したユーザーのパスワード再設定トークンを取得する。
     *
     * @param user ユーザー
     * @return パスワード再設定トークン
     */
    Optional<PasswordResetToken> findByUser(Users user);

    /**
     * 指定したトークン文字列からパスワード再設定トークンを取得する。
     *
     * @param token トークン文字列
     * @return パスワード再設定トークン
     */
    Optional<PasswordResetToken> findByToken(String token);
}
