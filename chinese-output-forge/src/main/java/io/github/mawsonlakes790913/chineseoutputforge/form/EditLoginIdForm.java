package io.github.mawsonlakes790913.chineseoutputforge.form;

import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * ユーザーID変更画面の入力内容を保持するフォームクラス。
 */
@Data
public class EditLoginIdForm {
    @NotBlank(message = "{signup.loginId.notBlank}")
    @Length(
        min = 8,
        max = 20,
        message = "{signup.loginId.length}"
    )
    @Pattern(
    	    regexp = "^[a-zA-Z0-9_]+$",
    	    message = "{signup.loginId.pattern}"
    	)
	private String loginId;
    
}
