package io.github.mawsonlakes790913.chineseoutputforge.exception;

/**
 * メールの作成または送信に失敗した場合にスローされる例外。
 */
public class MailSendException extends RuntimeException {

    public MailSendException(
            String message,
            Throwable cause) {

        super(message, cause);
    }
}
