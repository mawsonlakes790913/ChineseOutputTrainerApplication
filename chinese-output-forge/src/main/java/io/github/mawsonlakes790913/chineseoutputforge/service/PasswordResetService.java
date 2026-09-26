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

/**
 * パスワード再設定に関する業務処理を行うService。
 * 再設定用トークンの発行・検証、メール送信、パスワードの変更を行う。
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {
	
	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final MessageSource messageSource;
	private final MailService mailService;
	private final PasswordEncoder passwordEncoder;
	
	/**
	 * 指定したメールアドレスのユーザーにパスワード再設定用トークンを発行する。
	 * 既存のトークンがある場合は削除し、新しいトークンを保存した後、
	 * パスワード再設定用メールを送信する。
	 *
	 * @param email メールアドレス
	 * @param locale 現在の言語・地域情報
	 */
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

	    // 既存のパスワード再設定用トークンを取得
	    Optional<PasswordResetToken> existingToken =
	            passwordResetTokenRepository.findByUser(user);

	    // 既存のトークンがある場合は削除
	    if (existingToken.isPresent()) {
	        passwordResetTokenRepository.delete(existingToken.get());

	        // DELETEをDBへ反映
	        passwordResetTokenRepository.flush();
	    }

	    // 新しいトークンを生成
	    String token = UUID.randomUUID().toString();

	    // パスワード再設定用トークンを作成
	    PasswordResetToken passwordResetToken = new PasswordResetToken();
	    passwordResetToken.setUser(user);
	    passwordResetToken.setToken(token);
	    passwordResetToken.setExpiresAt(LocalDateTime.now().plusHours(1));

	    // パスワード再設定用トークンを保存
	    passwordResetTokenRepository.save(passwordResetToken);

	    // パスワード再設定用メールを送信
	    mailService.sendPasswordResetEmail(
	            user.getEmail(),
	            token);
	}
	
	/**
	 * パスワード再設定用トークンが有効か検証する。
	 * トークンが存在しない場合、または有効期限が切れている場合はエラーとする。
	 *
	 * @param token パスワード再設定用トークン
	 * @param locale 現在の言語・地域情報
	 * @return 検証済みのパスワード再設定用トークン
	 */
	public PasswordResetToken validateToken(String token, Locale locale) {

	    // トークン文字列からパスワード再設定用トークンを取得
	    Optional<PasswordResetToken> resetToken =
	            passwordResetTokenRepository.findByToken(token);

	    // トークンが存在するか検証
	    if (resetToken.isEmpty()) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "password.reset.error.invalidToken",
	                        null,
	                        locale));
	    }

	    // Optionalからパスワード再設定用トークンを取得
	    PasswordResetToken passwordResetToken = resetToken.get();

	    // トークンが有効期限内か検証
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
	
	/**
	 * パスワード再設定用トークンを使用してユーザーのパスワードを変更する。
	 * パスワード変更後は使用済みのトークンを削除する。
	 *
	 * @param token パスワード再設定用トークン
	 * @param newPassword 新しいパスワード
	 * @param locale 現在の言語・地域情報
	 */
	public void resetPassword(
			String token,
			String newPassword,
			Locale locale) {
		
	    // トークンを取得・検証
	    PasswordResetToken resetToken = validateToken(token, locale);
	    
	    // トークンからユーザーを取得
	    Users user = resetToken.getUser();
		
	    // 新しいパスワードをハッシュ化
	    String encodedPassword = passwordEncoder.encode(newPassword);

	    // パスワードを更新
	    user.setPassword(encodedPassword);

	    // 使用済みトークンを削除
	    passwordResetTokenRepository.delete(resetToken);
		
	}

}
