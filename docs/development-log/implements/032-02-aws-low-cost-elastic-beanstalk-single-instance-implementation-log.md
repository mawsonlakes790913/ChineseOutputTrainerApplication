# 032-02 AWS低コスト構成②｜Elastic BeanstalkのSingle Instance環境構築と動作確認（実装ログ）

## 1. 作業の目的

032-01では、RDSをMulti-AZからSingle-AZへ変更し、既存環境からデータの読み書きができることを確認した。

本章では、Elastic Beanstalkの**Load Balanced環境からSingle Instance環境への移行先を新規構築**する。既存環境は変更・削除せず、新環境でアプリケーションの起動とRDSへの読み書きを確認してから、次章でRoute 53を切り替える。

既存のPrivate App Subnet `10.1.20.0/24`、`10.1.21.0/24`にはEC2が稼働しているため、移行中にそれらのSubnetをPublicへ変更しない。代わりに未使用CIDRへ新しいPublic Subnetを作成する。

移行途中の構成図：

![](../../images/0032-16.png)

## 2. 新しいPublic Subnetを作成する

AWSコンソールの **VPC → サブネット → サブネットを作成** から、次の内容で作成した。

| 項目 | 設定値 |
|---|---|
| VPC | `chinese-output-forge-vpc` |
| サブネット名 | `chinese-output-forge-public-app-subnet-1a` |
| Availability Zone | `ap-northeast-1a` |
| IPv4 CIDR | `10.1.12.0/24` |

![](../../images/0032-06.png)

作成したSubnetには、既存のPublic Subnetで使用していたPublic用Route Tableを関連付けた。デフォルトルートは次のとおり。

```text
0.0.0.0/0 → Internet Gateway
```

![](../../images/0032-07.png)

## 3. Elastic BeanstalkのSingle Instance環境を新規作成する

**Elastic Beanstalk → 環境 → 新しい環境の作成** を開く。既存のLoad Balanced環境はそのまま稼働させ、新環境を別途作成する。

### 3.1 アプリケーションと環境の設定

| 項目 | 設定値 |
|---|---|
| アプリケーション名 | `Chinese-output-forge` |
| 環境名 | `Chinese-output-forge-single-env` |
| ドメイン名プレフィックス | `chinese-output-forge-single` |
| 環境の説明 | `Single Instance environment for low-cost architecture` |
| バージョンラベル | `1.0-8` |

元ログでは、バージョン`1.0-7`が使用できなかったため`1.0-8`を指定したと記録している。原因は特定していない。

環境プロパティには以下を設定した。

```properties
APP_BASE_URL=http://chinese-output-forge.ap-northeast-1.elasticbeanstalk.com
DB_URL=jdbc:postgresql://chinese-output-forge-db.c32cwi4eqr3d.ap-northeast-1.rds.amazonaws.com:5432/chinese_output_forge
SERVER_PORT=5000
SPRING_PROFILES_ACTIVE=prod
```

この時点では`APP_BASE_URL`に旧環境のElastic Beanstalk URLを設定している。新環境の動作確認とRoute 53の切り替えが完了した後、独自ドメイン`https://chinese-output-forge.com`へ変更する予定とした。

### 3.2 IAMロール・インスタンス設定

既存環境で使用していたIAMロールを再利用した。

| 項目 | 設定値 |
|---|---|
| サービスロール | `chinese-output-forge-elasticbeanstalk-service-role` |
| EC2インスタンスプロファイル | `chinese-output-forge-ec2-role` |
| 環境タイプ | 単一インスタンス（Single Instance） |
| EC2キーペア | `chinese-output-forge-ssh-key` |
| インスタンスタイプ | `t3.small` |
| VPC | `chinese-output-forge-vpc` |
| Subnet | `10.1.12.0/24`（`chinese-output-forge-public-app-subnet-1a`） |
| Security Group | `chinese-output-forge-public-ec2-sg`（新規作成） |

サービスロールはElastic Beanstalk自身がAWSリソースを管理するために使用し、EC2インスタンスプロファイルはBeanstalkが作成したEC2に付与され、SSMやParameter Storeへのアクセスなどに使用する。

### 3.3 新しいSecurity Groupを作成する

| 項目 | 設定値 |
|---|---|
| Security Group名 | `chinese-output-forge-public-ec2-sg` |
| 説明 | `Security group for Elastic Beanstalk Single Instance EC2` |
| VPC | `chinese-output-forge` |
| Inbound | HTTP / TCP 80 / `0.0.0.0/0` |
| Inbound | SSH / TCP 22 / マイIP（`/32`） |
| Outbound | すべてのトラフィック |

SSHの接続元は`0.0.0.0/0`ではなく、作業時のグローバルIPアドレス（マイIP）に限定した。別の回線から接続する場合は接続元IPが変わるため、必要に応じてSession Managerを使用する。

### 3.4 環境の作成結果

環境作成後、数分で正常に起動したことを確認した。

![](../../images/0032-08.png)

Elastic IPを確認すると、新しいSingle Instance環境のEC2にElastic IPが割り当てられていた。

![](../../images/0032-09.png)

```text
Internet
  ↓
Internet Gateway
  ↓
Public Subnet（10.1.12.0/24）
  ↓
EC2（Chinese-output-forge-single-env）
  └─ Elastic IP：13.193.140.206
```

この環境ではElastic Beanstalkの作成時にElastic IPが割り当てられたため、別途手動で確保・関連付けする作業は行わなかった。

## 4. 新環境の初回アクセスで502エラーが発生

次の新環境URLへアクセスした。

`http://chinese-output-forge-single.ap-northeast-1.elasticbeanstalk.com/`

結果は**`502 Bad Gateway`**だった。

![](../../images/0032-10.png)

### 4.1 Elastic Beanstalkのログを確認

**Elastic Beanstalk → `Chinese-output-forge-single-env` → ログ → ログをリクエスト** からログを取得した。

`web.stdout.log`では、Spring Boot 3.5.11がJava 21で起動し、`prod`プロファイルが有効になっていること、Tomcatが5000番ポートで初期化されていることを確認できた。

一方、その後のRDS接続処理で次のエラーが発生していた。

```text
The connection attempt failed.
Caused by: java.net.SocketTimeoutException: Connect timed out
```

Spring BootがRDSに接続できず起動に失敗した結果、nginxが502を返していた。新環境のEC2には新しいSecurity Groupを設定したが、RDS側ではまだそのSecurity Groupからの接続を許可していなかった。

### 4.2 RDS側のSecurity Groupを修正

`chinese-output-forge-rds-sg`のInbound Ruleに、次のルールを追加した。

| 項目 | 設定値 |
|---|---|
| タイプ | PostgreSQL |
| プロトコル | TCP |
| ポート | `5432` |
| ソース | 新Single Instance EC2のSecurity Group `sg-0efc96c6adfe3a676` |

既存環境からの接続を維持するため、旧EC2用Security Group `sg-015ac2597eab3b319`（`chinese-output-forge-ec2-sg`）をソースとするルールは削除しなかった。

![](../../images/0032-11.png)

## 5. 修正後の動作確認

### 5.1 アプリケーションの表示

再度、新環境URLへアクセスした。

`http://chinese-output-forge-single.ap-northeast-1.elasticbeanstalk.com/`

今度はSpring Bootアプリケーションが正常に表示された。

![](../../images/0032-12.png)

### 5.2 RDSへの書き込み・削除

新環境から問題を追加し、画面に反映されることを確認した。

![](../../images/0032-13.png)

![](../../images/0032-14.png)

さらに追加した問題を削除し、削除処理が正常に行われることも確認した。

![](../../images/0032-15.png)

これにより、新しいSingle Instance環境でSpring Bootが起動し、RDSへの接続とデータの追加・削除が正常に動作することを確認できた。

**この段階では既存のLoad Balanced環境とALBは削除していない。** 新旧環境を並行稼働させたまま、次の032-03でRoute 53の参照先を切り替える。

![](../../images/0032-16.png)
