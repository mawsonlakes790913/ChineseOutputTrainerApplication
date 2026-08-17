package io.github.mawsonlakes790913.chineseoutputforge.form;

import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SignupForm {
	
	@NotBlank
	@Length(min = 8, max = 20)
	@Pattern(regexp = "^[a-zA-Z0-9]+$")
	private String loginId;
	
	@NotBlank
	@Length(min = 8, max = 20)
	@Pattern(regexp = "^[a-zA-Z0-9]+$")
	private String password;
	
	@NotBlank
	private String passwordConfirm;
}
