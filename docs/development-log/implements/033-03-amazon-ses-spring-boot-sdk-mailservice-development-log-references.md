# 033-03 Amazon SES導入③｜Spring BootへのAWS SDK導入とメール送信処理の変更（実装ログ）

## はじめに

前章までに、Amazon SESで独自ドメイン`chinese-output-forge.com`の送信元認証とDMARCのDNS設定、本番アクセス申請を行った。

本章では、Chinese Output Forgeのメール送信処理を、Gmailを送信元とする`JavaMailSender`方式から、**AWS SDK for Java 2.xを使用してAmazon SES API v2を呼び出す方式**へ変更した作業を記録する。

IAMロールへの権限付与、実際の送信テストおよびその際に発生したエラーの調査は、次章の033-04で扱う。

## 1. メール送信方式の決定

変更前は、`MailService`で`JavaMailSender`と`MimeMessageHelper`を使用し、開発用GmailアドレスからSMTPでメールを送信していた。

Amazon SESにはSMTPインターフェースとAPIの2つの利用方法がある。SMTP方式なら既存の`JavaMailSender`を維持できるが、今回はEC2のIAMロールを利用でき、長期的なSMTP認証情報を新たに管理する必要がない**SES API方式**を採用した。

```text
Spring Boot（MailService）
    ↓
AWS SDK for Java 2.x（SesV2Client）
    ↓
Amazon SES API v2
    ↓
メール送信
```

## 2. pom.xmlにAWS SDKを追加する

`pom.xml`の`dependencyManagement`にAWS SDK for Java 2.xのBOMを追加した。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.55.14</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

さらに`dependencies`にSES API v2用モジュールを追加した。

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>sesv2</artifactId>
</dependency>
```

Eclipseで **Maven → Update Project** を実行し、依存関係を更新した。

この変更と後述する`SesV2Client`のBean登録は、元ログでは次のコミットとして記録している。

```bash
git commit -m "feat: configure AWS SDK for SES v2 integration"
```

## 3. SesV2ClientをSpring Beanとして登録する

AWS公式サンプルでは`main()`内でクライアントを生成していたが、今回は`SesConfig`にBeanとして登録し、`MailService`へDIする構成にした。

**SesConfig.java**

```java
@Configuration
public class SesConfig {

    @Bean
    SesV2Client sesV2Client() {
        return SesV2Client.builder()
                .region(Region.AP_NORTHEAST_1)
                .build();
    }
}
```

リージョンは東京（`ap-northeast-1`）を指定した。認証情報はコードに埋め込まず、EC2に設定されたIAMロールをAWS SDKから利用する構成とした。

## 4. MailServiceの送信処理を変更する

変更前は`sendPasswordResetEmail()`と`sendSignupCompleteEmail()`の両方で`MimeMessage`を生成し、`mailSender.send()`を呼び出していた。

変更後は2つのpublicメソッドを維持し、件名・本文・送信先を作成した後、共通の`private send()`へ渡す構成にした。これにより、既存の呼び出し元のメソッド名を変更せずにメール送信方式を切り替えた。

**MailService.java（変更後・元ログのコード）**

```java
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

```

主な変更点は以下のとおり。

- `JavaMailSender`から、DIされた`SesV2Client`へ変更。
- `sendPasswordResetEmail()`と`sendSignupCompleteEmail()`は維持し、送信処理を`private send(String email, String subject, String text)`へ集約。
- `Destination`、`Content`、`Body`、`Message`、`EmailContent`を組み立て、`SendEmailRequest`を生成。
- 本番用の送信元を`noreply@chinese-output-forge.com`に変更。
- `sesV2Client.sendEmail(emailRequest)`を呼び出し、`SdkException`を捕捉した場合は`MailSendException`に変換。

元ログの変更コミットは以下のとおり。

```bash
git commit -m "refactor: migrate email sending from JavaMailSender to Amazon SES v2"
```

## 5. この時点での実装範囲

ここまでで、アプリケーション側のAWS SDK依存関係、`SesV2Client`のBean登録、`MailService`の送信処理変更を実施した。

**この段階でメールの送信成功まで確認したわけではない。** 実際の送信テストではIAM権限に関するエラーが発生しており、権限設定と修正後の確認は033-04で記録する。

## まとめ

Spring Bootのメール送信処理を、既存のSMTP方式からAmazon SES API v2方式へ変更した。送信メールの種類ごとのpublicメソッドは残し、共通送信処理だけを置き換えることで、既存のアプリケーション構成を大きく変更せずに移行した。

AWS公式サンプルの詳細な読み解き、`main()`とServiceの違い、BOMやDIの仕組みについては、学習ログで別途整理する。

## 参考文献

- [AWS公式：SES SMTPによるプログラムからの送信](https://docs.aws.amazon.com/ja_jp/ses/latest/dg/send-using-smtp-programmatically.html)
- [DevelopersIO：PostfixとAmazon SESの連携](https://dev.classmethod.jp/articles/use-aws-api-between-postfix-and-amazon-ses/)
- [Adways技術ブログ：SMTP方式とAPI方式の比較](https://blog.engineer.adways.net/entry/2025/08/27/120000)
- [AWS SDKのMaven依存関係に関する参考記事①](https://qiita.com/say87a/items/e5c341b4292ebc4f2026)
- [AWS SDKのMaven依存関係に関する参考記事②](https://style.potepan.com/articles/35409.html)
- [AWS公式：Java SDKの導入](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/get-started.html)
- [SES API実装時に参照した記事](https://qiita.com/bicep_sasa/items/618c8ec728677c625afa)
- [AWS公式：SES v2のJavaコード例](https://docs.aws.amazon.com/ja_jp/sdk-for-java/latest/developer-guide/java_sesv2_code_examples.html)
