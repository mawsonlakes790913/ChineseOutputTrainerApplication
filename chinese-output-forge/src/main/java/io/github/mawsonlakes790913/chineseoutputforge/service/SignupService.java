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

/**
 * ユーザー新規登録に関する業務処理を行うService。
 * ログインID・メールアドレスの重複確認とユーザーの登録を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final MessageSource messageSource;
	
	/**
	 * 入力されたユーザー情報をもとに新しいユーザーを登録する。
	 * ログインIDとメールアドレスの重複を確認し、
	 * パスワードをハッシュ化して一般ユーザーとして保存する。
	 *
	 * @param form ユーザー新規登録画面の入力内容
	 * @param locale 現在の言語・地域情報
	 */
	public void signup(SignupForm form, Locale locale) {

	    // ログインIDの重複を確認
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

	    // メールアドレスの重複を確認
	    boolean emailExists =
	            userRepository.existsByEmail(
	                    form.getEmail());

	    if (emailExists) {
	        throw new DuplicateSignupException(
	                "email",
	                messageSource.getMessage(
	                        "signup.email.duplicate",
	                        null,
	                        locale));
	    }

	    // 新しいユーザーを作成して登録情報を設定
	    Users user = new Users();
	    user.setLoginId(form.getLoginId());
	    user.setEmail(form.getEmail());
	    user.setRole(Role.USER);
	    user.setPassword(
	            passwordEncoder.encode(
	                    form.getPassword()));

	    // ユーザー情報を保存
	    Users savedUser = userRepository.save(user);

	    // ユーザー登録完了をログに記録
	    log.info(
	            "ユーザー登録完了 userId={}, loginId={}",
	            savedUser.getId(),
	            savedUser.getLoginId());
	}
}
