package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/**
 * メール送信に関する業務処理を行うService。
 * パスワード再設定用メールの作成・送信を行う。
 */
@Service
@RequiredArgsConstructor
public class MailService {
	
	@Value("${app.base-url}")
	private String baseUrl;
	private final JavaMailSender mailSender;
	
	/**
	 * 指定したメールアドレスへパスワード再設定用メールを送信する。
	 * 再設定用トークンからURLを作成し、メール本文に設定する。
	 *
	 * @param email 送信先メールアドレス
	 * @param token パスワード再設定用トークン
	 */
	public void sendPasswordResetEmail(
	        String email,
	        String token) {

	    // パスワード再設定用URLを作成
	    String url = baseUrl
	            + "/reset-password?token="
	            + token;

	    // メールの件名と本文を作成
	    String subject = "【Chinese Output Forge】パスワード再設定のご案内";
	    String text =
	            "パスワード再設定のリクエストを受け付けました。\n\n"
	            + "以下のURLから新しいパスワードを設定してください。\n\n"
	            + url + "\n\n"
	            + "このURLの有効期限は1時間です。\n"
	            + "パスワード再設定をリクエストしていない場合は、このメールを無視してください。";

	    // パスワード再設定用メールを作成
	    MimeMessage message = mailSender.createMimeMessage();
	    try {
	        // 送信先や件名などのメール情報を設定
	        MimeMessageHelper messageHelper = new MimeMessageHelper(message);
	        messageHelper.setFrom("liuniao790913@gmail.com");
	        messageHelper.setTo(email);
	        messageHelper.setText(text);
	        messageHelper.setSubject(subject);

	        // パスワード再設定用メールを送信
	        mailSender.send(message);

	    } catch (MessagingException e) {
	        // メール情報の設定に失敗した場合は例外をスロー
	        throw new RuntimeException(
	                "メッセージの設定に失敗しました",
	                e);
	    }
	}
}
