package io.github.mawsonlakes790913.chineseoutputforge.exception;

/**
 * ユーザー登録時に入力項目の重複が検出された場合に発生する例外。
 */
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
