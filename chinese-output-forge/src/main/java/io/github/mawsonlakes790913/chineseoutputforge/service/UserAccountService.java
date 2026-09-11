package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Role;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.exception.CurrentPasswordMismatchException;
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

        // 変更前と変更後が同じか確認
        if (newLoginId.equals(currentLoginId)) {

            throw new IllegalArgumentException(
                    messageSource.getMessage(
                            "user.edit.loginId.error.same",
                            null,
                            locale
                    )
            );
        }

        // 新しいユーザーIDが既に使われているか確認
        boolean isExists =
                userRepository.existsByLoginId(newLoginId);

        if (isExists) {

            throw new DuplicateKeyException(
                    messageSource.getMessage(
                            "user.edit.loginId.error.duplicate",
                            null,
                            locale
                    )
            );
        }

        // 現在のユーザーを取得
        Users user = getUserOne(currentLoginId);

        if (user == null) {

            throw new IllegalArgumentException(
                    messageSource.getMessage(
                            "user.edit.loginId.error.notFound",
                            null,
                            locale
                    )
            );
        }

        // ユーザーIDを変更
        user.setLoginId(newLoginId);

        // 更新
        userRepository.save(user);

        log.info(
                "ユーザーID変更 currentUserId={}, newUserId={}",
                currentLoginId,
                newLoginId
        );
    }
    
	@Transactional
	public void updatePassword(String loginId, String currentPassword, String newPassword, Locale locale) {
		
	    // 現在のユーザーを取得
	    Users user = getUserOne(loginId);
	    if (user == null) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "user.edit.password.error.notFound",
	                        null,
	                        locale
	                )
	         );
	    }
	    
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

		log.info("パスワード変更 loginId={}", loginId);

	}
	
	@Transactional
	public void cancelMembership(String loginId, Locale locale) {

	    Users user = getUserOne(loginId);

	    if (user == null) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "user.delete.error.notFound",
	                        null,
	                        locale
	                )
	        );
	    }

	    deleteUserData(user);

	    log.info("退会完了 loginId={}", loginId);
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

	    deleteUserData(user);

	    log.info("ユーザー削除完了 userId={}", userId);
	}


	private void deleteUserData(Users user) {

	    Long userId = user.getId();

	    // ① ユーザーのお気に入りを削除
	    favoriteRepository.deleteByFavoriteKeyUserId(userId);

	    // ② ユーザーのAI生成履歴を削除
	    aiGenerationHistoryRepository.deleteByUserId(userId);

	    // ③ ユーザーの学習履歴を削除
	    studyHistoryRepository.deleteByStudyHistoryKeyUserId(userId);

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

	    Users user = getUserOne(loginId);

	    if (user == null) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                		"user.settings.error.notFound",
	                        null,
	                        locale
	                )
	        );
	    }

	    user.setLanguageVariant(languageVariant);

	    userRepository.save(user);

	    log.info(
	            "学習対象言語変更 loginId={}, languageVariant={}",
	            user.getLoginId(),
	            languageVariant
	    );
	}
	
	@Transactional
	public void updatePronunciationType(
	        String loginId,
	        PronunciationType pronunciationType,
	        Locale locale) {

	    Users user = getUserOne(loginId);

	    if (user == null) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                		"user.settings.error.notFound",
	                        null,
	                        locale
	                )
	        );
	    }

	    user.setPronunciationType(pronunciationType);

	    userRepository.save(user);

	    log.info(
	            "表示発音記号変更 loginId={}, pronunciationType={}",
	            user.getLoginId(),
	            pronunciationType
	    );
	}

}
