package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.entity.PasswordResetToken;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.PasswordResetTokenRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
	
	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final MessageSource messageSource;
	private final MailService mailService;
	private final PasswordEncoder passwordEncoder;
	
	@Transactional
	public void createPasswordResetToken(String email, Locale locale) {

	    // メールアドレスからユーザーを取得
	    Users user = userRepository.findByEmail(email)
	            .orElseThrow(() ->
	                    new IllegalArgumentException(
	                            messageSource.getMessage(
	                                    "password.reset.error.userNotFound",
	                                    null,
	                                    locale)));
	    
	    // 既存のトークンがあれば削除
	    Optional<PasswordResetToken> existingToken =
	            passwordResetTokenRepository.findByUser(user);

	    if (existingToken.isPresent()) {
	        passwordResetTokenRepository.delete(existingToken.get());
	        
	        // DELETEをDBへ反映
	        passwordResetTokenRepository.flush();
	    }

	    // トークンを生成
	    String token = UUID.randomUUID().toString();

	    // パスワードリセットトークンを作成
	    PasswordResetToken passwordResetToken =
	            new PasswordResetToken();

	    passwordResetToken.setUser(user);
	    passwordResetToken.setToken(token);
	    passwordResetToken.setExpiresAt(
	            LocalDateTime.now().plusHours(1));

	    // 保存
	    passwordResetTokenRepository.save(passwordResetToken);
	    
	    // email宛に送信するServiceへ引き継ぐ
	    mailService.sendPasswordResetEmail(
	            user.getEmail(),
	            token);
	}
	
	public PasswordResetToken validateToken(String token, Locale locale) {

		// トークンを検索
	    Optional<PasswordResetToken> resetToken =
	            passwordResetTokenRepository.findByToken(token);

	    // 実在するか検証
	    if (resetToken.isEmpty()) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "password.reset.error.invalidToken",
	                        null,
	                        locale));
	    }
	    
	    // OptionalからPasswordResetTokenを取得
	    PasswordResetToken passwordResetToken = resetToken.get();
	    
	    // 有効期限内か検証
	    if (passwordResetToken.getExpiresAt()
	            .isBefore(LocalDateTime.now())) {

	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "password.reset.error.expiredToken",
	                        null,
	                        locale));
	    }

	    return passwordResetToken;
	}
	
	public void resetPassword(
			String token,
			String newPassword,
			Locale locale) {
		
	    // トークンを取得・検証
	    PasswordResetToken resetToken =
	            validateToken(token, locale);
	    
	    // トークンからユーザーを取得
	    Users user = resetToken.getUser();
		
	    // 新しいパスワードをハッシュ化
	    String encodedPassword =
	            passwordEncoder.encode(newPassword);

	    // パスワードを更新
	    user.setPassword(encodedPassword);

	    // 使用済みトークンを削除
	    passwordResetTokenRepository.delete(resetToken);
		
	}

}
