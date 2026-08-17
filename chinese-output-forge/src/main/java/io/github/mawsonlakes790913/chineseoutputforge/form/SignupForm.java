package io.github.mawsonlakes790913.chineseoutputforge.form;

import org.hibernate.validator.constraints.Length;

import io.github.mawsonlakes790913.chineseoutputforge.validator.PasswordMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@PasswordMatch(
    passwordFieldName = "password",
    passwordConfirmFieldName = "passwordConfirm"
)
public class SignupForm {
	
    @NotBlank(message = "{signup.loginId.notBlank}")
    @Length(
        min = 8,
        max = 20,
        message = "{signup.loginId.length}"
    )
    @Pattern(
        regexp = "^[a-zA-Z0-9]+$",
        message = "{signup.loginId.pattern}"
    )
    private String loginId;
	
    @NotBlank(message = "{signup.password.notBlank}")
    @Length(
        min = 8,
        max = 20,
        message = "{signup.password.length}"
    )
    @Pattern(
        regexp = "^[a-zA-Z0-9]+$",
        message = "{signup.password.pattern}"
    )
    private String password;
	
    @NotBlank(message = "{signup.passwordConfirm.notBlank}")
    private String passwordConfirm;
}
