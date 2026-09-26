package io.github.mawsonlakes790913.chineseoutputforge.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * パスワード再設定用メールアドレスの入力内容を保持するフォームクラス。
 */
@Data
public class ForgotPasswordForm {

    @NotBlank(message = "{signup.email.notBlank}")
    @Email(message = "{signup.email.invalid}")
    private String email;
}
