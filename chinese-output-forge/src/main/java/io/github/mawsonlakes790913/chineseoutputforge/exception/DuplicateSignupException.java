package io.github.mawsonlakes790913.chineseoutputforge.exception;

public class DuplicateSignupException extends RuntimeException {

private final String field;

	public DuplicateSignupException(
	    String field,
	    String message) {
	
		super(message);
		this.field = field;
	}
	
	public String getField() {
		return field;
	}
}
