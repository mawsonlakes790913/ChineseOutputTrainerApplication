package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Role;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.form.SignupForm;
import io.github.mawsonlakes790913.chineseoutputforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	
	// ユーザーを新規登録
	public void signup(SignupForm form) {

	    // ログインIDの重複確認
	    boolean isExists =
	            userRepository.existsByLoginId(
	                    form.getLoginId());

	    if (isExists) {
	        throw new DuplicateKeyException(
	                "既に存在するユーザーです");
	    }

	    // ユーザー情報を作成
	    Users user = new Users();
	    user.setLoginId(form.getLoginId());
	    user.setPassword(form.getPassword());

	    // 一般ユーザーとして登録
	    user.setRole(Role.USER);

	    // パスワードをハッシュ化
	    String rawPassword = user.getPassword();
	    user.setPassword(
	            passwordEncoder.encode(rawPassword));

	    Users savedUser =
	            userRepository.save(user);

	    log.info(
	            "ユーザー登録完了 userId={}",
	            savedUser.getLoginId());
	}
}
