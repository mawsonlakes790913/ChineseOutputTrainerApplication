package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountService {
	
	private final UserRepository userRepository;
	private final MessageSource messageSource;
	
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

}
