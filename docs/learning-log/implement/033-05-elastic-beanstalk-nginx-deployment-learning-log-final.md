# 033-05 Elastic Beanstalkのデプロイ構造とNginx設定の永続化（学習ログ）

## はじめに

033-04では、Amazon SESから送信されたパスワード再設定メールのリンクを開いた際に、URLの名前解決エラーとHTTPS接続エラーが発生したことを振り返った。`APP_BASE_URL`を修正した後、EC2上で以前手動作成したNginxの`https.conf`が存在しないことが分かり、設定を再作成して復旧した。

しかし、**EC2上で設定を再作成して正常に動いたことと、次回のデプロイでも同じ設定が維持されることは別問題**である。そこで、Elastic Beanstalkがアプリケーションをデプロイする際、どのファイルをどのように扱うのかを調べた。

ここでは、Elastic Beanstalkのデプロイ構造と、Nginx設定をデプロイ成果物に含める理由を整理する。操作、コマンドの実行結果、画面確認は[033-05実装ログ](./033-05-nginx-https-config-persistence-development-log.md)に記録する。

## 1. EC2上の手動設定とデプロイ成果物の違い

今回のHTTPS接続に必要だった設定ファイルは、EC2上の次のパスに置かれていた。

```text
/etc/nginx/conf.d/https.conf
```

このファイルには、Nginxで443番ポートを待ち受け、Let's Encryptの証明書を使用し、受け付けたHTTPSリクエストをSpring Bootアプリケーションへ転送する設定が記載されていた。

EC2に直接ログインして設定を作成すると、その時点ではHTTPS通信を有効にできる。しかし、Elastic Beanstalkはアプリケーションのデプロイを管理する仕組みであり、EC2上の手動変更がアプリケーションのソースやデプロイ成果物に自動的に反映されるわけではない。

今回、以前作成した`https.conf`が見つからなくなった。**再デプロイで消失した可能性はあるものの、原稿では消失した直接のタイミングや原因までは特定していない。** したがって、原因を断定するのではなく、手動変更に依存しない構成へ改めることを目標とした。

学んだのは、稼働中のサーバーを手作業で修正するだけでは、次回のデプロイ時に同じ状態を再現できるとは限らないという点である。

## 2. Spring BootのJARとElastic Beanstalkのソースバンドルは別物

### 最初の疑問：`.platform`をプロジェクトに置けばJARへ入るのか

AWSの資料を参考に、Spring Bootプロジェクトのルートへ次のファイルを追加した。

```text
chinese-output-forge/
├── pom.xml
├── src/
└── .platform/
    └── nginx/
        └── conf.d/
            └── https.conf
```

最初は、Mavenで作成したJARに`https.conf`が含まれるかを確認した。しかし、`jar -tvf`でJARの内容を調べても、対象ファイルは検出されなかった。

ここで疑問になったのは、**プロジェクトに存在するファイルが、なぜデプロイ用のJARに含まれないのか**という点だった。

### JARが扱うものとElastic Beanstalkが扱うもの

Spring Bootの実行可能JARは、Javaアプリケーションを動かすための成果物である。一方、`https.conf`はSpring Bootが読み込む設定ではなく、**NginxがHTTPS通信を処理するための設定ファイル**である。

参照したAWS公式ドキュメントでは、Elastic BeanstalkはアプリケーションのJAR内部を解析しないため、Elastic Beanstalkが必要とするファイルをJARの外側に置くよう説明されていた。

この説明によって、次の二つを区別できた。

| 対象 | 役割 | 今回のファイル |
|---|---|---|
| Spring Bootの実行可能JAR | Javaアプリケーションを実行する | `application.jar` |
| Elastic Beanstalkのソースバンドル | アプリケーションとプラットフォーム設定をまとめて渡す | `application.jar`と`.platform/` |

つまり、**JARに設定ファイルが含まれないこと自体が、Elastic Beanstalkでの設定方法の失敗を意味するわけではない**。そもそも今回のNginx設定は、JARの外部に置くべきファイルだった。

参考：[AWS公式：Java SEプラットフォーム](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-platform.html)

## 3. `.platform/nginx/conf.d/`が持つ意味

次の疑問は、**JARの外に置くとして、Elastic Beanstalkはどの場所にあるNginx設定を認識するのか**という点だった。

AWS公式ドキュメントでは、Elastic Beanstalkの標準Nginx設定を拡張する場合、ソースバンドル内の`.platform/nginx/conf.d/`へ`.conf`ファイルを配置する方法が説明されていた。

```text
.platform/
└── nginx/
    └── conf.d/
        └── https.conf
```

ここで重要なのは、`.platform`が単なる任意のフォルダ名ではなく、**Elastic Beanstalkにプラットフォーム向けの設定を渡すための所定のディレクトリ**であることだ。

`src/main/resources`など、Spring Bootがアプリケーションのリソースとして扱う場所に置くことと、Elastic BeanstalkがNginx設定として読み込むことは同じではない。

今回の`https.conf`には、443番ポートの待ち受け、証明書・秘密鍵のパス、`localhost:5000`へのリバースプロキシ設定が記述されていた。これらはSpring BootのControllerではなく、アプリケーションの手前にあるWebサーバーの動作を決める設定である。

参考：[AWS公式：リバースプロキシの設定](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/platforms-linux-extend.proxy.html)

## 4. なぜJAR単体アップロードからZIP形式へ変更したのか

### JARと設定ファイルを一緒に渡す必要がある

これまではMavenで生成したJARをElastic Beanstalkへ直接アップロードしていた。しかし今回は、アプリケーション本体だけでなく、Nginxの追加設定もデプロイ時に渡さなければならない。

AWSのソースバンドルに関するドキュメントを調べ、**JARと`.platform`ディレクトリをZIPにまとめてアップロードする**方法へ変更した。

```text
chinese-output-forge-deploy.zip
├── application.jar
└── .platform/
    └── nginx/
        └── conf.d/
            └── https.conf
```

この構成では、`application.jar`がJavaアプリケーション本体、`.platform/nginx/conf.d/https.conf`がElastic Beanstalkへ渡すNginx設定に対応する。

### ZIP内部に余分な親フォルダを入れない理由

調べたAWSのソースバンドルの要件には、ZIP内に余分な親フォルダや最上位ディレクトリを含めないことがあった。したがって、次のように一段深く格納する構成は避ける必要がある。

```text
# 今回採用しない構成
chinese-output-forge-deploy.zip
└── chinese-output-forge-deploy/
    ├── application.jar
    └── .platform/
        └── nginx/
            └── conf.d/
                └── https.conf
```

**ZIPを展開した直下に、アプリケーションのJARと`.platform`が存在する**ことが重要だった。

また、今回の検討ではソースバンドルのサイズ制限も調べており、単一のZIPまたはWAR、500MB以下という要件を確認している。今回作成したZIPは約151.5MBだった。

参考：[AWS公式：アプリケーションのソースバンドル](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/applications-sourcebundle.html)

## 5. ZIPの内容を確認する意味

ZIPを作成しただけでは、必要なファイルが正しい階層に入っているとは限らない。

`unzip -Z`でZIPの内部を確認し、次の三点をチェックした。

1. ZIP直下に`application.jar`がある。
2. `.platform/nginx/conf.d/https.conf`がある。
3. 余分な親ディレクトリがない。

この確認は、**ファイルが手元のプロジェクトに存在すること**と、**Elastic Beanstalkに渡すZIPに正しい形で含まれていること**を区別するためのものだった。

先にJARの中身を調べたときは`https.conf`が見つからなかったが、今回はJARではなく、ソースバンドルであるZIPの中身を確認することが目的になる。確認対象を取り違えないことも学習上のポイントだった。

## 6. デプロイ後に何を確認できたか

作成したZIPをElastic Beanstalkへアップロードしてデプロイした後、今回の検討では以下の動作を確認した。

- `https://chinese-output-forge.com`へのHTTPSアクセス
- パスワード再設定メールの送信操作後、送信完了画面への遷移
- 受信メール内のリンクからパスワード再設定ページへの遷移

この確認によって、少なくとも**今回のZIPを使用したデプロイ後には、WebサイトへのHTTPS接続とメール内リンクからのアクセスが正常に動作した**ことが分かった。

ただし、ここから「将来のすべてのデプロイで必ず設定が維持される」「Let's Encryptの証明書更新まで自動化できた」と結論付けることはできない。原稿にはそこまでの検証記録はないため、今回確認できた範囲と今後の運用課題は分けて考える。

## 7. 今回の学習で整理できたこと

今回の問題を通じて、Spring Bootアプリケーションのデプロイは、**JavaコードをJARにまとめてアップロードするだけで完結するとは限らない**と理解した。

- **EC2上の手動変更**は、その時点で動作しても、デプロイ成果物として再現できるとは限らない。
- **Spring BootのJAR**と**Elastic Beanstalkのソースバンドル**は役割が異なる。
- **Nginxの設定**はSpring Bootの内部ではなく、Elastic Beanstalkが認識する`.platform/nginx/conf.d/`に配置する。
- **ZIPのディレクトリ構成**はデプロイの成否に関係するため、アップロード前に確認する。
- **動作確認の成功範囲**と、まだ確認していない将来の運用上の保証は区別する。

「アプリケーション本体とWebサーバー設定を同じデプロイ成果物として管理する重要性」に加えて、AWS公式資料の説明を確認することで、**なぜJAR単体ではなくZIP形式にする必要があったのか**まで理解を整理できた。

## 参考文献

以下は今回の学習で参照した資料である。

- [AWS公式：Elastic Beanstalkのリバースプロキシ設定](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/platforms-linux-extend.proxy.html)
- [Stack Overflow：Beanstalk deployment ignores my nginx configuration files in .ebextensions](https://stackoverflow.com/questions/61580965/beanstalk-deployment-ignores-my-nginx-configuration-files-in-ebextensions)
- [JARの内容確認コマンドに関する参考記事](https://qiita.com/cotaro/items/329089e8da6d2fce030e)
- [AWS公式：Elastic Beanstalk Java SEプラットフォーム](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-platform.html)
- [AWS公式：Reverse proxy configuration（英語版）](https://docs.aws.amazon.com/us_en/elasticbeanstalk/latest/dg/platforms-linux-extend.proxy.html)
- [AWS公式：ソースバンドルの作成](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/applications-sourcebundle.html)
- [ZIP作成コマンドに関する参考記事](https://qiita.com/griffin3104/items/948e38aab62bbb0d0610)
- [ZIPの内容確認コマンドに関する参考記事](https://skume.net/entry/2023/01/20/050000#google_vignette)
