# 031-03 AWS高可用性構成③｜SSMを利用したRDSへのデータ移行（学習ログ）

この章では、Private Subnet内のRDSへローカルのPostgreSQLデータを移行する過程で、接続方法や復元時のエラーから学んだことを記録する。具体的な操作・コマンド・確認結果は対応する実装ログ031-03を参照する。

## RDSへのデータ移行とSession Manager

今回のRDSはPrivate Subnetにあり、さらにEC2もPrivate Subnetにある。そのため、ローカルMacからRDSへ直接接続する一般的な方法が使えなかった。

最初は「Session ManagerでEC2を操作できればdumpファイルをEC2へ持っていけばよい」と考えたが、EC2を操作できることと、ローカルファイルを転送できることは別問題だった。

そこで、Session ManagerのRemote Host Port Forwardingを利用し、次の経路を作った。

```text
Mac（localhost:15432）
    ↓
SSMトンネル
    ↓
EC2
    ↓
RDS（5432）
```

この経路を通して、Mac上の`psql`や`pg_restore`からRDSを操作できた。

また、AWS CLI、Session Manager Plugin、EC2側のSSM Agent、IAMポリシー`AmazonSSMManagedInstanceCore`は、それぞれ別の役割を担っていることを、実際に利用する中で理解できた。

## ポートフォワーディングで詰まった点

最初の`aws ssm start-session`ではリージョンを指定しておらず、`NoRegion`エラーになった。AWSマネジメントコンソールで東京リージョンを表示していても、AWS CLIには別途リージョンの指定または設定が必要だと分かった。

その後、ローカル側にもPostgreSQLと同じ`5432`を指定したところ、セッションが期待どおり確立しなかった。AWS公式ドキュメントや技術記事の例を確認し、ローカル側を`15432`、RDS側を`5432`に分けることで正常にポートフォワーディングできた。

この経験から、**ポートフォワーディングのローカル側ポートと、最終的な接続先のポートは同じである必要がない**ことを理解した。

### 参考にした資料

- [DevelopersIO：Session Managerを利用したRDS接続](https://dev.classmethod.jp/articles/2026-04-14-rds-ssm-jdbc-iam-authentication/) — Private RDSへEC2を中継して接続する構成
- [AWS Samples：RDS / Aurora PostgreSQL DBA Toolkit](https://github.com/aws-samples/sample-rds-aurora-postgres-dba-toolkit/blob/main/pre-upgrade-check/README.md) — `AWS-StartPortForwardingSessionToRemoteHost`のコマンド構成
- [Zenn：Session Manager](https://zenn.dev/taroshun32/articles/session-manager) — SSHポートを公開せずPrivate EC2へ接続する考え方
- [AWS公式：Session Managerのセッション開始とポートフォワーディング](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html#sessions-start-port-forwarding)
- [AWS公式：macOSへのSession Manager Plugin導入](https://docs.aws.amazon.com/ja_jp/systems-manager/latest/userguide/install-plugin-macos-overview.html)
- [AWS公式：AWS CLIのインストール](https://docs.aws.amazon.com/ja_jp/cli/latest/userguide/getting-started-install.html)

## PostgreSQLのdump復元

RDSにはSpring Boot / JPAによってすでにテーブル構造が作られていたため、最初に`pg_restore --clean --if-exists`で復元しようとしたところ、FOREIGN KEYの依存関係によって既存テーブルや制約を削除できず、`relation already exists`やduplicate keyなどのエラーが連鎖した。

ここで、**`--clean`を付ければ既存DBを自動的にきれいな状態へ戻せるわけではない**ことを学んだ。テーブルや制約などのオブジェクト間の依存関係を考慮する必要があった。

今回は既存RDSのデータを残す必要がなく、ローカルDBを丸ごと復元することが目的だったため、個々のテーブルを削除するのではなく、次の方法へ変更した。

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

これにより依存オブジェクトをまとめて削除し、空のスキーマへ復元した。

また、ローカルDBとRDSではPostgreSQLユーザーが異なるため、`--no-owner`と`--no-privileges`を指定して、ローカル環境のOwnerやACLを持ち込まないようにした。

復元後には`question`テーブルの件数が0件から686件になり、Spring Bootからもデータを確認できた。**復元コマンドの成否だけでなく、アプリケーションからデータを利用できることまで確認する必要がある**と学んだ。

### 参考にした資料

- [Qiita：PostgreSQLのdump / restore](https://qiita.com/chamasei/items/77822274e8d693087096) — `pg_dump`と`pg_restore`による移行の流れ
- [Qiita：PostgreSQLのバックアップ・リストア](https://qiita.com/kenogi/items/69d0e9b78324f56e62ea) — コマンドとオプション
