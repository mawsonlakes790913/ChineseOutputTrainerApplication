package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class MailService {
	
	@Value("${app.base-url}")
	private String baseUrl;
	private final JavaMailSender mailSender;
	
	public void sendPasswordResetEmail(
	        String email,
	        String token) {

	    // パスワードリセットURL作成(ローカル環境)
	    String url = baseUrl
	            + "/reset-password?token="
	            + token;
	    
	    // 件名と本文を取得
	    String subject = "【Chinese Output Forge】パスワード再設定のご案内";

	    String text =
	            "パスワード再設定のリクエストを受け付けました。\n\n"
	            + "以下のURLから新しいパスワードを設定してください。\n\n"
	            + url + "\n\n"
	            + "このURLの有効期限は1時間です。\n"
	            + "パスワード再設定をリクエストしていない場合は、このメールを無視してください。";
	    
	    // email宛にメールを送信する
	    MimeMessage message = mailSender.createMimeMessage();
	    try {
	    	MimeMessageHelper messageHelper = new MimeMessageHelper(message);
	    	messageHelper.setFrom("liuniao790913@gmail.com");
            messageHelper.setTo(email);
            messageHelper.setText(text);
            messageHelper.setSubject(subject);
	    	
            mailSender.send(message);
        } catch(MessagingException e) {
            throw new RuntimeException("メッセージの設定に失敗しました", e);
        }
	    
	}
	



}
