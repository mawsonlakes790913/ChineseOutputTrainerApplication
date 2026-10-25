# 033-01 メール配送の仕組みとSPF・DKIM（学習ログ）

## はじめに

Chinese Output Forgeの送信元を開発用Gmailから`noreply@chinese-output-forge.com`へ移行する過程で、Amazon SESの設定画面に現れた「カスタムMAIL FROM」「SPF」「DKIM」「MX」「CNAME」の意味を調べた。本稿では、Amazon SESの独自ドメイン設定を通じて理解したメール配送と送信元認証の仕組みを整理する。Route 53への設定内容や確認結果は[033-01実装ログ](./033-01-amazon-ses-domain-dns-development-log.md)に記録する。

## 1. SESでドメイン認証が必要な理由

Amazon SESは、自分でSMTPサーバーを構築・管理しなくてもメールを送信できるAWSのサービスである。ただし、SESを利用できるからといって、任意のドメインを勝手に送信元として名乗れるわけではない。

今回使用する`chinese-output-forge.com`はRoute 53でDNSを管理している。SESが指定したDNSレコードをそのドメインのホストゾーンへ登録し、SESが検出できれば、ドメインを管理できる立場にあることを確認できる。**ドメインの取得**と、**SESで送信元として検証すること**は異なる操作だと理解した。

## 2. Header FromとEnvelope Fromは別の情報

最初に理解が必要だったのは、メールには送信元が二種類あるという点である。

| 項目 | 何を表すか | 今回の例 |
|---|---|---|
| Header From | 受信者がメールソフトで見る送信元 | `noreply@chinese-output-forge.com` |
| Envelope From | SMTPの`MAIL FROM`で指定する配送上の送信元 | `xxxxx@mail.chinese-output-forge.com` |
| MAIL FROMドメイン | Envelope Fromのアドレスのドメイン部分 | `mail.chinese-output-forge.com` |

SMTP通信では、概念的に次のように配送情報を指定する。

```text
MAIL FROM:<bounce@example.com>
RCPT TO:<user@example.com>
DATA
```

一方、受信者に表示されるヘッダーは次のようになる。

```text
From: noreply@chinese-output-forge.com
To: user@example.com
Subject: パスワード再設定
```

これを郵便の**封筒の差出人**と**封筒内の手紙に書かれた差出人**に例えていた。配送システムが参照する情報と、受信者が見る情報は一致するとは限らない。Envelope Fromは通常の本文には現れないが、受信後に`Return-Path`ヘッダーとして確認できる場合がある。

## 3. カスタムMAIL FROMを設定する意味

SESでは、カスタムMAIL FROMドメインを指定しなくても送信できる。この場合、Envelope FromにはSESが管理する`amazonses.com`配下のドメインが使用される。一方、受信者が目にするHeader Fromは`noreply@chinese-output-forge.com`である。

```text
カスタムMAIL FROMなし
Header From   noreply@chinese-output-forge.com
Envelope From xxxxx@SES管理ドメイン

カスタムMAIL FROMあり（今回）
Header From   noreply@chinese-output-forge.com
Envelope From xxxxx@mail.chinese-output-forge.com
```

今回設定した`mail.chinese-output-forge.com`は、Header Fromの`chinese-output-forge.com`と完全一致するわけではない。しかし、両者は共通の組織ドメインを持つ。この関係は、**DMARCのRelaxed Alignment**を理解する前提となる。単に独自ドメインを見た目に使用するだけではなく、配送上の送信元と受信者に見える送信元の関係を整えるための設定である。

## 4. Envelope Fromとバウンスの関係

存在しない宛先へ送信するなどして配送に失敗すると、**バウンス（Bounce）**が発生する。配送失敗の通知をどこへ返すかを判断する際、Envelope Fromが重要になる。

```text
SESから送信
    ↓
受信側で配送失敗
    ↓
Envelope Fromのドメインを参照
    ↓
配送失敗通知を送信側へ返す
```

今回カスタムMAIL FROMドメインを`mail.chinese-output-forge.com`に設定したのは、配送上の送信元にも自分のドメイン配下を使うためである。カスタムMAIL FROMを設定しない場合は、SESが管理するドメインがEnvelope Fromに使用される。カスタム設定には、後続のDMARCで学ぶ**ドメインの整合性（Alignment）**にも意味がある。

## 5. SPF：実際に接続した送信サーバーを確認する

SPF（Sender Policy Framework）は、受信側が**実際の接続元IPアドレス**を、Envelope Fromのドメインで公開されているSPFポリシーと照合する仕組みである。

今回のTXTレコードは次の内容だった。

```text
名前：mail.chinese-output-forge.com
タイプ：TXT
値："v=spf1 include:amazonses.com ~all"
```

| 要素 | 意味 |
|---|---|
| `v=spf1` | SPFポリシーであることを示す |
| `include:amazonses.com` | Amazon SES側のSPFポリシーを参照して送信元を判定する |
| `~all` | それ以外の送信元をSoftFailとして扱う |

受信側は、Envelope Fromから`mail.chinese-output-forge.com`を取り出してDNSを問い合わせ、`include`で参照するSES側のSPF情報も使って接続元IPが許可されているかを調べる。

検討したのは、**第三者が`mail.chinese-output-forge.com`をEnvelope Fromに指定し、SES以外のサーバーから送信した場合**も検討した。この場合、受信側は同じTXTレコードを確認するが、接続元IPがSES側のSPFポリシーで許可されていなければSPFはPASSしない。一方、第三者が自分の正規ドメインをEnvelope Fromに用いてHeader Fromだけを偽装した場合、SPFだけではその偽装を検出できない。この違いがDMARCを学ぶきっかけとなった。

```text
受信サーバーが接続元IPを確認
        ＋
Envelope Fromのドメインを確認
        ↓
mail.chinese-output-forge.comのTXTを取得
        ↓
include:amazonses.comを参照
        ↓
SPFの判定
```

ここで重要なのは、**SPFだけでは受信者が見るHeader Fromを直接検証しない**ことだ。Envelope Fromに使われたドメインについての送信元検証であり、Header Fromのなりすまし対策にはDMARCのAlignmentが関わる。この続きは033-02学習ログで扱う。

## 6. DKIM：電子署名と公開鍵で検証する

DKIM（DomainKeys Identified Mail）は、メールに付けた電子署名を検証し、署名したドメインと署名対象データの改ざん有無を確認する仕組みである。

- 送信側は**秘密鍵**を使って署名する。
- 受信側はDNSから対応する**公開鍵**を取得し、署名を検証する。
- 今回使用した**Easy DKIM**では、鍵をAmazon SESが管理する。

Easy DKIMでSESから示されたのは、3件のCNAMEレコードだった。

公開鍵を取得するまでの流れを段階的に整理すると、次のようになる。

```text
① SESが秘密鍵で署名する
   DKIM-Signature: d=chinese-output-forge.com; s=xxxx1
                  ↓
② 受信側が d と s からDNS名を組み立てる
   xxxx1._domainkey.chinese-output-forge.com
                  ↓
③ Route 53のCNAMEを参照する
   xxxx1.dkim.amazonses.com
                  ↓
④ SES側のDNSで公開鍵を取得する
                  ↓
⑤ 取得した公開鍵で署名を検証する
```

**CNAMEの値は公開鍵そのものではなく、公開鍵を取得するための参照先**である。実際の識別子は伏せ、ここでは説明用の値を使用する。

```text
xxxx1._domainkey.chinese-output-forge.com
    CNAME → xxxx1.dkim.amazonses.com
```

これは2つの独立したレコードではなく、**1件のCNAMEレコードの名前と参照先**である。`chinese-output-forge.com`側に公開鍵を直接書くのではなく、SESが管理するDNS名を参照するように設定する。

## 7. DKIMのセレクターは何に使われるのか

DKIM署名には、概念的に次の情報が含まれる。

```text
DKIM-Signature:
    d=chinese-output-forge.com
    s=xxxx1
```

- `d`：署名したドメイン
- `s`：鍵を識別する**セレクター**

受信側は、`s`と`d`を組み合わせて次のDNS名を作る。

```text
xxxx1._domainkey.chinese-output-forge.com
```

そのDNS名のCNAMEをたどり、SESが管理する公開鍵を取得して検証する。3件のCNAMEがあるからといって、**1通のメールの検証に3件すべてを同時使用するわけではない**。署名で指定されたセレクターに対応するDNS名が参照される。複数のレコードが用意されるのは、SES側での鍵管理・ローテーションに対応するためである。

## 8. MXレコードはSPFと役割が違う

カスタムMAIL FROMドメインにはSPF用TXTだけでなく、次のMXレコードも必要だった。

```text
名前：mail.chinese-output-forge.com
タイプ：MX
値：10 feedback-smtp.ap-northeast-1.amazonses.com
```

MX（Mail Exchange）レコードは、**そのドメイン宛てのメールをどのメールサーバーへ配送するか**を指定する。今回の用途は、Envelope Fromのドメイン宛てに返されるバウンスなどをSES側へ届けることにある。値の先頭にある`10`はMXの優先度であり、複数ある場合は通常、数値が小さいほうが優先される。

| DNSレコード | 今回の役割 |
|---|---|
| TXT（SPF） | このMAIL FROMドメインを名乗る送信サーバーを認証するための情報 |
| MX | このMAIL FROMドメイン宛ての配送失敗通知などをSESへ届ける宛先 |
| CNAME（DKIM） | SESが管理するDKIM公開鍵へたどり着くための参照先 |

同じメール送信設定で登場するDNSレコードでも、**何を認証するか／どこへ配送するか**は異なる。

## 9. 今回の理解と次の疑問

SESに独自ドメインを登録する作業は、単に送信元アドレスを変更する操作ではなかった。メールの配送情報と表示情報を区別し、SPFで送信元サーバーを、DKIMで電子署名を検証できるようDNSを整える作業だった。

ただし、**SPFまたはDKIMの認証が成功しただけで、Header Fromに表示されたドメインが正当だと判断できるわけではない**。次の033-02では、認証されたドメインとHeader Fromの整合性を扱うDMARCについて整理する。
