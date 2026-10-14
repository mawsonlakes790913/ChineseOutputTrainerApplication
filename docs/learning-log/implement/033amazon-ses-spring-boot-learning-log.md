# 033 Amazon SESによる独自ドメインのメール送信環境構築とSpring Bootへの導入 — 学習ログ

本章では、Chinese Output Forgeのメール送信機能を、開発用GmailアドレスとSpring Mailを利用した構成から、独自ドメイン`chinese-output-forge.com`とAmazon SESを利用する構成へ移行した。メール認証の仕組み、AWS側の設定、Spring Bootの実装、動作確認で発見した問題と修正を振り返り、今回理解したことを整理する。

# 1. Amazon SESと独自ドメインの役割

## メールサーバーを自分で運用しない

Chinese Output Forgeでは、会員登録完了時やパスワード再設定時にメールを送信する。これまでは開発用Gmailアドレスを送信元としていたが、本番環境では`noreply@chinese-output-forge.com`を使用することにした。

Amazon SESはAWSのメール配信サービスであり、自分でSMTPサーバーを構築・管理しなくてもメールを送信できる。今回の構成では、Spring BootからSESへメール送信を依頼し、SESが外部の受信メールサーバーへ配送する。

## SESへのドメイン登録はドメインの新規取得ではない

`chinese-output-forge.com`はすでにRoute 53で管理していた。SESへこのドメインを登録する作業は、新しいドメインを購入することではなく、**そのドメインを送信元として使用する権限があることをDNSで検証する作業**である。

SESが提示するDNSレコードをRoute 53に登録し、SES側がその存在を確認することでドメイン認証が完了する。認証直後は「検証保留中」だったが、時間をおいて「検証済み」へ変わった。

ただし、ドメインが検証済みになっても、SESのサンドボックス制限やIAM権限の問題が自動的に解消するわけではない。**ドメインの検証、送信先の制限、アプリケーションの送信権限は別々の条件**である。

# 2. Header FromとEnvelope Fromを区別する

## メールには二つの送信元がある

メールには、受信者が画面で確認する`Header From`と、メールサーバー間の配送に使われる`Envelope From`がある。

今回の設定では、受信者に見える送信元は`noreply@chinese-output-forge.com`である。一方、配送に用いるEnvelope Fromには、カスタムMAIL FROMドメイン`mail.chinese-output-forge.com`が使われる。

- **Header From**：メールヘッダーに記載され、受信者に表示される送信元
- **Envelope From**：SMTPの`MAIL FROM`コマンドで指定され、配送やバウンス処理に用いられる送信元
- **Return-Path**：受信後にEnvelope Fromを確認できる場合があるヘッダー

この違いを理解することで、なぜ見た目の送信元アドレスだけではメール認証を説明できないのかが分かった。

## カスタムMAIL FROMを使用する理由

SESではカスタムMAIL FROMを設定しなくても送信でき、その場合はSESが管理するドメインがEnvelope Fromに使用される。

今回は`mail.chinese-output-forge.com`を設定した。Header FromとEnvelope Fromは完全に同じドメインではないが、組織ドメイン`chinese-output-forge.com`が共通しているため、DMARCのRelaxed Alignmentに関係する。

また、Envelope Fromは、宛先不明などでメールを配送できなかった場合のバウンスを処理するうえでも重要になる。

# 3. SPF・DKIM・MXレコードの役割

## SPFは送信サーバーを確認する

SPF（Sender Policy Framework）は、実際に接続してきた送信サーバーのIPアドレスが、Envelope From側のドメインからの送信を許可されているかを確認する仕組みである。

今回、Route 53には`mail.chinese-output-forge.com`のTXTレコードとして次の値を設定した。

```text
v=spf1 include:amazonses.com ~all
```

`include:amazonses.com`はAmazon SES側のSPFポリシーを参照する指定であり、`~all`はそれ以外の送信元をSoftFailとして扱う指定である。

**SPFが直接確認するのは、通常、受信者が見るHeader Fromではなく、Envelope Fromのドメインと送信元IPアドレスの関係**である。この違いが、後述するDMARCの必要性につながる。

## DKIMは電子署名を確認する

DKIM（DomainKeys Identified Mail）は、送信側が秘密鍵でメールに電子署名を付け、受信側が公開鍵を取得して署名を検証する仕組みである。

今回のEasy DKIMではSESが鍵を管理するため、自分で鍵を生成・保管する必要はない。SESが提示した3件のCNAMEレコードをRoute 53へ登録した。

DKIM署名には、署名したドメインを示す`d`と、鍵を識別するセレクター`s`が含まれる。受信側はそれらから`セレクター._domainkey.ドメイン`というDNS名を作り、CNAMEの参照先であるSES側のDNSから公開鍵を取得する。

3件のCNAMEを1通のメールで同時に使うわけではなく、署名に記載されたセレクターに対応するレコードを参照する。

## MXは配送先を指定する

カスタムMAIL FROMドメインには、次のMXレコードも設定した。

```text
10 feedback-smtp.ap-northeast-1.amazonses.com
```

MXは、そのドメイン宛てのメールをどのメールサーバーへ配送するかを指定するレコードである。今回のMXは、Envelope From宛てに返されるバウンスをSESへ届けるために使う。

**同じ`mail.chinese-output-forge.com`に設定したTXTとMXでも、前者はSPF認証、後者は配送先の指定という異なる役割**を持つ。

# 4. DMARCによるドメイン整合性の確認

## SPFやDKIMがPASSするだけでは不十分

SPFがPASSしても、攻撃者が自分のドメインをEnvelope Fromに使い、Header Fromだけを`chinese-output-forge.com`に偽装する可能性がある。同様に、攻撃者自身のドメインによるDKIM署名がPASSすることもあり得る。

DMARCは、SPFまたはDKIMで認証されたドメインと、受信者に表示されるHeader Fromのドメインとの整合性（Alignment）を確認する。

今回の正規メールでは、SPF側のEnvelope Fromが`mail.chinese-output-forge.com`、DKIMの署名ドメインが`chinese-output-forge.com`となるため、Header Fromとの整合性を満たせる構成になっている。

## 最初はp=noneから開始する

Route 53に次のTXTレコードを追加した。

```text
レコード名：_dmarc.chinese-output-forge.com
値：v=DMARC1; p=none;
TTL：300秒
```

DMARCには`none`、`quarantine`、`reject`のポリシーがある。今回は実際のメール送信で認証結果を十分に確認する前だったため、最初から隔離や拒否を要求せず、`p=none`から始めた。

**認証の仕組みを導入することと、認証失敗メールの拒否を要求することは別段階**である。今回の`p=none`は、DMARCに失敗したメールの隔離・拒否を受信側へ要求しない設定である。

# 5. SESサンドボックスの解除とAWSによる審査

## ドメイン認証後にも送信先制限が残る

SESのサンドボックス環境では、送信先が検証済みアドレスなどに制限される。Chinese Output Forgeでは、登録ユーザーが入力したメールアドレスへパスワード再設定メールを送る必要があるため、本番アクセスを申請した。

申請では、送信するメールの種類をトランザクションメールとし、公開済みWebサイトのURLを提出した。

## 追加審査で求められた情報

AWSから追加情報を求められたため、次の内容を説明した。

- 送信対象は、ユーザー自身が登録したメールアドレスのみであること
- メールはパスワード再設定など、ユーザーの操作を契機としたシステムメールに限ること
- 当初の送信量は1日数通程度を想定していること
- マーケティングメールや購入したメールアドレスのリストは使用しないこと
- バウンスや苦情はSESの機能・抑制リストなどで管理する方針であること
- 実際に送信するメールの内容例

追加情報を提出し、最終的に本番アクセスの承認を得た。

この経験から、SESの本番アクセス申請では、単に技術的に送信できるかだけでなく、**誰に何のためにメールを送るのか、配信失敗や苦情をどう扱うのか**も確認されると分かった。

# 6. SMTP方式とAWS SDK方式の比較

## 既存のJavaMailSenderを維持する選択肢

移行前の`MailService`では、Spring Mailの`JavaMailSender`と`MimeMessageHelper`を使用していた。SESにもSMTPインターフェイスがあるため、SMTPエンドポイントと認証情報を設定すれば、既存の送信処理を大きく変更せずに利用できる。

ただし、その場合はSMTPユーザー名・パスワードなどの認証情報を管理する必要がある。

## AWS SDK方式を採用した理由

今回のEC2にはすでにIAMロールが設定されていたため、AWS SDK for Java 2.xの`SesV2Client`を使用してSES APIを呼び出す方式を採用した。

この方式では、EC2のIAMロールを利用してAWS認証情報を取得できるため、SMTP用の長期的な認証情報を新たに作成・管理する必要がない。

その代わり、`MailService`の実装を`JavaMailSender`からSES APIへ変更する必要があった。

**既存コードの変更量が少ない方法と、AWS上での認証情報管理に適した方法は必ずしも一致しない**。今回は後者を優先した。

# 7. IAMポリシーとAWS SDKの実装

## EC2のIAMロールに送信権限を追加する

既存のEC2インスタンスロールに、インラインポリシー`SESSendEmailPolicy`を追加した。送信に関係するアクションとして`ses:SendEmail`と`ses:SendRawEmail`を許可した。

IAMポリシーの`Action`は許可するAWS操作を、`Resource`はその操作を許可する対象リソースを指定する。

今回のポリシーでは`"Resource": "*"`を設定した。これはIAMによるリソースの限定を行わないという意味であり、**SESのドメイン検証要件やサンドボックス制限まで無効にするわけではない**。

また、メール送信の権限を付与しても、SES Identityの作成・削除や送信統計の取得といった管理操作を自動的に許可することにはならない。

## AWS SDKのBOMとモジュール

`pom.xml`では、AWS SDK for Java 2.xのBOMを`dependencyManagement`に追加し、必要なSES SDKモジュールを依存関係へ追加した。

BOMは複数の関連ライブラリのバージョンを統一して管理するための仕組みであり、BOMを追加すること自体が、利用するすべてのSDKモジュールをアプリケーションへ取り込むことを意味するわけではない。

## 公式サンプルをSpring BootのServiceへ組み込む

AWS公式サンプルは`main()`から実行する独立したJavaプログラムだったため、そのまま`MailService`へコピーするのではなく、処理を分解して理解した。

- `main()`：送信元・送信先などの準備とクライアント生成
- `send()`：送信リクエストの組み立て
- `client.sendEmail()`：SES APIの呼び出しと例外処理

コマンドライン引数の処理は不要であり、送信先や本文は既存のServiceの引数・変数を再利用できる。

`SesV2Client`は毎回生成するのではなく、`SesConfig`でSpringのBeanとして登録し、`MailService`へDIする構成にした。これにより、クライアント生成とメール送信処理の責務を分けられた。

# 8. 実際の送信テストとURL・HTTPSの問題

## メール送信とリンク先の動作は別々に確認する

SESを利用したパスワード再設定メールの送信を確認したところ、メールに記載されたリンクを開いてもページにアクセスできなかった。

調査の結果、問題は二つあった。

まず、Elastic Beanstalkの環境変数`APP_BASE_URL`が、名前解決できない旧URLを参照していた。このため`DNS_PROBE_FINISHED_NXDOMAIN`が発生した。

`APP_BASE_URL`を`https://chinese-output-forge.com`へ変更すると、今度は`ERR_CONNECTION_REFUSED`が発生した。

## HTTPSの設定ファイルが消失していた

EC2のSecurity Groupでは443番ポートを許可していたが、nginxは80番ポートでしか待ち受けていなかった。以前EC2上へ手動で作成した`/etc/nginx/conf.d/https.conf`が存在しなくなっていたためである。

再デプロイで消失した可能性はあったが、原稿では消失の直接原因までは特定できていない。

設定ファイルを再作成し、`nginx -t`で構文を検証してから再読み込みした。さらに待ち受けポートを確認し、80番・443番の両方でnginxがLISTENしていることを確認した。

その後、再送信したメールのリンクからパスワード再設定ページへ正常にアクセスできた。

**メールが届くこと、URLが正しいこと、HTTPSで接続できること、アプリケーションの画面が表示されることは、それぞれ独立して検証する必要がある**と分かった。

# 9. Elastic BeanstalkでnginxのHTTPS設定を永続化する

## EC2上への手動設定は再デプロイに弱い

HTTPS設定をEC2上で再作成すれば一時的には復旧できる。しかし、再デプロイ時に再び設定が消失する可能性があるため、手動設定に依存しない方法へ変更した。

AWSのドキュメントを調べ、Elastic Beanstalkのnginx設定は、アプリケーションのソースバンドル内の`.platform/nginx/conf.d/`へ配置する方法があると理解した。

## JARの中に入れるだけでは反映されない

最初はプロジェクトに`https.conf`を追加し、JAR内に含まれているかを確認した。しかし、JARの内容を調べても設定ファイルは見つからなかった。

ここで、Spring Bootが実行するJARと、Elastic Beanstalkがデプロイ時に参照するファイルは別物であると理解した。

**Elastic BeanstalkはJARの内部を解析してnginx設定を適用するわけではない**ため、`.platform`ディレクトリはJARの外側に配置する必要がある。

## JARと.platformをZIPでまとめる

最終的には、`application.jar`と`.platform/nginx/conf.d/https.conf`を同じZIPファイルに含め、Elastic Beanstalkの「アップロードとデプロイ」から反映した。

ZIPの中身を確認してからデプロイし、独自ドメインへのHTTPSアクセス、パスワード再設定メールの送信、メール内リンクからのページ遷移を検証した。原稿ではこの3点すべての成功を確認している。

この修正によって、**アプリケーション本体と、そのアプリケーションを動かすためのWebサーバー設定を、同じデプロイ成果物として管理する**重要性を学んだ。

# 10. メール送信失敗時の例外処理と画面表示

## 会員登録とパスワード再設定では失敗時の扱いが違う

SES APIを呼び出す実装では、送信に失敗した場合の例外を`MailService`側で扱い、Controllerでユーザー向けのエラーメッセージを表示する構成を整えた。

会員登録の場合は、登録処理そのものが成功していれば、その結果を維持しながら登録完了メールの送信失敗を通知する。

一方、パスワード再設定では、メールを送信できなかったのに送信完了画面へ遷移すると、ユーザーが実際には受け取れないメールを待つことになる。そのため、メールアドレス入力画面へ戻して送信失敗を表示する。

**同じメール送信エラーでも、直前に完了した業務処理によって、適切な画面遷移が異なる**。

## Thymeleafと多言語メッセージを整備する

`SignupController`と`PasswordResetController`で例外発生時のModel属性を設定し、Thymeleaf側でエラーメッセージを表示するようにした。

また、`messages.properties`へ日本語・簡体字中国語・繁体字中国語のメッセージを追加した。

今回の確認では、Controllerの処理、Thymeleafの表示条件、メッセージ定義の整合性を確認し、Mavenビルドも実施した。

ただし、**SESの送信エラーを意図的に発生させた画面テストは実施していない**。コードと設定の確認が完了したことと、例外発生時の表示を実際に検証したことは区別して記録する。

# 11. 第31〜33章を通じた学習の総括

第31章ではVPC、サブネット、ALB、Auto Scaling、Multi-AZ RDSなどを組み合わせた高可用性構成を構築した。第32章では、稼働するサービスの規模とコストに合わせてSingle Instance・Single-AZ構成へ移行し、DNS、HTTPS、監視、DB接続を再確認した。

そして第33章では、アプリケーションから送るメールについても、ドメイン認証、配送、IAM権限、SDK、例外処理まで含めた本番環境向けの構成へ変更した。

今回特に重要だったのは、AWSの各サービスを個別に設定するだけでは、アプリケーション全体の正常動作は保証されないという点である。

SESからメールを送信できても、メール内のURLが誤っていればユーザーは目的の画面へ到達できない。DNSの設定が正しくても、nginxが443番ポートで待ち受けていなければHTTPS通信は成立しない。EC2上で手動修正して動作しても、次のデプロイで設定が失われるなら安定した運用とはいえない。

**ネットワーク、DNS、メール認証、IAM、Spring Boot、nginx、Elastic Beanstalkの設定が互いに関係していることを、実際の不具合と修正を通じて理解できた。**

これをもって、Chinese Output Forgeの初期開発とAWSへの本番デプロイに関する学習ログを締めくくる。
