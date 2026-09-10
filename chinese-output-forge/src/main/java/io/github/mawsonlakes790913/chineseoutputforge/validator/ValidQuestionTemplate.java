package io.github.mawsonlakes790913.chineseoutputforge.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = { ValidQuestionTemplateValidator.class })
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidQuestionTemplate {
	String message() default "AI生成を許可する場合はテンプレートを入力してください。"; 
	
	/** グループ */
    Class<?>[] groups() default {};

    /** ペイロード */
    Class<? extends Payload>[] payload() default {};
 
} 
