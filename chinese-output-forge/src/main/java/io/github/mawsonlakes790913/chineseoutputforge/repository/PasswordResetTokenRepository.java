package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.PasswordResetToken;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {
	
	// 指定したユーザーのパスワードリセットトークンを取得
	Optional<PasswordResetToken> findByUser(Users user);

	// 指定したトークン文字列からパスワードリセットトークンを取得
    Optional<PasswordResetToken> findByToken(String token);
    
}
