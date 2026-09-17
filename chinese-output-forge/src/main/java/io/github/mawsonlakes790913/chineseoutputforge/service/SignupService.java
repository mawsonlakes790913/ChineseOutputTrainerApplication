package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Role;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.exception.DuplicateSignupException;
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
	private final MessageSource messageSource;
	
	// ユーザーを新規登録
	public void signup(SignupForm form, Locale locale) {

	    // ログインIDの重複確認
	    boolean loginIdExists =
	            userRepository.existsByLoginId(
	                    form.getLoginId());

	    if (loginIdExists) {

	        throw new DuplicateSignupException(
	                "loginId",
	                messageSource.getMessage(
	                        "signup.loginId.duplicate",
	                        null,
	                        locale));
	    }
	    
	    // メールアドレスの重複確認
	    boolean emailExists = userRepository.existsByEmail(
                form.getEmail());
	    
	    if (emailExists) {

	        throw new DuplicateSignupException(
	                "email",
	                messageSource.getMessage(
	                        "signup.email.duplicate",
	                        null,
	                        locale));
	    }
	    
	    // ユーザー情報を作成
	    Users user = new Users();
	    
	    // ログインIDを登録
	    user.setLoginId(form.getLoginId());
	    
	    // メールアドレスを登録
	    user.setEmail(form.getEmail());

	    // 一般ユーザーとして登録
	    user.setRole(Role.USER);

	    // パスワードをハッシュ化
	    user.setPassword(
	            passwordEncoder.encode(
	                    form.getPassword()));

	    // 保存
	    Users savedUser =
	            userRepository.save(user);

	    log.info(
	            "ユーザー登録完了 userId={}, loginId={}",
	            savedUser.getId(),
	            savedUser.getLoginId());
	}
}
