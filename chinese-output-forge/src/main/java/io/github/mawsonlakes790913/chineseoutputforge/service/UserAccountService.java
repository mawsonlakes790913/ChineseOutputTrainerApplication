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
	
	public Users getUserOne(String loginId) {

	    log.debug("ユーザー検索 userId={}", loginId);

	    return userRepository.findByLoginId(loginId)
	            .orElse(null);
	}
	
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

	    // 更新
	    userRepository.save(user);

	    log.info(
	            "ログインID変更完了 userId={}, oldLoginId={}, newLoginId={}",
	            user.getId(),
	            currentLoginId,
	            newLoginId);
	}
    
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

	    // 更新
	    userRepository.save(user);

	    log.info(
	            "パスワード変更完了 userId={}, loginId={}",
	            user.getId(),
	            user.getLoginId());

	}
	
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

	    // 更新
	    userRepository.save(user);

	    log.info(
	            "メールアドレス変更完了 userId={}, oldEmail={}, newEmail={}",
	            user.getId(),
	            currentEmail,
	            newEmail);
	}
	
	@Transactional
	public void cancelMembership(String loginId, Locale locale) {

        // 現在のユーザーを取得
        Users user = getUserOrThrow(
        		loginId,
        		"user.delete.error.notFound",
                locale);
	    
        // ログ用にidとログインidを取得
        Long userId = user.getId();
        String deletedLoginId = user.getLoginId();

        deleteUserData(user);

        log.info(
                "退会完了 userId={}, loginId={}",
                userId,
                deletedLoginId);
	}


	@Transactional
	public void deleteUser(Long userId, Locale locale) {

	    Users user = userRepository.findById(userId)
	            .orElseThrow(() ->
	                    new IllegalArgumentException(
	                            messageSource.getMessage(
	                                    "user.delete.error.notFound",
	                                    null,
	                                    locale
	                            )
	                    )
	            );

	    if (user.getRole() == Role.ADMIN) {
	        throw new IllegalStateException(
	                messageSource.getMessage(
	                        "admin.user.delete.error.admin",
	                        null,
	                        locale
	                )
	        );
	    }

	    String deletedLoginId = user.getLoginId();

	    deleteUserData(user);

	    log.info(
	            "ユーザー削除完了 userId={}, loginId={}",
	            userId,
	            deletedLoginId);
	}


	private void deleteUserData(Users user) {

	    Long userId = user.getId();

	    // ① ユーザーのお気に入りを削除
	    favoriteRepository.deleteByUserId(userId);

	    // ② ユーザーのAI生成履歴を削除
	    aiGenerationHistoryRepository.deleteByUserId(userId);

	    // ③ ユーザーの学習履歴を削除
	    studyHistoryRepository.deleteByUserId(userId);

	    // ④ ユーザー所有のAI生成由来問題を削除
	    questionRepository.deleteByOwnerId(userId);

	    // ⑤ ユーザーを削除
	    userRepository.delete(user);
	}
	
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

	    user.setLanguageVariant(languageVariant);

	    userRepository.save(user);

	    log.debug(
	            "学習対象言語変更 userId={}, languageVariant={}",
	            user.getId(),
	            languageVariant);
	}
	
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

	    user.setPronunciationType(pronunciationType);

	    userRepository.save(user);

	    log.debug(
	            "表示発音記号変更 userId={}, pronunciationType={}",
	            user.getId(),
	            pronunciationType);
	}
	
	private Users getUserOrThrow(
	        String loginId,
	        String messageCode,
	        Locale locale) {

	    Users user = getUserOne(loginId);

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
