# 033-04 IAMロールによる認証・認可とメール送信テスト（学習ログ）

## はじめに

033-03では、Spring Bootの`MailService`からAWS SDK for Java 2.xの`SesV2Client`を使ってAmazon SES APIを呼び出す方式を選んだ。しかし、SDKを導入しただけでは、EC2上のアプリケーションがSESへメール送信できるとは限らない。**AWSへの認証と、IAMによる操作の許可は別の問題**だからである。

ここでは、IAMポリシーの読み方、送信先検証の意味、送信テストで発生した接続エラーの切り分けを整理する。画面操作・コマンド・スクリーンショットは[033-04実装ログ](./033-04-amazon-ses-iam-send-test-development-log.md)に記録する。

## 1. AWS SDKを導入してもSESの操作権限は付かない

今回のSpring Bootアプリケーションは、Elastic Beanstalkで稼働するEC2上から`SesV2Client.sendEmail(...)`を呼び出す。EC2には`chinese-output-forge-ec2-role`というIAMロールが設定されていた。AWS SDKは実行環境から認証情報を取得できるため、アプリケーションにSMTPユーザー名やパスワードを埋め込まずにSES APIを利用できる。

ただし、**IAMロールが付いていることと、SESの送信操作が許可されていることは同じではない**。既存ロールにはSSMやElastic Beanstalk Web Tier関連の管理ポリシーが付いていたが、SESのメール送信権限は含まれていなかった。そのため、送信操作を明示的に許可するインラインポリシーを追加した。

```text
Spring Boot / MailService
    ↓
SesV2Client.sendEmail(...)
    ↓
EC2のIAMロールに基づく認証情報を利用
    ↓
IAMによってSES送信操作が許可されるか評価
    ↓
許可されていればSES APIへ送信を依頼
```

ここで整理したかったのは、**認証情報の取得**、**IAMの認可**、**SES側の送信条件**という三つの層である。

## 2. IAMポリシーのJSONをどう読むか

今回、既存ロールに追加した`SESSendEmailPolicy`は以下の内容だった。

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ses:SendEmail",
        "ses:SendRawEmail"
      ],
      "Resource": "*"
    }
  ]
}
```

### `Version`と`Statement`

`Version`の`2012-10-17`は**IAMポリシー言語のバージョン**を示すもので、今回ポリシーを作成した日付ではない。`Statement`にはアクセス制御のルールを記述する。複数のルールを記述できるため配列`[]`になっている。

### `Effect`と`Action`

`Effect`はそのルールが操作を許可するか拒否するかを表し、今回の`Allow`は許可を意味する。`Action`は、**どのAWSサービスのどの操作**を対象にするかを指定する。

```text
ses:SendEmail
 │      │
 │      └─ メール送信操作
 └─ Amazon SES
```

今回の`Action`には`ses:SendEmail`と`ses:SendRawEmail`が含まれる。前者はSESのメール送信、後者はMIME形式のメールを扱う送信操作に対応する。Spring Bootから実際に呼び出す`SesV2Client.sendEmail(...)`は、**アプリケーション側のAPI呼び出し**であり、IAMポリシーの`ses:SendEmail`は、**その操作を許可する権限の指定**である。この二つを混同しないことが重要だった。

### `Resource`の`*`は何を意味するか

`Resource`は、`Action`で指定した操作を**どのAWSリソースに対して許可するか**を定める。今回の`"Resource": "*"`は、IAMポリシー上では特定のSES IdentityやConfiguration Setなどの対象に限定しないという意味である。

ただし、**すべての送信元・送信先へ無条件に送信できるという意味ではない**。SES Identityの検証要件やサンドボックスの送信先制限は別に存在する。また、このポリシーで許可しているのは送信操作であり、SES Identityの作成・削除や送信統計の取得などの管理操作まで自動的に許可されるわけではない。

今回の実装ログに記録されているのは`Resource: "*"`を指定した構成であり、送信元IdentityのARNへ制限する最小権限化を実施した記録はない。実施していない変更を学習成果として扱わない。

## 3. SESのサンドボックスとIAMの権限は別の制限

送信テストの時点では、東京リージョンのSES本番アクセス申請が審査中だった。そのため、IAMロールに送信権限を追加しても、SESのサンドボックスによる送信先制限は残っていた。

そこで、テストに使用するGmailアドレスをSESへ登録し、受信した検証メールから認証して、IDステータスが「検証済み」になったことを確認した。

```text
SES送信元ドメインの検証
    ≠ IAMによるSES送信操作の許可
    ≠ サンドボックスでの送信先検証
```

同じ「検証済み」や「送信できる」という表現でも、**何が確認され、どの制限が解除されたのか**を分けて考える必要がある。

## 4. 送信テストで失敗したのはSES APIではなくリンク先へのアクセスだった

検証済みアドレスを使ってパスワード再設定メールの送信を試した。メールに記載されたリンクを開くと、パスワード再設定ページへアクセスできなかった。

ここで重要なのは、**メール送信の失敗**と**メール内リンクのアクセス失敗**を同一視しないことだった。今回の原稿に記録されている障害は、メールに記載されたURLと、WebサーバーのHTTPS設定に関するものである。この段階でIAMの`AccessDenied`が発生したという記録はない。

### 最初のエラー：`DNS_PROBE_FINISHED_NXDOMAIN`

Elastic Beanstalkの環境変数`APP_BASE_URL`には、当初次の値が設定されていた。

```text
http://chinese-output-forge.ap-northeast-1.elasticbeanstalk.com
```

しかし、このホスト名はDNSで名前解決できなかったため、ブラウザに`DNS_PROBE_FINISHED_NXDOMAIN`が表示された。

このエラーから分かるのは、まず**ホスト名から接続先IPアドレスを取得できていない**ということである。SESがメールを配送できたか、Spring BootのControllerが正しいか、Nginxが起動しているかを論じる前に、メール本文に埋め込んだURLそのものを確認する必要があった。

そこで`APP_BASE_URL`を公開済みの独自ドメインへ変更した。

```text
https://chinese-output-forge.com
```

### 次のエラー：`ERR_CONNECTION_REFUSED`

URLを修正すると、今度は`ERR_CONNECTION_REFUSED`が発生した。先ほどの名前解決エラーとは異なり、**接続先で要求されたポートへの接続が受け付けられない**状態を示していた。

EC2のSecurity Groupでは443番ポートが許可されていた。しかし、Nginxの待ち受けを調べると80番ポートしか使用していなかった。さらに、以前EC2上で作成した`/etc/nginx/conf.d/https.conf`が存在しないことが分かった。

ここで理解したのは、**Security Groupで443番ポートを許可すること**と、**Nginxが443番ポートでHTTPS接続を待ち受けること**は別の設定だという点である。

```text
ブラウザ → DNSで名前解決
         ↓
       EC2へ接続
         ↓
       Security Groupによる通信許可
         ↓
       Nginxが443番で待ち受け
         ↓
       Spring Boot（5000番）へ転送
```

いずれかの段階が欠ければ、メール本文のURLが正しくてもページを表示できない。

## 5. Nginxの設定を復旧して何を確認したか

Let's Encryptの証明書と秘密鍵が残っていることを確認し、消失していた`https.conf`をEC2上で再作成した。設定は443番ポートでHTTPS通信を受け付け、Spring Bootが動作する5000番ポートへ転送するものだった。

```bash
sudo nginx -t
sudo systemctl reload nginx
sudo ss -lntp | grep -E ':80 |:443 '
```

`nginx -t`で構文検証が成功し、再読み込み後には80番・443番の両方でNginxが`LISTEN`していることを確認した。さらにパスワード再設定メールを再送信し、新しいリンクからパスワード再設定ページが表示されることを確認できた。

一方、**会員登録完了メールの受信まで確認できたという明確な完了記録は、この節にはない**。また、`https.conf`が消失した直接の原因も確定していない。Elastic Beanstalkの再デプロイに伴って消失した可能性はあるが、可能性と確認済みの事実は区別する。

## 6. 今回の学びと次章へのつながり

今回の一連の確認では、次のように問題を切り分ける必要があった。

| 確認する層 | 今回の確認・問題 |
|---|---|
| AWSの認証・認可 | EC2のIAMロールにSES送信操作を許可 |
| SESの送信条件 | サンドボックス内の送信先Gmailを検証 |
| メール本文 | `APP_BASE_URL`が旧ホスト名を参照 |
| DNS | `DNS_PROBE_FINISHED_NXDOMAIN` |
| HTTPS接続 | `ERR_CONNECTION_REFUSED` |
| Nginx | 443番の待ち受け設定ファイルが不在 |
| 最終的な画面 | 修正後、パスワード再設定ページの表示を確認 |

**メールが送られることと、そのメールによってユーザーが目的の操作を完了できることは別々に検証すべき**だと理解した。

また、今回のHTTPS復旧はEC2上への手動設定である。再デプロイ後も設定が残る構成にするため、次の033-05ではElastic Beanstalkのソースバンドルと`.platform/nginx/conf.d/`の関係を学ぶ。

## 参考文献

参考にした資料を示す。

- [AWS公式：Amazon SESのIAMアクセス制御](https://docs.aws.amazon.com/ja_jp/ses/latest/dg/control-user-access.html#iam-and-ses-examples-email-sending-actions)
- [IAMポリシー設定の参考記事](https://www.cloudbuilders.jp/articles/3481/)
- [SESで送信先アドレスを検証する際の参考記事](https://qiita.com/hengineer/items/1f32f66a18db19b9129f)
- [Stack Overflow：Elastic BeanstalkのNginx設定に関する議論](https://stackoverflow.com/questions/61580965/beanstalk-deployment-ignores-my-nginx-configuration-files-in-ebextensions)
