package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Role;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.exception.CurrentPasswordMismatchException;
import io.github.mawsonlakes790913.chineseoutputforge.exception.DuplicateSignupException;
import io.github.mawsonlakes790913.chineseoutputforge.exception.PasswordSameException;
import io.github.mawsonlakes790913.chineseoutputforge.repository.AiGenerationHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.FavoriteRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ユーザーアカウント管理に関する業務処理を行うService。
 * ユーザー情報の取得、ログインID・メールアドレス・パスワード・学習設定の変更、
 * 退会および管理者によるユーザー削除を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountService {
	
	private final UserRepository userRepository;
	private final MessageSource messageSource;
	private final PasswordEncoder passwordEncoder;
	private final FavoriteRepository favoriteRepository;
	private final StudyHistoryRepository studyHistoryRepository;
	private final AiGenerationHistoryRepository aiGenerationHistoryRepository;
	private final QuestionRepository questionRepository;
	
	/**
	 * ログインIDからユーザーを取得する。
	 *
	 * @param loginId ログインID
	 * @return ユーザー。存在しない場合はnull
	 */
	public Users getUserOne(String loginId) {

	    log.debug("ユーザー検索 userId={}", loginId);

	    return userRepository.findByLoginId(loginId)
	            .orElse(null);
	}
	
	/**
	 * ユーザーのログインIDを変更する。
	 * 現在のログインIDとの一致および他のユーザーとの重複を確認した上で更新する。
	 *
	 * @param currentLoginId 現在のログインID
	 * @param newLoginId 新しいログインID
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void updateLoginId(
	        String currentLoginId,
	        String newLoginId,
	        Locale locale) {

	    // 現在のユーザーを取得
	    Users user = getUserOrThrow(
	            currentLoginId,
	            "user.edit.loginId.error.notFound",
	            locale);

	    // 変更前と変更後が同じか確認
	    if (newLoginId.equals(user.getLoginId())) {

	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "user.edit.loginId.error.same",
	                        null,
	                        locale));
	    }

	    // 新しいログインIDが既に使われているか確認
	    boolean loginIdExists =
	            userRepository.existsByLoginId(newLoginId);

	    if (loginIdExists) {

	        throw new DuplicateSignupException(
	                "loginId",
	                messageSource.getMessage(
	                        "user.edit.loginId.error.duplicate",
	                        null,
	                        locale));
	    }

	    // ログインIDを変更
	    user.setLoginId(newLoginId);

	    // ユーザー情報を更新
	    userRepository.save(user);

	    log.info(
	            "ログインID変更完了 userId={}, oldLoginId={}, newLoginId={}",
	            user.getId(),
	            currentLoginId,
	            newLoginId);
	}
    
	/**
	 * ユーザーのパスワードを変更する。
	 * 現在のパスワードを確認し、新しいパスワードをハッシュ化して更新する。
	 *
	 * @param loginId ログインID
	 * @param currentPassword 現在のパスワード
	 * @param newPassword 新しいパスワード
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void updatePassword(String loginId, String currentPassword, String newPassword, Locale locale) {
		
        // 現在のユーザーを取得
        Users user = getUserOrThrow(
        		loginId,
        		"user.edit.password.error.notFound",
                locale);
	    
	    // 現在のパスワードが正しいか確認
	    if (!passwordEncoder.matches(
	            currentPassword,
	            user.getPassword())) {

	        throw new CurrentPasswordMismatchException(
	                messageSource.getMessage(
	                        "user.edit.password.error.currentPassword",
	                        null,
	                        locale
	                )
	        );
	    }
	    // 新しいパスワードが現在のパスワードと同じか確認
	    if (passwordEncoder.matches(
	            newPassword,
	            user.getPassword())) {

	        throw new PasswordSameException(
	                messageSource.getMessage(
	                        "user.edit.password.error.same",
	                        null,
	                        locale
	                )
	        );
	    }
	    
	    // パスワードをハッシュ化して更新
	    user.setPassword(passwordEncoder.encode(newPassword));

	    // ユーザー情報を更新
	    userRepository.save(user);

	    log.info(
	            "パスワード変更完了 userId={}, loginId={}",
	            user.getId(),
	            user.getLoginId());

	}
	
	/**
	 * ユーザーのメールアドレスを変更する。
	 * 現在のメールアドレスとの一致および他のユーザーとの重複を確認した上で更新する。
	 *
	 * @param loginId ログインID
	 * @param newEmail 新しいメールアドレス
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void updateEmail(
	        String loginId,
	        String newEmail,
	        Locale locale) {

	    // 現在のユーザーを取得
	    Users user = getUserOrThrow(
	            loginId,
	            "user.edit.email.error.notFound",
	            locale);

	    // 現在のメールアドレスを取得
	    String currentEmail = user.getEmail();

	    // 変更前と変更後が同じか確認
	    if (newEmail.equals(currentEmail)) {

	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "user.edit.email.error.same",
	                        null,
	                        locale));
	    }

	    // 新しいメールアドレスが既に使われているか確認
	    boolean emailExists =
	            userRepository.existsByEmail(newEmail);

	    if (emailExists) {

	        throw new DuplicateSignupException(
	                "email",
	                messageSource.getMessage(
	                        "signup.email.duplicate",
	                        null,
	                        locale));
	    }

	    // メールアドレスを変更
	    user.setEmail(newEmail);

	    // ユーザー情報を更新
	    userRepository.save(user);

	    log.info(
	            "メールアドレス変更完了 userId={}, oldEmail={}, newEmail={}",
	            user.getId(),
	            currentEmail,
	            newEmail);
	}
	
	/**
	 * ログイン中のユーザーの退会処理を行う。
	 * ユーザーに関連するデータを削除した後、ユーザー情報を削除する。
	 *
	 * @param loginId ログインID
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void cancelMembership(String loginId, Locale locale) {

        // 現在のユーザーを取得
        Users user = getUserOrThrow(
        		loginId,
        		"user.delete.error.notFound",
                locale);
	    
        // ログ出力用にユーザーIDとログインIDを保持
        Long userId = user.getId();
        String deletedLoginId = user.getLoginId();

        deleteUserData(user);

        log.info(
                "退会完了 userId={}, loginId={}",
                userId,
                deletedLoginId);
	}

	/**
	 * 管理者操作によって指定したユーザーを削除する。
	 * 管理者ユーザーは削除対象外とし、ユーザーに関連するデータも削除する。
	 *
	 * @param userId 削除するユーザーID
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void deleteUser(Long userId, Locale locale) {

	    // 削除対象のユーザーを取得
	    Users user = userRepository.findById(userId)
	            .orElseThrow(() ->
	                    new IllegalArgumentException(
	                            messageSource.getMessage(
	                                    "user.delete.error.notFound",
	                                    null,
	                                    locale)));

	    // 管理者ユーザーは削除不可
	    if (user.getRole() == Role.ADMIN) {
	        throw new IllegalStateException(
	                messageSource.getMessage(
	                        "admin.user.delete.error.admin",
	                        null,
	                        locale));
	    }

	    // ログ出力用に削除対象のログインIDを保持
	    String deletedLoginId = user.getLoginId();

	    // ユーザーに紐づくデータを削除
	    deleteUserData(user);

	    // ユーザー削除完了をログに記録
	    log.info(
	            "ユーザー削除完了 userId={}, loginId={}",
	            userId,
	            deletedLoginId);
	}

	/**
	 * ユーザーに関連するデータとユーザー情報を削除する。
	 *
	 * @param user 削除対象のユーザー
	 */
	private void deleteUserData(Users user) {

	    Long userId = user.getId();

	    // ユーザーのお気に入りを削除
	    favoriteRepository.deleteByUserId(userId);

	    // ユーザーのAI生成履歴を削除
	    aiGenerationHistoryRepository.deleteByUserId(userId);

	    // ユーザーの学習履歴を削除
	    studyHistoryRepository.deleteByUserId(userId);

	    // ユーザー所有のAI生成由来問題を削除
	    questionRepository.deleteByOwnerId(userId);

	    // ユーザーを削除
	    userRepository.delete(user);
	}
	
	/**
	 * ユーザーの学習対象言語を変更する。
	 *
	 * @param loginId ログインID
	 * @param languageVariant 新しい学習対象言語
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void updateLanguageVariant(
	        String loginId,
	        LanguageVariant languageVariant,
	        Locale locale) {
	    
        // 現在のユーザーを取得
        Users user = getUserOrThrow(
        		loginId,
        		"user.settings.error.notFound",
                locale);

        // 学習対象言語を更新
	    user.setLanguageVariant(languageVariant);
	    userRepository.save(user);

	    log.debug(
	            "学習対象言語変更 userId={}, languageVariant={}",
	            user.getId(),
	            languageVariant);
	}
	
	/**
	 * ユーザーの発音表記設定を変更する。
	 *
	 * @param loginId ログインID
	 * @param pronunciationType 新しい発音表記
	 * @param locale 現在の言語・地域情報
	 */
	@Transactional
	public void updatePronunciationType(
	        String loginId,
	        PronunciationType pronunciationType,
	        Locale locale) {
		
        // 現在のユーザーを取得
        Users user = getUserOrThrow(
        		loginId,
        		"user.settings.error.notFound",
                locale);

        // 発音表記設定を更新
	    user.setPronunciationType(pronunciationType);
	    userRepository.save(user);

	    log.debug(
	            "表示発音記号変更 userId={}, pronunciationType={}",
	            user.getId(),
	            pronunciationType);
	}
	
	/**
	 * ログインIDからユーザーを取得し、存在しない場合は例外を発生させる。
	 *
	 * @param loginId ログインID
	 * @param messageCode ユーザーが存在しない場合のメッセージコード
	 * @param locale 現在の言語・地域情報
	 * @return ユーザー
	 */
	private Users getUserOrThrow(
	        String loginId,
	        String messageCode,
	        Locale locale) {

	    // ログインIDからユーザーを取得
	    Users user = getUserOne(loginId);

	    // ユーザーが存在しない場合は例外をスロー
	    if (user == null) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        messageCode,
	                        null,
	                        locale));
	    }

	    return user;
	}

}
