package io.github.mawsonlakes790913.chineseoutputforge.validator;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;



public class PasswordMatchValidator
		implements ConstraintValidator<PasswordMatch, Object> {
	
	private String passwordFieldName;
	private String passwordConfirmFieldName;
	
	@Override
	public void initialize(PasswordMatch passwordMatch) {
		this.passwordFieldName = passwordMatch.passwordFieldName();
		this.passwordConfirmFieldName = passwordMatch.passwordConfirmFieldName();
	}
	
	@Override
	public boolean isValid(Object value,
            ConstraintValidatorContext context) {
		BeanWrapper beanWrapper = new BeanWrapperImpl(value);
		String password = (String) beanWrapper
                .getPropertyValue(this.passwordFieldName);
		
		String passwordConfirm = (String) beanWrapper
                .getPropertyValue(this.passwordConfirmFieldName);
		
        if (password == null || passwordConfirm == null) {
            return true;
        }
        
        if (!passwordConfirm.equals(password)) {
        	return false;
        }
        
        return true;
	}
}
