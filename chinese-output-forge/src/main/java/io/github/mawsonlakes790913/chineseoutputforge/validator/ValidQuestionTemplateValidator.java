package io.github.mawsonlakes790913.chineseoutputforge.validator;

import io.github.mawsonlakes790913.chineseoutputforge.form.QuestionForm;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidQuestionTemplateValidator
implements ConstraintValidator<ValidQuestionTemplate, QuestionForm> {

	@Override
	public boolean isValid(
	    QuestionForm form,
	    ConstraintValidatorContext context) {
	
	if (form == null) {
	    return true;
	}
	
	if (!form.isAllowAiVariation()) {
	    return true;
	}
	
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
