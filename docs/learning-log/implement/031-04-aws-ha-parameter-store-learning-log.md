# 031-04 AWS高可用性構成④｜Parameter Storeによる機密情報の管理（学習ログ）

この章では、機密情報をElastic Beanstalkの環境プロパティからAWS Systems Manager Parameter Storeへ移行した際に、設定値の扱い方やSpring Bootと外部SDKの違いについて学んだことを記録する。具体的な設定手順、修正コード、デプロイの経過は対応する実装ログ031-04を参照する。

## 機密情報をParameter Storeへ移行

当初はDBパスワードやAPIキーをElastic Beanstalkの環境プロパティに置けば、Gitへ書かずに済むため十分だと考えていた。

しかし、**「Gitに秘密情報を残さないこと」と「AWS上で秘密情報を安全に保管すること」は別の問題**である。そこで、DBパスワード、Google APIキー、OpenAI APIキー、メールパスワードをParameter Storeの`SecureString`へ移した。

一方、すべての設定値をParameter Storeへ移すことはしなかった。DB URLやユーザー名など、秘匿する必要のない値まで暗号化管理する必要はないと判断したためである。

この作業を通して、設定値は単に「環境ごとに変わる値」として一括りにするのではなく、**「環境によって変わる値」と「秘密として保護すべき値」を区別する**必要があると学んだ。

## Spring Cloud AWSとParameter Store

Parameter Storeに値を登録するだけで、Spring Bootから自動的に参照できるわけではなかった。Spring Cloud AWSのParameter Store連携を追加し、次の設定によってSpringのProperty Sourceへ取り込んだ。

```yaml
spring:
  config:
    import: aws-parameterstore:/config/chinese-output-forge/
```

ここで重要だったのは、**Parameter Storeから読み込んだ値はSpring Bootのプロパティであり、OSの環境変数が新しく作成されるわけではない**という点だった。

当初はこの違いを十分に意識していなかったが、後にGemini Clientの起動失敗を経験して、具体的に理解することになった。

## Parameter Store移行後の502エラー

Parameter Storeへ移行した新しいJARをデプロイすると、ALBから`502 Bad Gateway`が返るようになった。

最初はネットワークやRDSなども原因として考えられた。しかし、Elastic Beanstalkのログを確認すると、Spring Bootが起動途中で停止しており、Gemini Clientの生成時にAPIキーを取得できていないことが分かった。

ログには、Google Gen AI SDKが`GOOGLE_API_KEY`または`GEMINI_API_KEY`をOS環境変数から取得できないという内容が出ていた。

この経験から、**ブラウザに表示された502エラーだけでALBやネットワークの問題と判断してはいけない**ことを学んだ。ALBの転送先となるアプリケーションが起動できていない場合にも502が発生し得るため、アプリケーションログまで追って原因を切り分ける必要がある。

## GeminiConfig / OpenAiConfigの修正

移行前のGemini Clientは`new Client()`、OpenAI Clientは`fromEnv()`を使用していた。どちらもSDK自身がOS環境変数からAPIキーを取得することを前提とした実装だった。

ところが、Parameter Storeから取り込んだAPIキーはSpringのプロパティとして参照できても、SDKが直接読むOS環境変数には存在しない。そのため、従来の初期化方法ではAPIキーを取得できなかった。

そこで、Springから値を注入し、SDKのBuilderへ明示的に渡す方式へ変更した。

```java
@Value("${GOOGLE_API_KEY}") String apiKey
```

この修正を通して、次の2つを区別できるようになった。

- **OS環境変数**：SDKが`new Client()`や`fromEnv()`などで直接参照する値
- **Spring Environment / Property Source**：Springが`${...}`や`@Value`を通して解決する値

SpringはOS環境変数もプロパティとして扱えるため、両者を混同しやすい。しかし、**「Springから参照できる」ことと「外部SDKがOS環境変数として参照できる」ことは同じではない**。

また、OpenAI側では実際に起動エラーが出る前に、Geminiと同じ前提に依存していると気づき、`fromEnv()`からAPIキーの明示指定へ変更した。1つの障害を解消するだけでなく、**同じ前提で実装された別の箇所も点検する必要がある**と学んだ。

### 参考にした資料

- https://github.com/awspring/spring-cloud-aws/discussions/12
  - Parameter Storeの値をSpringのプロパティとして扱い、`@Value`で参照する考え方を参考にした。古いDiscussionのため、現在の依存関係や設定方法そのものの根拠にはしていない。
- https://ai.google.dev/gemini-api/docs/api-key?hl=ja#java
  - Gemini Java ClientへAPIキーを明示的に渡せることを確認した。
- https://github.com/googleapis/java-genai/blob/main/README.md
  - `new Client()`による環境変数からの自動取得と、`Client.builder().apiKey(...).build()`による明示指定の違いを確認した。
