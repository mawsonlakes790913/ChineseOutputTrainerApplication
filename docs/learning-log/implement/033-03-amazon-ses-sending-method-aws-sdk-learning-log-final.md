# 033-03 Amazon SESの送信方式とAWS SDKの仕組み（学習ログ）

## はじめに

033-01・033-02では、Amazon SESを利用するためのドメイン認証、SPF・DKIM・DMARC、サンドボックス解除を整理した。しかし、SES側で送信できる環境が整っても、Spring Bootアプリケーションが**どの方式でSESにメール送信を依頼するか**は別に決める必要があった。

ここでは、送信方式の比較、MavenのBOM、AWS公式サンプルの読み解き、Spring BootのServiceへの組み込み方を整理する。編集したファイルやコード、コミットなどは[033-03実装ログ](./033-03-amazon-ses-spring-boot-sdk-mailservice-development-log.md)に記録する。

## 1. Amazon SESにはSMTP方式とAPI方式がある

### 既存のJavaMailSenderを使うSMTP方式

移行前の`MailService`は、Spring Mailの`JavaMailSender`と`MimeMessageHelper`を使用していた。送信処理は`mailSender.send(message)`であり、開発用Gmailアドレスを送信元に指定していた。

Amazon SESはSMTPインターフェースを提供しているため、既存の`JavaMailSender`を維持し、接続先をSESのSMTPエンドポイントへ変更する方法がある。東京リージョンの例は次のとおりである。

```text
email-smtp.ap-northeast-1.amazonaws.com
```

この方法では、SMTPエンドポイント・ポート番号のほか、**SMTPユーザー名とSMTPパスワード**をアプリケーションから利用できるようにする必要がある。エンドポイントとポート番号自体は秘密情報ではないが、ユーザー名とパスワードは秘密情報として管理しなければならない。

```text
Spring Boot → MailService → JavaMailSender
             → SES SMTPエンドポイント → Amazon SES
```

既存コードの変更量が少ない点は利点だが、SMTP認証情報を別途作成・保管することになる。

### AWS SDKからSES APIを呼び出す方式

もう一つは、AWS SDK for Java 2.xを使って、`SesV2Client`からAmazon SES API v2を呼び出す方法である。

```text
Spring Boot → MailService → SesV2Client
             → Amazon SES API v2 → Amazon SES
```

こちらは`JavaMailSender`を利用する既存の送信処理を書き換える必要がある。一方、AWS SDKには実行環境からAWSの認証情報を取得する仕組みがあり、EC2にIAMロールが付いていれば、そのロールに基づく一時的な認証情報を利用できる。

今回のEC2にはすでにIAMロールが設定されていた。そのため、**SMTP用の長期的な認証情報を新たに管理しないこと**を優先し、SES API方式を採用した。

| 比較項目 | SES SMTP方式 | SES API方式（今回） |
|---|---|---|
| Spring Boot側の送信手段 | `JavaMailSender` | `SesV2Client` |
| 既存の`MailService` | 比較的小さな変更で移行可能 | 送信処理の変更が必要 |
| 接続先 | SES SMTPエンドポイント | SES API |
| 認証情報 | SMTPユーザー名・パスワード | EC2のIAMロールを利用できる |
| 今回の判断 | 採用しない | **採用** |

**コードの変更が少ない方法と、実行環境に適した認証情報の管理方法は必ずしも同じではない**ことが、この比較で理解した点だった。なお、IAMロールへの具体的な権限付与は033-04で扱う。

## 2. AWS SDK for Java 2.xとMavenの依存関係

### AWS SDKはサービスごとにモジュールが分かれている

AWS SDK for Java 2.xは、JavaからAWSサービスのAPIを利用するためのライブラリである。今回はAmazon SES API v2を使うため、`software.amazon.awssdk`グループの`sesv2`モジュールを追加した。

最初に`pom.xml`の`dependencyManagement`へBOMを追加し、続いて`dependencies`へ必要なモジュールを追加した。

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

### BOMは「依存関係の追加」ではなく「バージョンの管理」

BOM（Bill of Materials）は、関連するライブラリのバージョンをまとめて管理するための仕組みである。AWS SDKにはSES以外にも複数のサービス用モジュールがあるため、個々の依存関係にバージョンを記載する代わりに、BOMで整合したバージョンを管理できる。

ただし、**BOMを登録しただけでは`sesv2`が使えるようにはならない**。実際に利用するモジュールを別途`dependencies`へ追加する。

```xml
<dependencies>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sesv2</artifactId>
    </dependency>
</dependencies>
```

この記述で`version`を省略できるのは、BOMでバージョンを管理しているためである。`groupId`はAWS SDK for Java 2.xのグループ、`artifactId`は使用するモジュールを示す。原稿では、以前のAWS SDKの`aws-java-sdk-{サービス名}`という形式と、2.xのモジュール名の違いも確認していた。

`pom.xml`を編集した後はEclipseの**Maven → Update Project**で依存関係を更新した。ここは実際の操作なので実装ログに残し、学習ログでは**BOMとモジュールの役割の違い**を中心に記録する。

## 3. AWS公式サンプルをそのままコピーしなかった理由

### 公式サンプルは単独実行するJavaプログラム

AWS公式の`SendEmail`サンプルを読み、処理を次の三つに分解した。

1. `main()`：引数の確認、送信情報の準備、`SesV2Client`の生成
2. `send()`：宛先・件名・本文から`SendEmailRequest`を組み立てる
3. `client.sendEmail()`：SES APIへ送信を依頼し、例外を扱う

公式サンプルでは、`main(String[] args)`で送信元・送信先・件名をコマンドライン引数から取得し、リージョンやHTML本文を設定してから`send()`を呼び出す。しかし、Chinese Output Forgeではメールの送信先や本文を既存の`MailService`が生成しており、コマンドライン引数は不要である。

| 公式サンプルの要素 | 今回のServiceでの扱い | 理由 |
|---|---|---|
| `String usage`、`args.length` | 削除 | コマンドラインから実行しない |
| `sender` | 独自ドメインの送信元を指定 | Gmailから切り替える |
| `recipient` | 既存の引数`email`を使う | 送信先は呼び出し元から受け取る |
| `subject` | 既存の件名を使う | メール種別ごとに作成済み |
| `bodyHTML` | 既存の`text`を使う | 今回のメールはプレーンテキスト |
| `SesV2Client`生成 | `SesConfig`へ移す | SpringのBeanとして管理する |
| `send()` | Service内の共通処理へ組み込む | 二種類のメールで再利用する |

重要なのは、**公式サンプルの目的はAPIの呼び出し方を示すことであり、既存のSpring Bootアプリケーションにそのまま組み込む設計を示すものではない**という点である。

## 4. SesV2Clientをどこで生成するか

### Service内で生成するか、Beanとして登録するか

公式サンプルでは`main()`で`SesV2Client.builder().region(...).build()`を呼び出していた。これを`MailService`内で行う案と、Spring Beanとして登録する案を比較した。

今回は`SesConfig`という設定クラスを作り、`SesV2Client`をBeanとして登録する方式を採用した。

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

- `@Configuration`：Springの設定クラスとして扱う。
- `@Bean`：メソッドが返すオブジェクトをSpringの管理対象にする。
- `SesV2Client.builder()`：SES API v2用クライアントのBuilderを取得する。
- `.region(Region.AP_NORTHEAST_1)`：東京リージョンを指定する。
- `.build()`：クライアントを生成する。

Beanはデフォルトでシングルトンスコープなので、通常は同じクライアントを再利用できる。`MailService`側では次のように受け取る。

```java
@Service
@RequiredArgsConstructor
public class MailService {
    private final SesV2Client sesV2Client;
}
```

`@RequiredArgsConstructor`によって必要なコンストラクタが生成され、Springが登録済みのBeanをDI（依存性注入）する。**クライアントの生成と管理を設定クラスへ任せ、`MailService`はメール送信の処理に集中させる**という責務の分離が、採用理由の一つだった。

なお、今回のコードにはアクセスキーやシークレットキーを直接記述していない。AWS SDKが実行環境から認証情報を取得する構成を利用するためであり、IAMロールによる認証・認可の詳細は033-04へ引き継ぐ。

## 5. Builderでメール送信リクエストを組み立てる

公式サンプルの`send()`では、送信に必要な情報を一つの`SendEmailRequest`へまとめる。ここでは、この処理を一段ずつ分解して確認していた。

```java
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
        .fromEmailAddress("noreply@chinese-output-forge.com")
        .build();
```

| クラス | 今回の役割 |
|---|---|
| `Destination` | `toAddresses(email)`で送信先を指定 |
| `Content` | 本文の`text`と件名の`subject`をそれぞれ保持 |
| `Body` | `.text(content)`でプレーンテキスト本文を指定 |
| `Message` | 件名と本文をまとめる |
| `EmailContent` | `.simple(msg)`で通常のメール内容を設定 |
| `SendEmailRequest` | 送信先・内容・送信元を一つのAPIリクエストにまとめる |

Builderパターンでは、`builder()`で設定用オブジェクトを取得し、必要な値を指定してから`build()`で完成したオブジェクトを作る。各オブジェクトが次のオブジェクトに渡される順序を追うことで、**単に長いコードを暗記するのではなく、SESに必要なリクエストの構造を理解する**ことを重視していた。

公式サンプルでは`.html(content)`を使用していたが、今回は既存のメール本文がプレーンテキストであるため`.text(content)`へ変更した。送信元・送信先・件名・本文の組み立てという基本構造は変わらない。

## 6. `sendEmail()`の成功とメールの配信完了は違う

リクエストを組み立てたら、次のメソッドでAmazon SESへ送信を依頼する。

```java
sesV2Client.sendEmail(emailRequest);
```

公式サンプルの`client.sendEmail(emailRequest)`と、成功時の`System.out.println("email was sent")`、失敗時の`catch (SesV2Exception e)`をそれぞれ検討した。

ここで理解した重要な点は、**`sendEmail()`が例外を出さずに終了したことは、SESがリクエストを受け付けたことを意味するのであって、受信者のメールボックスへの配信完了を保証するわけではない**ということだった。メール送信処理の成功と、実際の配送結果は分けて考える必要がある。

また、公式サンプルは単独実行するプログラムなので、エラー時に`System.err.println()`で表示したり、`System.exit(1)`で終了したりする。しかし、WebアプリケーションのServiceで`System.exit(1)`を使うと、メール送信処理だけでなくアプリケーション全体を終了させることになる。

そこで今回の`MailService`では、SDK由来の例外を捕捉し、アプリケーション側の`MailSendException`へ変換する方式を採用した。

```java
try {
    sesV2Client.sendEmail(emailRequest);
} catch (SdkException e) {
    throw new MailSendException(
            "Amazon SESでのメール送信に失敗しました", e);
}
```

公式サンプルの`SesV2Exception`と、今回使用した`SdkException`の違いにも注意が必要である。前者はSESサービス側の例外を表し、後者はAWS SDKの例外としてより広い範囲を捕捉する。**例外をServiceでどう扱い、Controllerからどの画面へ戻すか**は033-06で扱う。

## 7. 既存のpublicメソッドを残し、送信処理だけを共通化する

Chinese Output Forgeには、パスワード再設定メール用の`sendPasswordResetEmail()`と、会員登録完了メール用の`sendSignupCompleteEmail()`があった。今回の移行ではこれらを残し、件名と本文を作成した後に、共通の`private send(email, subject, text)`を呼ぶ構成とした。

```text
sendPasswordResetEmail(email, token) ─┐
                                      ├─ private send(email, subject, text)
sendSignupCompleteEmail(email, id) ───┘         ↓
                                      SendEmailRequestを生成
                                              ↓
                                      sesV2Client.sendEmail(...)
```

これにより、メールの種類ごとの本文生成処理と、Amazon SESへ送信する処理を分離できる。また、呼び出し元のControllerなどが利用するpublicメソッド名を変えずに、送信基盤をGmail/SMTPからSES APIへ切り替えられた。

## まとめ

今回の学習で中心となったのは、**SMTPとSES APIの選択が、単なる送信方法の違いだけでなく、認証情報の管理やSpring Boot側の設計に関係する**という点である。

既存の`JavaMailSender`を維持する方法もあったが、EC2のIAMロールを利用できることからAWS SDK方式を選択した。そのうえで、BOMとモジュールの違い、公式サンプルの`main()`とServiceの違い、Builderによるリクエスト構築、Bean・DIによるクライアント管理を整理した。

ここまでの段階では、**AWS SDKの導入と`MailService`の変更を理解・実装したこと**が対象である。IAMの具体的な権限設定と実際の送信テスト、その際に見つかったURL・HTTPSの問題は、次の033-04学習ログで扱う。

## 参考文献

以下は今回の学習で参照した資料である。

- [AWS公式：SES SMTPインターフェースからの送信](https://docs.aws.amazon.com/ja_jp/ses/latest/dg/send-using-smtp-programmatically.html)
- [DevelopersIO：PostfixとAmazon SES](https://dev.classmethod.jp/articles/use-aws-api-between-postfix-and-amazon-ses/)
- [Adways技術ブログ：SMTP方式とAPI方式](https://blog.engineer.adways.net/entry/2025/08/27/120000)
- [AWS SDKのMaven依存関係に関する参考記事①](https://qiita.com/say87a/items/e5c341b4292ebc4f2026)
- [AWS SDKのMaven依存関係に関する参考記事②](https://style.potepan.com/articles/35409.html)
- [AWS公式：SDK for Javaの導入](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/get-started.html)
- [SES API実装時に参照した記事](https://qiita.com/bicep_sasa/items/618c8ec728677c625afa)
- [AWS公式：SES v2のJavaコード例](https://docs.aws.amazon.com/ja_jp/sdk-for-java/latest/developer-guide/java_sesv2_code_examples.html)
