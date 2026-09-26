package io.github.mawsonlakes790913.chineseoutputforge.validator;

import io.github.mawsonlakes790913.chineseoutputforge.form.QuestionForm;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * AI生成を許可する問題にテンプレートが設定されていることを検証するValidator。
 */
public class ValidQuestionTemplateValidator
implements ConstraintValidator<ValidQuestionTemplate, QuestionForm> {

	/**
	 * AI生成を許可する場合にテンプレートが入力されているか検証する。
	 * AI生成を許可しない場合はテンプレートの入力有無にかかわらず有効とする。
	 *
	 * @param form 検証対象の問題フォーム
	 * @param context バリデーションコンテキスト
	 * @return バリデーション条件を満たす場合はtrue、それ以外はfalse
	 */
	@Override
	public boolean isValid(
	    QuestionForm form,
	    ConstraintValidatorContext context) {
	
	// フォームがnullの場合は他のバリデーションに委ねる
	if (form == null) {
	    return true;
	}

	// AI生成を許可しない場合はテンプレートの検証対象外
	if (!form.isAllowAiVariation()) {
	    return true;
	}

	// テンプレートが入力されている場合は有効
	if (form.getTemplate() != null
	        && !form.getTemplate().isBlank()) {
	    return true;
	}
	
    // デフォルトのクラスレベルエラーを無効化
    context.disableDefaultConstraintViolation();

    // エラーをtemplateフィールドに紐付ける
    context.buildConstraintViolationWithTemplate(
                    context.getDefaultConstraintMessageTemplate())
            .addPropertyNode("template")
            .addConstraintViolation();

    return false;
	}
}
