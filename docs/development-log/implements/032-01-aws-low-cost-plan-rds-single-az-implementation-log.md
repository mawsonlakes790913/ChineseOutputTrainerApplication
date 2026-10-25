# 032-01 AWS低コスト構成①｜移行計画とRDSのSingle-AZ化（実装ログ）

## 1. 作業の目的

031では、Chinese Output ForgeをALB・複数EC2・Auto Scaling・NAT Gateway・RDS Multi-AZを使用する高可用性構成でデプロイし、動作確認を行った。

個人開発アプリとしての運用コストを抑えるため、高可用性構成を確認したうえで低コスト構成へ移行する。**本章では移行前の構成と作業順序を整理し、RDSをMulti-AZからSingle-AZへ変更してデータの読み書きを確認する。** Elastic Beanstalkの新環境作成以降は032-02以降に記録する。

## 2. 移行前後の構成を整理

### 2.1 移行前：高可用性構成

![](../../images/0031-21.png)

| 項目 | 移行前 | 移行後の方針 |
|---|---|---|
| Webへの入口 | Route 53 → ALB | Route 53 → EC2のElastic IP |
| アプリケーション | Elastic Beanstalk / EC2 2台 / Load Balanced | Elastic Beanstalk / EC2 1台 / Single Instance |
| EC2の配置 | Private App Subnet、複数AZ | 新しいPublic Subnet、単一AZ |
| インターネット向け通信 | NAT Gateway 2台 | EC2からInternet Gatewayへ直接通信 |
| データベース | RDS Multi-AZ | RDS Single-AZ |
| HTTPS終端 | ALB | EC2のnginx |
| 監視・通知 | CloudWatch / SNS | 継続利用 |

### 2.2 当初の低コスト構成案

![](../../images/0032-01.png)

当初は既存の`10.1.20.0/24`をPublic Subnetに変更し、EC2を1台に集約する案を検討した。RDSは`10.1.30.0/24`（1a）に置き、`10.1.31.0/24`（1c）はDB Subnet Group用に残す想定だった。

ただし、後続の作業では**既存EC2が稼働するSubnetを移行中に変更せず、新しいPublic Subnet `10.1.12.0/24`を作成する方針へ変更**した。また、RDSのSingle-AZ化直後の配置先は当初の想定とは異なり`ap-northeast-1c`だった。以下はこの時点の計画であり、最終構成の実績ではない。

最終構成図として元ログに掲載した画像：

![](../../images/0032-62.png)

## 3. 移行前のAWSリソースを棚卸し

### 3.1 Subnet・Route Table

元ログで作成した棚卸し表を残す。**「低コスト版」列は移行前の計画**であり、後続の実際の変更結果とは区別する。

| Subnet | AZ | 移行前の役割 | 配置リソース | Route Table | デフォルトルート | 当初の方針 |
|---|---|---|---|---|---|---|
| `10.1.10.0/24` | AZ1 | Public | ALB、NAT GW | Public RT | `0.0.0.0/0 → IGW` | 不要候補 |
| `10.1.11.0/24` | AZ2 | Public | ALB、NAT GW | Public RT | `0.0.0.0/0 → IGW` | 不要候補 |
| `10.1.20.0/24` | AZ1 | Private | EC2 | Private RT | `0.0.0.0/0 → NAT GW` | Public化を検討 |
| `10.1.21.0/24` | AZ2 | Private | EC2 | Private RT | `0.0.0.0/0 → NAT GW` | 削除候補 |
| `10.1.30.0/24` | AZ1 | Private | RDS | DB RT | local | 残す |
| `10.1.31.0/24` | AZ2 | Private | RDS Standby | DB RT | local | 残す |

### 3.2 Security Group・環境設定

- ALB用SG：外部からのHTTPS `443`を受け付け、EC2へ転送。ALB廃止後に不要となる予定。
- 既存EC2用SG：ALB用SGからのアプリケーション通信を許可。新しいPublic EC2用SGは別途作成する。
- RDS用SG：アプリケーションEC2からのPostgreSQL `5432`を許可。新環境のEC2用SGからの接続許可が必要になる。
- Spring Profile：`prod`を継続。
- DB接続先・認証情報：RDS EndpointおよびParameter Store等の設定を新環境で再確認する。
- HTTPS：ALBでのTLS終端からEC2上のnginxへ移す。
- IAM：Elastic BeanstalkとEC2の既存ロールを確認して再利用する方針。

> 元ログの棚卸し表にはRDS用SGのポートとして「3306等」という仮置き表記があったが、実際に使用しているDBはPostgreSQLであり、接続ポートは`5432`。ここでは実際のDB構成に合わせて記載した。

### 3.3 移行の順序

1. RDSをSingle-AZ化し、既存アプリケーションからのDB接続を確認する。
2. 新しいPublic SubnetにElastic BeanstalkのSingle Instance環境を構築する。
3. 新環境でSpring Bootの起動、RDSの読み書きを確認する。**旧環境はこの段階で削除しない。**
4. Route 53のAレコードを新環境のElastic IPへ切り替える。
5. EC2側でHTTPSを設定し、独自ドメインからの接続を確認する。
6. 監視・通知を調整し、旧ALB・Elastic Beanstalk環境・NAT Gatewayなどを整理する。
7. 最終構成の動作確認を行う。

## 4. RDSをMulti-AZからSingle-AZへ変更

### 4.1 設定変更

AWSコンソールで次の操作を実施した。

1. **RDS → データベース → `chinese-output-forge-db`** を開く。
2. 「可用性と耐久性」の「マルチAZ配置」で、**「スタンバイインスタンスを作成しないでください」**へ切り替える。
3. **「すぐに適用」**を選択し、DBインスタンスの変更を実行する。

![](../../images/0032-02.png)

### 4.2 Single-AZへの変更結果

変更後、RDSの「設定」タブで以下を確認した。

| 確認項目 | 結果 |
|---|---|
| ステータス | `利用可能` |
| マルチAZ | `なし` |
| 配置AZ | `ap-northeast-1c` |

![](../../images/0032-03.png)

**Multi-AZからSingle-AZへの変更は完了したが、RDSは当初想定した1aではなく1cに配置された。**

### 4.3 ネットワークインターフェイスで配置Subnetを確認

EC2コンソールの「ネットワークインターフェイス」で、RDSのSecurity Group `chinese-output-forge-rds-sg`を使うインターフェイスを検索した。

| 確認項目 | 結果 |
|---|---|
| 該当件数 | 1件 |
| AZ | `ap-northeast-1c` |
| Subnet ID | `subnet-0f89c37f618be3534` |
| Security Group | `chinese-output-forge-rds-sg` |
| 対応するSubnet | `chinese-output-forge-private-db-subnet-1c`（`10.1.31.0/24`） |

この時点の実際の配置に合わせ、DB用Subnetの役割を以下のように整理し直した。

- **`10.1.31.0/24`（1c）**：Single-AZ RDSが配置されているSubnet
- **`10.1.30.0/24`（1a）**：DB Subnet Groupのために残すSubnet

これは**Single-AZ化直後の確認結果**であり、後続の032-06で実施するAZ変更とは区別する。

## 5. Single-AZ化後のアプリケーション動作確認

RDSの変更後も、既存の高可用性環境を経由して`https://chinese-output-forge.com`にアクセスし、次の操作を確認した。

| 確認内容 | 結果 |
|---|---|
| HTTPSでのサイト表示 | 正常 |
| ログイン | 成功 |
| 問題一覧などDBからの読み込み | 正常に表示 |
| 問題の新規追加 | 成功 |
| 再読み込み後の追加データ保持 | 確認できた |

問題追加時の画面：

![](../../images/0032-04.png)

再読み込み後のデータ保持確認：

![](../../images/0032-05.png)

**RDSをSingle-AZに変更しても、既存アプリケーションからの接続とデータの読み書きが正常に行われることを確認した。**

## 6. この章の作業結果

- RDS `chinese-output-forge-db` をMulti-AZからSingle-AZへ変更した。
- 変更直後の配置先が`ap-northeast-1c`、Subnetが`10.1.31.0/24`であることを確認した。
- 既存のWebアプリケーションからログイン・データ参照・追加・保持を確認した。
- 旧環境は残したままとし、次章で新しいElastic Beanstalk Single Instance環境を構築する。
