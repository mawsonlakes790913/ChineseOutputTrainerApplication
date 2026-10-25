# 033-01 Amazon SESへの独自ドメイン登録とDNS設定（実装ログ）

## 1. 目的と対象

Chinese Output Forgeの会員登録完了メール・パスワード再設定メールについて、開発・動作確認用のGmailアドレスに代わり、独自ドメインの送信元`noreply@chinese-output-forge.com`を使用するため、Amazon SESの送信ドメインを設定した。

対象の既存ドメインは`chinese-output-forge.com`。DNSはRoute 53で管理している。今回は新しいドメインの取得や、メールを受信するためのメールボックスの作成は行わない。

この段階ではSESのドメイン登録とDNSレコードの追加・検証までを行い、DMARC設定、本番アクセス申請、Spring Bootからの送信処理の変更は後続の実装ログに分ける。

## 2. Amazon SESの初期設定

AWSマネジメントコンソールでAmazon SESを開き、初期設定を開始した。

送信元として使用する予定のアドレスは次のとおり。

```text
noreply@chinese-output-forge.com
```

パスワード再設定などの少量のシステムメールを送信する目的のため、初期設定時に表示された次の追加機能は使用しない方針とした。

- Virtual Deliverability Manager
- エンゲージメントの追跡
- 最適化された共有配信
- 自動検証
- マネージド専用IP
- テナント管理

## 3. 送信ドメインとカスタムMAIL FROMドメインの登録

SESに、既存の独自ドメインを送信ドメインとして登録した。

```text
送信ドメイン：chinese-output-forge.com
```

あわせて、カスタムMAIL FROMドメインを次の値に設定した。

```text
MAIL FROMドメイン：mail.chinese-output-forge.com
```

メールのヘッダーに表示する送信元と、配送時に使用するMAIL FROMドメインを区別した構成とした。

```text
Header From：noreply@chinese-output-forge.com
Envelope From：xxxxx@mail.chinese-output-forge.com
```

`xxxxx`は説明用の表記であり、実際の固定アドレスとして設定した値ではない。

## 4. SESから提示されたDNSレコードの確認

送信ドメインとカスタムMAIL FROMドメインを設定した後、SESが提示したDNSレコードを確認した。Route 53へ追加するレコードは次のとおり。

| 用途 | レコードタイプ | 件数 |
|---|---|---:|
| Easy DKIM | CNAME | 3 |
| カスタムMAIL FROM（バウンスの返送先） | MX | 1 |
| カスタムMAIL FROM（SPF） | TXT | 1 |

### 4.1 DKIM用CNAME

SESのEasy DKIMで提示された3件のCNAMEレコードを使用する。元ログではセレクターやCNAMEの実値を伏せた例で記載しているため、ここでも具体的な識別子は記載しない。

```text
xxxx1._domainkey.chinese-output-forge.com → xxxx1.dkim.amazonses.com
xxxx2._domainkey.chinese-output-forge.com → SESが提示した対応先
xxxx3._domainkey.chinese-output-forge.com → SESが提示した対応先
```

各レコードは、SESが提示した名前と値の組み合わせで登録する。

### 4.2 SPF用TXT

カスタムMAIL FROMドメインに、以下のTXTレコードを設定した。

```text
レコード名：mail.chinese-output-forge.com
タイプ：TXT
値："v=spf1 include:amazonses.com ~all"
```

### 4.3 カスタムMAIL FROM用MX

同じMAIL FROMドメインに、次のMXレコードを設定した。

```text
レコード名：mail.chinese-output-forge.com
タイプ：MX
値：10 feedback-smtp.ap-northeast-1.amazonses.com
```

## 5. Route 53への登録

Route 53で`chinese-output-forge.com`のホストゾーンを開き、SESから提示されたDKIM用CNAMEレコード3件と、カスタムMAIL FROM用MX・TXTレコードを登録した。

この作業では、既存のWebアプリケーション用Aレコード、NSレコード、SOAレコードは変更していない。

登録後の対象レコードは以下の構成となった。

```text
chinese-output-forge.com
├─ A          Webアプリケーションの接続先（既存）
├─ NS         権威DNSサーバー（既存）
├─ SOA        DNSゾーン管理情報（既存）
├─ CNAME × 3  DKIM（追加）
├─ MX         カスタムMAIL FROM（追加）
└─ TXT        SPF（追加）
```

## 6. SESでドメイン認証を確認

DNSレコード登録後、SESの画面に戻ってドメインのIDステータスを確認した。

登録直後は次の状態だった。

```text
検証保留中
```

しばらく待って画面を更新すると、ステータスが次のように変わった。

```text
検証済み
```

これにより、`chinese-output-forge.com`のSESにおけるドメイン認証が完了したことを確認した。

## 7. この段階の結果と残作業

- SESに`chinese-output-forge.com`を送信ドメインとして登録した。
- カスタムMAIL FROMドメイン`mail.chinese-output-forge.com`を設定した。
- Route 53へDKIM用CNAME 3件、SPF用TXT 1件、MAIL FROM用MX 1件を登録した。
- SESのIDステータスが「検証済み」になったことを確認した。

**この時点では、SESのサンドボックス制限は解除されておらず、DMARCレコードも未設定だった。** また、Spring Bootからのメール送信成功を確認した段階ではない。

次の実装ログでは、DMARC用TXTレコードの追加と、SESの本番アクセス申請・審査対応を扱う。
