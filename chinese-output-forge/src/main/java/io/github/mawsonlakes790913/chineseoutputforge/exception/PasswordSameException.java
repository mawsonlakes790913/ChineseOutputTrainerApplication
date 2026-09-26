package io.github.mawsonlakes790913.chineseoutputforge.exception;

/**
 * 新しいパスワードが現在のパスワードと同じ場合に発生する例外。
 */
public class PasswordSameException extends RuntimeException {
	
    public PasswordSameException(String message) {
        super(message);
    }
} 