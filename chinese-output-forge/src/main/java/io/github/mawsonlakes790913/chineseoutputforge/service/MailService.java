package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.exception.MailSendException;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * メール送信に関する業務処理を行うService。
 * パスワード再設定用メールや会員登録完了メールの作成・送信を行う。
 */
@Service
@RequiredArgsConstructor
public class MailService {
	
	@Value("${app.base-url}")
	private String baseUrl;
	private final SesV2Client sesV2Client;
	
	/**
	 * 指定したメールアドレスへパスワード再設定用メールを送信する。
	 * 再設定用トークンからURLを作成し、メール本文に設定する。
	 *
	 * @param email 送信先メールアドレス
	 * @param token パスワード再設定用トークン
	 * @throws MailSendException メール送信に失敗した場合
	 */
    public void sendPasswordResetEmail(String email, String token) {

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


        send(email, subject, text);
    }
    
    /**
     * 指定したメールアドレスへ会員登録完了メールを送信する。
     * ログインIDとログイン画面のURLをメール本文に設定する。
     *
     * @param email 送信先メールアドレス
     * @param loginId 登録したユーザーのログインID
     * @throws MailSendException メール送信に失敗した場合
     */
    public void sendSignupCompleteEmail(String email, String loginId) {

	    // ログイン画面のURLを作成
	    String url = baseUrl + "/login";

	    // メールの件名と本文を作成
	    String subject = "【Chinese Output Forge】会員登録完了のお知らせ";
	    String text =
	            "Chinese Output Forgeへの会員登録が完了しました。\n\n"
	            + "ログインID：" + loginId + "\n\n"
	            + "以下のURLからログインしてください。\n\n"
	            + url;

        send(email, subject, text);
    }
	

	
    /**
     * Amazon SES API v2を使用してメールを送信する共通処理。
     * 送信先・件名・本文から送信リクエストを作成し、SESに送信する。
     *
     * @param email 送信先メールアドレス
     * @param subject メールの件名
     * @param text メールの本文
     * @throws MailSendException メール送信に失敗した場合
     */
    private void send(String email, String subject, String text) {

    	String sender = "noreply@chinese-output-forge.com";

        Destination destination = Destination.builder()
                .toAddresses(email)
                .build();

        Content content = Content.builder()
                .data(text)
                .build();

        Content sub = Content.builder()
                .data(subject)
                .build();

        Body body = Body.builder()
                .text(content)
                .build();

        Message msg = Message.builder()
                .subject(sub)
                .body(body)
                .build();

        EmailContent emailContent = EmailContent.builder()
                .simple(msg)
                .build();

        SendEmailRequest emailRequest = SendEmailRequest.builder()
                .destination(destination)
                .content(emailContent)
                .fromEmailAddress(sender)
                .build();

        try {
            sesV2Client.sendEmail(emailRequest);

        } catch (SdkException e) {
            throw new MailSendException(
                    "Amazon SESでのメール送信に失敗しました",
                    e);
        }
    }
}
