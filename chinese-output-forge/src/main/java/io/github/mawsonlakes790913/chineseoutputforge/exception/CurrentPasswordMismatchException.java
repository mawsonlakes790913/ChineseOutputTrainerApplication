package io.github.mawsonlakes790913.chineseoutputforge.exception;

/**
 * 現在のパスワードが一致しない場合に発生する例外。
 */
public class CurrentPasswordMismatchException extends RuntimeException {
	
    public CurrentPasswordMismatchException(String message) {
        super(message);
    }

}
