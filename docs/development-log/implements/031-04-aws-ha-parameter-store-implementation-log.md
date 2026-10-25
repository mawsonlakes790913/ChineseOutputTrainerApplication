# 031-04 AWS高可用性構成④｜Parameter Storeによる機密情報の管理

## 作業概要

031-03でRDSへのデータ移行を完了した後、Elastic Beanstalkの環境プロパティに設定していた機密情報をParameter Storeへ移した。移行後の初回デプロイでは`502 Bad Gateway`が発生したため、ログを調査し、Gemini / OpenAIクライアントの初期化方法を変更して再デプロイした。

> 元ログの「10. 環境プロパティの暗号化」を実装記録中心に整理。パラメータの実値・秘密情報は掲載しない。

## 1. Parameter Storeへの移行

Elastic Beanstalkの環境プロパティで管理していたDBパスワードやAPIキーを、Systems Manager Parameter Storeの`SecureString`へ移す。ソースコードへの記載を避けるとともに、機密値の保存方法を変更するための作業。

# やり方

Spring BootからParameter Storeを簡単に利用するために「Spring Cloud
AWS」を使う

Spring Cloud AWS - AWS CloudとSpring
Bootを連携するためのオープンソースプロジェクト - Spring
BootからAWSの各種サービスを利用しやすくする - 今回は AWS Systems Manager
Parameter Storeとの連携に使用する

今回の役割 Spring Bootアプリ ↓ Spring Cloud AWS ↓ AWS Systems Manager
Parameter Store ↓ DB接続情報などを取得

Spring Cloud AWSを使うことで、Spring BootからParameter
Storeに保存した設定値を読み込める。

# 開発手順

1.  AWS Parameter Storeにパラメータを作成する

2.  AWSロールにポリシーをアタッチする：`AmazonSSMReadOnlyAccess`

3.  Spring Bootアプリの`application-prod.properties`ファイルを編集する

4.  Parameter StoreをサポートするようにMavenの`pom.xml`を更新する

5.  Mavenを使用してSpring Bootアプリをパッケージ化する

6.  AWSの環境プロパティを更新する

7.  Spring BootのJARファイルをElastic Beanstalkにアップロードする

## ステップ1. AWS Parameter Storeにパラメータを作成する

パラメータ名はそれぞれ

/config/chinese-output-forge/DB_USERNAME　cof_admin
/config/chinese-output-forge/DB_PASSWORD　[REDACTED]
/config/chinese-output-forge/GOOGLE_API_KEY　[REDACTED]
/config/chinese-output-forge/MAIL_USERNAME　[REDACTED]
/config/chinese-output-forge/MAIL_PASSWORD　[REDACTED]
/config/chinese-output-forge/OPENAI_API_KEY　[REDACTED]

とする。

## ステップ2：AWSロールにポリシーをアタッチする

-   AWSロールに`AmazonSSMReadOnlyAccess`ポリシーをアタッチする
-   アプリケーションがAWS Parameter
    Storeから設定データを読み取れるようにする

IAMからchinese-output-forge-ec2-roleにアクセス

AWSElasticBeanstalkWebTierを選択した状態で許可を追加

AmazonSSMReadOnlyAccessを検索
AmazonSSMReadOnlyAccessをチェックし、許可を追加

## ステップ3：Spring Bootの`application-prod.yml`を編集する


application-prod.ymlは既存のapplication.ymlをそのままコピーして作ればいい

**ファイル：`application-prod.yml`**

``` yaml
spring:
  config:
    # 機密情報をAWS Parameter Storeから読み込む
    import: aws-parameterstore:/config/chinese-output-forge/
```

-   `spring.config.import`
    -   追加の設定情報を読み込む
-   `aws-parameterstore:`
    -   Spring Cloud AWSを使ってAWS Parameter
        Storeから追加の設定情報を取得する
-   `/config/chinese-output-forge/`
    -   Parameter Storeに作成したパラメータへのパス

## 実装

既存の`application-prod.yml`の`spring:`直下に以下を追加する。

``` yaml
spring:

  config:
    # Load properties from AWS Parameter Store
    import: aws-parameterstore:/config/chinese-output-forge/

  messages:
    fallback-to-system-locale: false
```

意味としては、

**AWS Parameter Storeの `/config/chinese-output-forge/`
配下にある設定を、Spring Bootの設定として読み込む**

ということ。

先ほど作成した、

``` text
/config/chinese-output-forge/DB_PASSWORD
/config/chinese-output-forge/GOOGLE_API_KEY
/config/chinese-output-forge/MAIL_PASSWORD
/config/chinese-output-forge/OPENAI_API_KEY
```

を読み込むための設定。

    spring:

      config:
        import: aws-parameterstore:/config/chinese-output-forge/

      messages:
        fallback-to-system-locale: false

      datasource:
        url: ${DB_URL:jdbc:postgresql://localhost:5432/chinese_output_forge}
        username: ${DB_USERNAME:chinese_output_forge_app}
        password: ${DB_PASSWORD}

      jpa:
        hibernate:
          ddl-auto: update

      mail:
        host: smtp.gmail.com
        port: 587
        username: ${MAIL_USERNAME}
        password: ${MAIL_PASSWORD}
        properties:
          mail:
            smtp:
              auth: true
              starttls:
                enable: true

    logging:
      level:
        org.springframework.security: DEBUG

    app:
      base-url: http://localhost:8080

## ステップ4：Parameter StoreをサポートするようにMavenの`pom.xml`を更新する


-   Spring Cloud AWSのBOM（Bill of Materials）を使用する
-   Spring Cloud AWSの各依存関係で推奨されるバージョンを定義する
-   Spring Boot Starter Parentと似た考え方

**ファイル：`pom.xml`**

``` xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.awspring.cloud</groupId>
            <artifactId>spring-cloud-aws-dependencies</artifactId>
            <version>x.y.z</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

> この設定は通常の`<dependencies>`セクションではなく、`<dependencyManagement>`セクションに記述する。

-   Spring Cloud AWS Starter Parameter Storeの依存関係を追加する
-   バージョン番号を記述する必要はない
    -   前のスライドで設定したSpring Cloud AWSのBOMで定義されているため

**ファイル：`pom.xml`**

``` xml
<dependencies>

    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-starter-parameter-store</artifactId>
    </dependency>

    ...

</dependencies>
```

> こちらは通常の`<dependencies>`セクションに追加する。

## 5. Mavenを使ってSpring Bootアプリをパッケージ化する

Parameter
Storeを使用する場合、ローカル環境ではAWSのリージョンを特定できずエラーになることがある。

その場合は、Eclipseの環境変数に以下を追加する。

``` text
AWS_REGION=ap-northeast-1
```

これにより、Spring Bootが

``` text
ap-northeast-1
```

にあるParameter Storeへアクセスできるようになる。

その後、Mavenを使ってアプリケーションをJarファイルにパッケージ化する。

## Step 6：AWSの環境プロパティを更新する

Elastic Beanstalkで環境プロパティを設定する。

``` text
SPRING_PROFILES_ACTIVE=prod
```

これにより、`prod`プロファイルを使用して`application-prod.yml`を読み込む。

`application-prod.yml`には以下を設定している。

``` yaml
spring:
  config:
    import: aws-parameterstore:/config/chinese-output-forge/
```

つまり、

``` text
SPRING_PROFILES_ACTIVE=prod
        ↓
application-prod.yml が有効になる
        ↓
spring.config.import が読み込まれる
        ↓
AWS Systems Manager Parameter Store の
/config/chinese-output-forge/
配下から設定を読み込む
```

Parameter Storeから読み込む機密情報は以下の4つ。

``` text
/config/chinese-output-forge/DB_PASSWORD
/config/chinese-output-forge/GOOGLE_API_KEY
/config/chinese-output-forge/MAIL_PASSWORD
/config/chinese-output-forge/OPENAI_API_KEY
```

# 実装

``` text
Elastic Beanstalk
↓
Chinese-output-forge-env
↓
設定
↓
環境プロパティ
```

以下の4つをElastic Beanstalkの環境プロパティから削除する。

``` text
DB_PASSWORD
GOOGLE_API_KEY
MAIL_PASSWORD
OPENAI_API_KEY
```

これらはParameter Storeの`SecureString`から取得するため、Elastic
Beanstalk側には保存しない。

一方、以下は設定しておく。

``` text
SPRING_PROFILES_ACTIVE=prod
```

これにより、本番環境では`application-prod.yml`が有効になり、Parameter
Storeから機密情報を読み込む。

## Step 7：Spring BootのJARファイルをElastic Beanstalkにアップロードする

Spring BootのJARファイルを **Elastic Beanstalk** にアップロードする。

## 実行（失敗）

新しいJARファイルをElastic
Beanstalkにアップロードし、デプロイを実行した。

デプロイ完了後にドメインへアクセスしたところ、以下のエラーが発生した。

``` text
502 Bad Gateway
```

Elastic Beanstalkのログを確認すると、Spring
Bootの起動時に以下のエラーが発生していた。

``` text
Failed to instantiate [com.google.genai.Client]:
Factory method 'geminiClient' threw exception with message:
API key must either be provided or set in the environment variable
GOOGLE_API_KEY or GEMINI_API_KEY.
```

一方で、Parameter Store自体の読み込みは行われていた。

``` text
The following 1 profile is active: "prod"

Loading property from AWS Parameter Store with name:
/config/chinese-output-forge/
```

## 原因

ログの詳細は、

Error creating bean with name 'geminiClient' ... Failed to instantiate
\[com.google.genai.Client\]: Factory method 'geminiClient' threw
exception with message: API key must either be provided or set in the
environment variable GOOGLE_API_KEY or GEMINI_API_KEY. If both are set,
GOOGLE_API_KEY will be used.

で、

「geminiClient の生成に失敗した。 APIキーを明示的に渡すか、環境変数
GOOGLE_API_KEY または GEMINI_API_KEY に設定する必要がある。」
というエラー。

### 今回のケース

しかし、今回 `GOOGLE_API_KEY` と `OPENAI_API_KEY` をElastic
Beanstalkの環境プロパティから削除し、Parameter Storeへ移動した。

Parameter Storeに保存した値は、Spring Cloud
AWSによって読み込まれ、**Spring Bootのプロパティとして利用できる**。

例えば、Parameter Storeに以下のパラメータを保存している。

``` text
/config/chinese-output-forge/GOOGLE_API_KEY
```

`application-prod.yml` では以下の設定によって、Parameter Storeの
`/config/chinese-output-forge/` 配下を読み込んでいる。

``` yaml
spring:
  config:
    import: aws-parameterstore:/config/chinese-output-forge/
```

そのため、Spring Bootからは `GOOGLE_API_KEY`
をプロパティとして利用できる。

しかし、**Parameter StoreからSpring
Bootのプロパティとして読み込んでも、EC2上にOSの環境変数 `GOOGLE_API_KEY`
が作成されるわけではない。**

そのため、これまで使用していた以下のコードではAPIキーを取得できなくなった。

``` java
return new Client();
```

`new Client()` はSpring Bootのプロパティを参照する処理ではなく、Google
Gen AI SDKが環境変数 `GOOGLE_API_KEY` または `GEMINI_API_KEY`
を直接探す。

今回はElastic Beanstalkの環境プロパティから `GOOGLE_API_KEY`
を削除しているため、SDKが参照できる環境変数が存在せず、以下のエラーが発生した。

``` text
API key must either be provided or set in the environment variable
GOOGLE_API_KEY or GEMINI_API_KEY.
```

つまり、今回の状態は以下のようになっていた。

``` text
Parameter Store
/config/chinese-output-forge/GOOGLE_API_KEY
        ↓
Spring Cloud AWS
        ↓
Spring Bootのプロパティ
GOOGLE_API_KEY
        ↓
        ×
new Client()
        ↓
Google Gen AI SDKはOSの環境変数
GOOGLE_API_KEYを探す
        ↓
Elastic Beanstalkの環境プロパティからは削除済み
        ↓
見つからない
        ↓
Gemini Clientの生成に失敗
        ↓
Spring Bootの起動に失敗
        ↓
502 Bad Gateway
```

そのため、SDKから環境変数を直接取得するのではなく、Spring
BootがParameter Storeから取得した値を `@Value`
で受け取り、Client生成時に明示的に渡すように修正する。

## 修正

``` text
git commit -m "Update AI clients to use injected API keys"
```

## GeminiConfig.java

``` java
@Configuration
public class GeminiConfig {

    /**
     * Gemini APIとの通信に使用するClientを生成する。
     *
     * @param apiKey Gemini APIキー
     * @return Gemini API用のClient
     */
    @Bean
    Client geminiClient(
            @Value("${GOOGLE_API_KEY}") String apiKey) {

        return Client.builder()
                .apiKey(apiKey)
                .build();
    }
}
```

## OpenAiConfig.java

``` java
@Configuration
public class OpenAiConfig {

    /**
     * OpenAI APIとの通信に使用するClientを生成する。
     *
     * @param apiKey OpenAI APIキー
     * @return OpenAI API用のClient
     */
    @Bean
    OpenAIClient openAIClient(
            @Value("${OPENAI_API_KEY}") String apiKey) {

        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}
```

Parameter Storeの

``` text
/config/chinese-output-forge/OPENAI_API_KEY
```

から読み込んだ値を `@Value("${OPENAI_API_KEY}")`
で取得し、OpenAIのClientへ渡す。

# 一言でまとめると

APIキーの保管場所を「EBの環境プロパティ」から「Parameter
Store」に変えたことで、APIキーはOS環境変数ではなくSpring
Bootのプロパティとして読み込まれるようになった。そのため、SDKによる環境変数からの自動取得から、@Value
を使ってSpring Bootのプロパティを取得しSDKへ明示的に渡す方式へ変更した。

## 修正後の流れ

``` text
Parameter Store
/config/chinese-output-forge/GOOGLE_API_KEY
/config/chinese-output-forge/OPENAI_API_KEY
        ↓
Spring Cloud AWS
        ↓
Spring Bootのプロパティとして読み込み
        ↓
@Valueで取得
        ↓
Gemini / OpenAIのClient生成時にAPIキーを渡す
```

修正後、再度JARファイルを作成する。

``` bash
./mvnw clean package
```

作成された新しいJARファイルをElastic
Beanstalkへアップロードし、再デプロイする。

------------------------------------------------------------------------

## 実行（成功）

ドメイン chinese-output-forge.ap-northeast-1.elasticbeanstalk.com
にアクセスすると見事にエラーなくページが表示された。


## 公開前の確認事項

- `pom.xml`のBOMバージョンが元ログでは`x.y.z`となっている。実際に使用したバージョンは未記録のため、公開前に確認する。
- `AmazonSSMReadOnlyAccess`は元ログに記載された実際の設定。権限を対象パラメータへ限定するかは別途検討事項とし、実施済みとは記載しない。
- 元ログには`application-prod.yml`に`app.base-url: http://localhost:8080`がある。実際の本番環境での使用状況は未確認。
- パラメータの実値、APIキー、パスワード、メールアカウント情報は公開前に再確認する。
