# 031 Chinese Output ForgeをAWSへデプロイする（高可用性構成・学習ログ）

この章では、Chinese Output
ForgeをAWSへデプロイした際に、実装そのものの手順ではなく、設計判断、試行錯誤、失敗から得た知識を記録する。

実装手順の再掲ではなく、実際に構築したことで理解が深まった点を中心に残す。

# 高可用性構成を一度構築してから低コスト構成へ変更する判断

AWSの学習を始めた段階では、ALB、複数EC2、Auto Scaling、RDS Multi-AZ、NAT
Gatewayなどを組み合わせた高可用性構成を「本番らしい構成」として考えていた。

しかし、実際に構築を進めると、可用性を高めるほど常時稼働するリソースが増え、ランニングコストも増えることを実感した。Chinese
Output
Forgeは個人開発の学習アプリであり、短時間の停止が重大な損失につながるサービスではない。そのため、技術的に高可用性を実現できることと、その可用性が実際のサービス要件として必要であることは別問題だと考えるようになった。

一方で、すでに高可用性を前提としたVPCやSubnetを構築していたことに加え、今回の目的にはAWSそのものの学習も含まれていた。そのため途中で単純な構成へ変更するのではなく、一度高可用性構成を完成させて動作確認を行い、その後に低コスト構成へ変更する方針にした。

今回の構築を通して、AWSの設計では「できるだけ冗長化する」のではなく、**可用性要件とコストを比較して必要な構成を選択すること**が重要だと学んだ。

# AWS構成概要

最初はALB、Elastic Beanstalk、EC2、Auto Scaling、NAT
Gateway、RDSなどを個別のサービスとして理解していたため、全体としてどのように接続されるのかを整理するのが難しかった。

構成図を作成し、

``` text
Route 53
    ↓
ALB
    ↓
EC2
    ↓
RDS
```

というWebアクセスの経路と、

``` text
EC2
    ↓
NAT Gateway
    ↓
Internet Gateway
    ↓
Internet
```

というアウトバウンド通信の経路を分けて考えることで理解しやすくなった。

また、Elastic
Beanstalkは通信経路に存在するサービスではなく、EC2、ALB、Auto
Scalingなどを利用してアプリケーション環境を管理するサービスであることも、実際のリソースを確認することで理解できた。

# 没になった案 S3の実装

当初は将来的なTTS音声ファイルの保存先としてS3を導入する予定だった。

しかし、現在のChinese Output
Forgeには音声ファイルを生成・保存・取得・再生するコードが存在しない。その状態でS3
Bucketだけを作っても、アプリケーションの機能としてS3との連携を検証したことにはならない。

S3を意味のある形で検証するには、Spring
Boot側にもTTS生成、S3へのアップロード、音声取得、ブラウザでの再生などを実装する必要があり、今回のAWSデプロイ作業から大きく範囲が広がる。

ここから、**将来使うかもしれないAWSサービスを先回りして追加するより、現在のアプリケーション要件に必要なサービスだけを導入する方がよい**と判断した。現在のChinese
Output
Forgeが扱う永続データはRDSで完結しているため、S3は音声機能を実装する段階で改めて導入することにした。

# VPC / Subnet

VPCを作ること自体よりも、Public、Private App、Private
DBという役割ごとにSubnetを分け、それを2つのAvailability
Zoneへ展開する設計を考えることが勉強になった。

特に、Public /
Privateという区別はSubnetの名前だけで決まるのではなく、ルートテーブルなどを含むネットワーク構成によって決まるという点が重要だった。

また、同じVPC内でも、ALB、EC2、RDSをすべて同じSubnetへ置くのではなく、外部公開の必要性に応じて配置場所を分けることで、ネットワークレベルで役割を分離できることを理解した。

# Internet Gateway / NAT Gateway / Route Table

今回のネットワーク構築で特に理解が深まったのが、Internet GatewayとNAT
Gatewayの役割の違いだった。

Private
SubnetにEC2を置くと、外部から直接アクセスさせない構成にできる一方で、GeminiやOpenAIなどの外部APIへのアクセスや外向き通信は必要になる。そのため、「インターネットから直接到達できないこと」と「インターネットへ一切通信できないこと」は同じではない。

そこで、Private App Subnetからの`0.0.0.0/0`をNAT Gatewayへ向け、NAT
GatewayをPublic Subnetに置く構成にした。

高可用性構成では各AZにNAT
Gatewayを1つずつ配置したが、ここでも可用性とコストの関係を実感した。NAT
GatewayまでAZごとに用意すれば単一AZへの依存を減らせるが、その分だけ常時コストが発生する。この経験が、後で低コスト構成へ変更する判断にもつながった。

# Security Group

Security
Groupは単に「必要なポートを開けるもの」と考えるより、**どの層からどの層への通信を許可するか**という形で考える方が理解しやすかった。

今回、

``` text
Internet
    ↓
ALB
    ↓
EC2
    ↓
RDS
```

という経路に合わせて、ALB用、EC2用、RDS用のSecurity Groupを分離した。

特にEC2やRDSの受信元をIPアドレスではなく別のSecurity
Groupとして指定できることが重要だった。これにより、RDSをインターネットへ公開せず、「Chinese
Output
ForgeのEC2から来たPostgreSQL通信だけを許可する」という構成にできた。

# RDS / Multi-AZ

RDSでは、当初は自分でPrimaryをAZ1、StandbyをAZ2へ明示的に配置するものだと思っていた。しかし実際には、Multi-AZ
DBインスタンス配置ではPrimary /
SecondaryのAZを利用者がそのように固定するのではなく、RDS側で決定されることを知った。

また、StandbyをRead
Replicaのように通常時の読み取り処理へ使うものではなく、障害時のフェイルオーバー先として利用するという違いも、今回の構成を作る中で整理できた。

Multi-AZは単に「RDSを2台にする」という話ではなく、可用性のための冗長化であることを実際の設定と結び付けて理解できた。

# Spring BootのJAR作成とElastic Beanstalk

ローカル開発ではEclipseからSpring
Bootを起動していたため、AWSへデプロイする段階で「アプリケーションを実行可能JARとしてパッケージ化し、それをElastic
Beanstalkへ渡す」という流れを改めて意識することになった。

また、`clean package`では単にJARを作るだけでなくテストも実行されるため、テスト時にSpringのApplicationContextが起動する構成では環境変数不足によってビルドが失敗する可能性があることも経験した。

ローカル実行、Mavenビルド、AWS上の本番実行では、それぞれ環境変数や認証情報がどこから与えられるのかを分けて考える必要があると学んだ。

# Elastic Beanstalk / ALB / EC2 / Auto Scaling

Elastic Beanstalkを使う前は、Elastic
Beanstalkそのものがアプリケーションを実行するサーバーのようなイメージもあったが、実際にはEC2、ALB、Auto
Scaling Groupなどを組み合わせて環境を管理していることが分かった。

Elastic Beanstalkの画面だけを見るのではなく、EC2、Target Group、Auto
Scaling
Groupなどを個別に確認することで、「EB環境の裏側でどのAWSリソースが動いているのか」を具体的に把握できた。

また、2台構成にしたことで、単にEC2を2台起動するだけでは高可用性にならず、ALBによる振り分け、ヘルスチェック、Auto
Scalingによる台数維持まで組み合わせて初めてWeb層の冗長化になることを理解した。

# EC2のメモリ不足とインスタンスタイプ

デプロイ中にはEC2側のリソース不足も経験した。アプリケーションが正常なコードであっても、実行環境のCPUやメモリが不足すれば安定して起動できない。

この経験から、クラウド上の障害をすべてアプリケーションコードの問題として見るのではなく、ログを確認しながらアプリケーション、OS、EC2スペック、ネットワークなど複数の層を切り分ける必要があると学んだ。

# ALBとHTTPセッション / CSRF

複数EC2へした後、ログイン時に403が発生する問題が起きた。

Spring
SecurityのCSRFトークンやHTTPセッションを各EC2のローカルメモリで保持している状態では、ログイン画面を表示したEC2とPOSTを処理するEC2が異なると、セッション情報が一致しない可能性がある。

ALBのstickinessを有効にし、同じクライアントからのリクエストを同じEC2へ送るようにすると、新しいシークレットウィンドウでログインできるようになった。

この問題から、**アプリケーションを1台から複数台へ増やすと、ステートをどこに持つかが設計上の問題になる**ことを学んだ。単純にEC2を増やすだけでは、1台構成では見えなかったセッション管理上の問題が発生する。

# RDSへのデータ移行 / Session Manager

今回のRDSはPrivate Subnetにあり、さらにEC2もPrivate
Subnetにある。そのため、ローカルMacからRDSへ直接接続する一般的な方法が使えなかった。

最初は「Session
ManagerでEC2を操作できればdumpファイルをEC2へ持っていけばよい」と考えたが、EC2を操作できることと、ローカルファイルを転送できることは別問題だった。

そこで、Session ManagerのRemote Host Port Forwardingを利用し、

``` text
Mac
localhost:15432
    ↓
SSMトンネル
    ↓
EC2
    ↓
RDS:5432
```

という経路を作り、Mac上の`psql` / `pg_restore`からRDSを操作した。

ここでは、AWS CLI、Session Manager Plugin、EC2側のSSM
Agent、IAMポリシー`AmazonSSMManagedInstanceCore`がそれぞれ別の役割を持つことを実際に使いながら理解できた。

## ポートフォワーディングで詰まった点

最初の`aws ssm start-session`ではリージョンを指定しておらず、`NoRegion`エラーになった。AWS
CLIではコンソールで東京リージョンを表示していることとは別に、CLI側でも利用リージョンを指定または設定する必要があると分かった。

その後、ローカル側もPostgreSQLと同じ`5432`を指定したところセッションが期待どおり確立しなかった。参考にしたAWS公式ドキュメントや技術記事の例を確認し、ローカル側を`15432`、RDS側を`5432`として分けることで正常にポートフォワーディングできた。

この経験から、ポートフォワーディングでは「ローカル側で待ち受けるポート」と「最終接続先のポート」は同じである必要がないことを実感できた。

## 参考にした資料

-   https://dev.classmethod.jp/articles/2026-04-14-rds-ssm-jdbc-iam-authentication/?utm_source=chatgpt.com
    -   Session ManagerのRemote Host Port
        Forwardingを使って、EC2を中継してPrivate
        RDSへ接続する構成を参考にした。
-   https://github.com/aws-samples/sample-rds-aurora-postgres-dba-toolkit/blob/main/pre-upgrade-check/README.md?utm_source=chatgpt.com
    -   `AWS-StartPortForwardingSessionToRemoteHost`を利用する具体的なコマンド構成を参考にした。
-   https://zenn.dev/taroshun32/articles/session-manager
    -   Session ManagerによってSSHポートを公開せずPrivate
        EC2へ接続する考え方を参考にした。
-   https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html#sessions-start-port-forwarding
    -   AWS公式のポートフォワーディング手順を確認した。
-   https://docs.aws.amazon.com/ja_jp/systems-manager/latest/userguide/install-plugin-macos-overview.html
    -   macOSへのSession Manager Plugin導入方法を確認した。
-   https://docs.aws.amazon.com/ja_jp/cli/latest/userguide/getting-started-install.html
    -   AWS CLIの導入方法を確認した。

# PostgreSQLのdump復元

RDSにはSpring Boot /
JPAによってすでにテーブル構造が作られていたため、最初に`pg_restore --clean --if-exists`で復元しようとしたところ、FOREIGN
KEYの依存関係によって既存テーブルや制約を削除できず、`relation already exists`やduplicate
keyなどのエラーが連鎖した。

ここで、`--clean`を付ければ既存DBを自動的にきれいな状態へ戻せるわけではなく、オブジェクト同士の依存関係を考える必要があることを学んだ。

今回は既存RDSのデータを残す必要がなく、ローカルDBを丸ごと復元することが目的だったため、個々のテーブルを削除するのではなく、

``` sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

として依存オブジェクトごと一度削除し、空のスキーマへ復元する方法へ変更した。

また、ローカルDBとRDSではPostgreSQLユーザーが異なるため、`--no-owner`と`--no-privileges`を使ってローカル環境のOwnerやACLを持ち込まないようにした。

復元後に`question`が0件から686件になり、Spring
Bootからもデータを確認できたことで、単に`pg_restore`がエラーを出さなかっただけでなく、アプリケーション側まで含めて移行を確認する重要性も学んだ。

## 参考にした資料

-   https://qiita.com/chamasei/items/77822274e8d693087096
    -   `pg_dump` /
        `pg_restore`によるPostgreSQLデータ移行の流れを参考にした。
-   https://qiita.com/kenogi/items/69d0e9b78324f56e62ea
    -   PostgreSQLのバックアップ・リストア時のコマンドやオプションを参考にした。

# 機密情報をParameter Storeへ移行

当初はDBパスワードやAPIキーをElastic
Beanstalkの環境プロパティに置けば、Gitへ書かずに済むため十分だと考えていた。

しかし、「Gitに秘密情報を残さないこと」と「AWS上で秘密情報を安全に保管すること」は別の問題である。そこで、DBパスワード、Google
APIキー、OpenAI APIキー、メールパスワードをParameter
Storeの`SecureString`へ移した。

ここでは、すべての設定値をParameter
Storeへ移すのではなく、秘密情報だけを対象にした。DB
URLやユーザー名など、秘匿する必要のない値まで暗号化管理する必要はないと判断した。

この作業を通して、設定値を「環境ごとに変わる値」と「秘密として保護すべき値」に分けて考える必要があると学んだ。

# Spring Cloud AWSとParameter Store

Parameter Storeへ値を保存するだけではSpring
Bootから自動的に使えるわけではなく、Spring Cloud AWSのParameter
Store連携を追加し、

``` yaml
spring:
  config:
    import: aws-parameterstore:/config/chinese-output-forge/
```

としてSpringのProperty Sourceへ取り込む必要があった。

ここで特に重要だったのは、**Parameter Storeから読み込んだ値はSpring
Bootのプロパティであって、OSの環境変数が新しく作られるわけではない**という点だった。

この違いは、後のGemini / OpenAI
Clientの起動失敗で初めて具体的に理解できた。

# Parameter Store移行後の502エラー

Parameter
Storeへ移行して新しいJARをデプロイすると、ALBから502が返るようになった。

最初はネットワークやRDSなど複数の原因が考えられたが、Elastic
Beanstalkのログを確認すると、Spring
Boot自体が起動途中で停止しており、Gemini
Client生成時にAPIキーを取得できていないことが分かった。

ログには、Google Gen AI
SDKが`GOOGLE_API_KEY`または`GEMINI_API_KEY`を環境変数から取得できないという内容が出ていた。

この経験から、502は必ずしもALBそのものの障害を意味せず、**ALBの転送先アプリケーションが正常に起動していない場合にも発生する**こと、そしてAWSのエラー調査ではブラウザに表示されたステータスコードだけで判断せず、アプリケーションログまで追う必要があると学んだ。

# GeminiConfig / OpenAiConfigの修正

Parameter Store移行前のGemini Clientは`new Client()`、OpenAI
Clientは`fromEnv()`を使っていたため、SDK自身がOS環境変数からAPIキーを取得していた。

Parameter
Store移行後はAPIキーがSpringのプロパティとして読み込まれるため、同じコードではSDKから見えない。

そこで、

``` java
@Value("${GOOGLE_API_KEY}") String apiKey
```

のようにSpringから値を注入し、SDKのBuilderへ明示的に渡す方式へ変更した。

この修正を通して、

``` text
OS環境変数
```

と、

``` text
Spring Environment / Property Source
```

は同じものではないことを理解した。Springが`${...}`で環境変数も解決できるため混同しやすかったが、「Springから参照できる」ことと「外部SDKがOS環境変数として直接参照できる」ことは別である。

OpenAI側では実際の起動エラーが出る前に、Geminiと同じ構造の問題を持っていると判断して`fromEnv()`から明示的なAPIキー指定へ変更した。1つの障害原因を直すだけでなく、同じ前提に依存している別のコードも確認する必要があると学んだ。

## 参考にした資料

-   https://github.com/awspring/spring-cloud-aws/discussions/12?utm_source=chatgpt.com
    -   Parameter
        Storeから取得した値をSpringのプロパティとして扱い、`@Value`で参照する考え方を参考にした。古いSpring
        Cloud
        AWSのDiscussionなので、現在の依存関係や設定方法そのものの根拠にはしていない。
-   https://ai.google.dev/gemini-api/docs/api-key?hl=ja&utm_source=chatgpt.com#java
    -   Gemini Java ClientへAPIキーを明示的に渡せることを確認した。
-   https://github.com/googleapis/java-genai/blob/main/README.md?utm_source=chatgpt.com
    -   `new Client()`による環境変数からの自動取得と、`Client.builder().apiKey(...).build()`による明示指定の違いを確認した。

# Route 53 / 独自ドメイン

当初はRoute
53でドメイン自体も取得する予定だったが、AWS側で登録できず、問い合わせても作業を進められなかったため、お名前.comで`chinese-output-forge.com`を取得する方法へ変更した。

ここで学んだのは、**ドメインのRegistrarとDNSの管理先は同じである必要がない**ということだった。

ドメインの契約・更新はお名前.comのままでも、NSレコードをRoute
53のネームサーバーへ変更すれば、DNSレコードはRoute 53で管理できる。

Route 53でHosted
Zoneを作成しただけではDNS管理先は切り替わらず、実際のドメイン側でネームサーバーを変更する必要があることも、`dig`でGMOのNSからAWSのNSへ切り替わる過程を確認して理解できた。

# Route 53 AliasとALB

AWS学習教材ではAレコードをEC2のIPアドレスへ向ける例もあったが、今回の構成ではEC2をPrivate
Subnetへ配置し、外部アクセスはALBが受ける設計にしている。

そのため、教材の手順をそのまま使わず、Route
53のAレコードをAliasとしてALBへ向けた。

ここから、教材と自分の構成が違う場合は操作手順をコピーするのではなく、**「ユーザーからの通信を最初に受けるリソースは何か」からDNSの接続先を考える必要がある**と学んだ。

また、ALBには自分で固定IPを設定して通常のAレコードへ書くのではなく、Route
53 AliasでAWSリソースとして指定できることも実装を通して理解した。

# ACM / HTTPS

Route
53からALBへのルーティングを設定した直後、ドメインだけでアクセスすると接続できなかったが、`http://`を明示すると正常に表示された。

この時点で、DNSやALBへのルーティング自体は成功しており、HTTPS側だけが未構築であると切り分けることができた。

ACMで証明書を発行し、DNS検証をRoute 53で行い、ALBへHTTPS
443リスナーを追加した。しかし、それだけではHTTPSアクセスはタイムアウトした。

原因はALBのSecurity
Groupに443番ポートのインバウンドルールがなかったことだった。

この経験からHTTPSを成立させるには、

``` text
DNS
↓
ALB
↓
HTTPSリスナー :443
↓
SSL/TLS証明書
↓
Security Group :443
```

のように複数の設定が揃う必要があり、証明書を発行しただけではHTTPS通信は成立しないことを学んだ。

# CloudWatch / Amazon SNS

CloudWatch
AlarmとSNSを設定しただけでは、本当に通知まで届くかは分からないため、EC2へ意図的にCPU負荷を発生させてEnd-to-Endで確認した。

2
vCPUであることを`nproc`で確認し、`yes > /dev/null &`を2つ実行してCPU使用率を上げ、CloudWatch
Alarmが`ALARM`へ遷移してSNS経由でメールが届くことを確認した。

ここでは、監視は「設定画面にAlarmが存在すること」を確認するだけでは不十分で、**実際に障害条件を発生させて通知経路までテストすること**が重要だと学んだ。

また、負荷テスト後には`ps aux | grep yes`でPIDを確認し、プロセスを停止した。監視テストでは異常状態を作るだけでなく、テスト終了後にリソースを正常状態へ戻すところまで含めて考える必要がある。

# 高可用性構成の動作確認

最後に各サービスを個別に見るだけでなく、Route 53 → ALB → EC2 →
RDS、Private EC2 → NAT Gateway → Internet、CloudWatch → SNS →
Emailという複数の経路を実際に確認した。

特に、EC2が「実行中」であることとALBから利用可能であることは同じではないため、Target
Groupで`Healthy`になっていることを別途確認した。また、Auto Scaling
Groupについても設定値だけでなく、Desired / Min /
Maxが2で実際に2台が正常に所属していることを確認した。

RDSについてはMulti-AZ設定、EC2からRDSの5432番ポートへの到達、アプリケーションからのDB利用をそれぞれ確認した。NAT
Gatewayについてもルートテーブルを見るだけでなく、Private
EC2から`curl`を実行して外部HTTPS通信まで確認した。

この一連の確認から、クラウド構成の動作確認では、**リソースが存在すること、設定が正しいこと、実際の通信が成功することを分けて確認する**必要があると学んだ。

# 今回のAWSデプロイを通して得たこと

今回最も大きかったのは、AWSサービスを単独の用語として覚えるのではなく、実際のSpring
Bootアプリケーションを動かすための一つのシステムとして結び付けて理解できたことだった。

特に、

-   高可用性とコストはトレードオフである
-   Public / Privateは単なるSubnet名ではなくルーティングによって成立する
-   Private EC2でもNAT Gatewayを経由して外向き通信できる
-   Security Groupは通信経路に沿って層ごとに制御できる
-   Elastic Beanstalkの背後ではEC2、ALB、Auto Scalingなどが動いている
-   複数EC2ではHTTPセッションの持ち方も考慮する必要がある
-   Private RDSへはSSMポートフォワーディングを利用できる
-   DB復元ではテーブル間の依存関係やOwnerの違いを考慮する必要がある
-   SpringのプロパティとOS環境変数は同じものではない
-   502などの表面的なエラーからログを追って原因を切り分ける必要がある
-   DNS、証明書、ALBリスナー、Security
    Groupはそれぞれ別の層として確認する必要がある
-   監視は設定するだけでなく実際に異常を発生させて通知まで確認する

といった点は、教科書だけで学習していた段階よりも実装後の方が明確に理解できた。

次の低コスト構成への変更では、今回構築した各リソースの役割を理解した上で、Chinese
Output
Forgeに必要な可用性とコストを比較し、どのリソースを残し、どのリソースを削減するかを判断する。
