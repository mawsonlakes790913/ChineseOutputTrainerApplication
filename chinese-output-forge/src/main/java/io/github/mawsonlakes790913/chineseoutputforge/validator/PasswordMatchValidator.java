package io.github.mawsonlakes790913.chineseoutputforge.validator;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 2つのパスワードフィールドの値が一致することを検証するValidator。
 */
public class PasswordMatchValidator
		implements ConstraintValidator<PasswordMatch, Object> {
	
	private String passwordFieldName;
	private String passwordConfirmFieldName;
	
	/**
	 * バリデーションアノテーションから検証対象のフィールド名を取得する。
	 *
	 * @param passwordMatch パスワード一致検証の設定
	 */
	@Override
	public void initialize(PasswordMatch passwordMatch) {
		this.passwordFieldName = passwordMatch.passwordFieldName();
		this.passwordConfirmFieldName = passwordMatch.passwordConfirmFieldName();
	}
	
	/**
	 * 指定されたオブジェクトのパスワードと確認用パスワードが一致するか検証する。
	 *
	 * @param value 検証対象のオブジェクト
	 * @param context バリデーションコンテキスト
	 * @return パスワードが一致する場合はtrue、それ以外はfalse
	 */
	@Override
	public boolean isValid(Object value,
            ConstraintValidatorContext context) {
		// 検証対象のパスワードを取得
		BeanWrapper beanWrapper = new BeanWrapperImpl(value);
		String password = (String) beanWrapper
		        .getPropertyValue(this.passwordFieldName);

		String passwordConfirm = (String) beanWrapper
		        .getPropertyValue(this.passwordConfirmFieldName);

		// いずれかがnullの場合は他のバリデーションに委ねる
		if (password == null || passwordConfirm == null) {
		    return true;
		}

		// パスワードが一致するか確認
		if (!passwordConfirm.equals(password)) {
		    return false;
		}
		return true;
	}
}
