package io.github.mawsonlakes790913.chineseoutputforge.form;

import org.hibernate.validator.constraints.Length;

import io.github.mawsonlakes790913.chineseoutputforge.validator.PasswordMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@PasswordMatch(
	    passwordFieldName = "newPassword",
	    passwordConfirmFieldName = "newPasswordConfirm"
	)
@Data
public class EditPasswordForm {
    // パスワード変更用
	@NotBlank
    private String currentPassword;

    @NotBlank(message = "{signup.password.notBlank}")
    @Length(
        min = 8,
        max = 20,
        message = "{signup.password.length}"
    )
    @Pattern(
    	    regexp = "^[\\x21-\\x7E]+$",
    	    message = "{signup.password.pattern}"
    	)
    private String newPassword;
	
	@NotBlank
	private String newPasswordConfirm;
}
