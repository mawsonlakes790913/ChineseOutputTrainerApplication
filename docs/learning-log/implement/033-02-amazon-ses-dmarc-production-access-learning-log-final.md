# 033-02 DMARCとドメイン認証、Amazon SESのサンドボックス（学習ログ）

## はじめに

033-01では、メールのHeader FromとEnvelope Fromの違い、SPFによる送信サーバーの検証、DKIMによる電子署名の検証を整理した。しかし、**SPFやDKIMの認証が成功しただけで、受信者が画面で見る送信元まで正当だと言えるのか**という疑問が残る。

DMARCの仕組みとポリシー選択の理由、Amazon SESの本番アクセス申請を通じて理解したことを整理する。Route 53の操作やAWSへの申請・回答は[033-02実装ログ](./033-02-amazon-ses-dmarc-production-access-development-log.md)に記録する。

## 1. SPFがPASSしてもHeader Fromの正当性は保証されない

SPFが主に照合するのは、**Envelope Fromのドメイン**と、受信サーバーが確認した**実際の送信元IPアドレス**である。

例えば、攻撃者が次のようなメールを送信した場合を考える。

```text
Header From:   noreply@chinese-output-forge.com
Envelope From: attacker@evil-example.com
```

受信者のメール画面には`noreply@chinese-output-forge.com`が送信元として表示され得る。しかしSPFは、`evil-example.com`のポリシーを参照する。攻撃者が自分で管理する`evil-example.com`からの送信を正しく許可していれば、**SPF自体はPASSする可能性がある**。

```text
攻撃者の送信サーバー
    ↓
Envelope From: attacker@evil-example.com
    ↓
evil-example.com のSPFを確認
    ↓
送信元IPが許可されていれば SPF PASS

一方、受信者に見えるHeader Fromは
noreply@chinese-output-forge.com
```

逆に、攻撃者がEnvelope Fromそのものを`xxxxx@mail.chinese-output-forge.com`と偽装し、Amazon SES以外の許可されていないサーバーから送信した場合は、033-01で設定した`include:amazonses.com`に照らしてSPF認証に成功しない。

つまり、**Envelope Fromのなりすましを検出すること**と、**Header Fromのなりすましを検出すること**は別の問題である。

## 2. DKIMがPASSしても同じ問題が残る

DKIMは、送信側が付けた電子署名を公開鍵で検証する。署名には、例えば次のような署名ドメインが含まれる。

```text
DKIM-Signature: d=evil-example.com; ...
```

攻撃者が自身のドメイン`evil-example.com`で有効な署名を付ければ、DKIMの署名検証そのものはPASSし得る。しかし、Header Fromが`chinese-output-forge.com`であれば、**署名の正しさだけでは表示上の送信元の正当性は証明されない**。

このSPFとDKIMそれぞれの限界を例示したうえで、DMARCが必要になる理由を整理した。

## 3. DMARCが確認するAlignment（ドメイン整合性）

DMARC（Domain-based Message Authentication, Reporting and Conformance）は、SPF・DKIMの認証結果を利用しながら、認証されたドメインと**Header Fromのドメインが整合しているか**を確認する仕組みである。この整合性を**Alignment（アライメント）**と呼ぶ。

DMARCは、単にSPFとDKIMの両方がPASSしたかを確認する仕組みではない。基本的には、**SPFがPASSしてHeader Fromと整合するか、DKIMがPASSしてHeader Fromと整合するかの、少なくとも一方**を満たす必要がある。

先ほどの攻撃者の例を整理すると、次のようになる。

| 比較対象 | ドメイン | Header Fromとの関係 |
|---|---|---|
| Header From | `chinese-output-forge.com` | 基準 |
| SPFで認証されたEnvelope From | `evil-example.com` | 整合しない |
| DKIM署名の`d=` | `evil-example.com` | 整合しない |

この場合、SPFやDKIMの個別の検証がPASSしても、DMARCのAlignmentを満たさない。

### 今回の正規メールではどうなるか

Chinese Output ForgeからSESを使用して送信する場合、想定しているドメインは次のとおりである。

```text
Header From
  noreply@chinese-output-forge.com
        │
        ├─ SPF側
        │   Envelope From: xxxxx@mail.chinese-output-forge.com
        │   → 組織ドメインが共通
        │
        └─ DKIM側
            d=chinese-output-forge.com
            → Header Fromのドメインと一致
```

カスタムMAIL FROMに使用する`mail.chinese-output-forge.com`は、Header Fromの`chinese-output-forge.com`と完全一致しない。しかし、**Relaxed Alignment**では組織ドメインが一致していれば整合性を満たせる。033-01でカスタムMAIL FROMを設定した理由が、ここでつながった。

DKIM側も、署名ドメイン`d=chinese-output-forge.com`がHeader Fromと一致する構成になっている。ただし、**設定上Alignmentを満たせること**と、**実際に受信したメールで認証成功を確認したこと**は区別する。今回DMARCレコードを追加した時点では、実メールの認証結果までは確認していなかった。

## 4. DMARCポリシーは誰が決め、誰が参照するのか

DMARCでは、Header Fromのドメインを管理する側が、認証に失敗したメールをどう扱ってほしいかというポリシーをDNSに公開する。

例えば、`chinese-output-forge.com`をHeader Fromに指定したメールを受け取ったサーバーは、次のDNS名に公開されたTXTレコードを参照する。

```text
_dmarc.chinese-output-forge.com
```

攻撃者がHeader Fromだけを偽装しても、**正当なドメイン管理者がRoute 53へ登録したDMARCポリシーを、攻撃者が勝手に変更できるわけではない**。

```text
受信サーバー
    ↓
Header Fromのドメインを確認
    ↓
_dmarc.chinese-output-forge.com をDNSで検索
    ↓
ドメイン管理者が公開したDMARCポリシーを取得
    ↓
認証・Alignmentの結果とポリシーを踏まえて処理
```

DMARCポリシーは受信側への要求・方針であり、実際の取り扱いには受信側の判断も関係する。

## 5. なぜ最初から`p=reject`にしなかったのか

DMARCの代表的なポリシーは次の3種類である。

| ポリシー | ドメイン管理者が受信側に示す方針 |
|---|---|
| `p=none` | DMARC失敗を理由とした隔離・拒否を要求しない |
| `p=quarantine` | 失敗メールを迷惑メールなどとして隔離するよう要求する |
| `p=reject` | 失敗メールを拒否するよう要求する |

今回Route 53へ設定した値は次のとおりである。

```text
レコード名: _dmarc.chinese-output-forge.com
タイプ: TXT
値: v=DMARC1; p=none;
TTL: 300秒
```

**SPFとDKIMのDNS設定は済んでいても、実際に送信したメールのDMARC認証成功をまだ確認していない**ことが、`p=none`を選択した理由となった。

最初から`p=reject`にすると、設定漏れや想定外の送信経路によって正規のメールまで拒否される可能性がある。そのため、まず`none`で運用を開始し、認証状況を確認したうえで必要に応じて`quarantine`や`reject`への強化を検討する、という段階的な考え方を学んだ。

なお、今回の原稿には、DMARC集計レポートの収集設定や、`quarantine`・`reject`への変更を実施した記録はない。**`p=none`を設定したことと、継続的なレポート監視を実装したことは同義ではない**。

## 6. SESのドメイン検証とサンドボックス解除は別の条件

Amazon SESにドメインを登録し、Route 53に必要なDNSレコードを設定すると、SES側のドメインステータスは「検証済み」になった。しかし、**ドメインが検証済みでも、サンドボックス環境の送信先制限は残る**。

ここで、混同しやすい条件を整理した。

| 条件 | 何を確認・制限するか |
|---|---|
| SESのドメイン検証 | そのドメインを送信元として利用できるようにするための確認 |
| サンドボックス | SESから送信できる宛先などの制限 |
| IAMによる権限 | アプリケーション・EC2がSESの送信APIを実行できるか（033-04で詳述） |

Chinese Output Forgeでは、ユーザー自身が登録したメールアドレスにパスワード再設定メールを送る必要がある。送信先を事前にすべてSESで検証する運用は想定していないため、**本番アクセス（Production access）**の申請が必要になった。

## 7. 本番アクセス申請で学んだこと

申請時には、パスワード再設定などの**トランザクションメール**を送ること、公開済みのWebサイトを運用していることを申告した。

申請を送信しただけでは、直ちにサンドボックスが解除されるわけではなかった。AWSから追加情報を求められ、次の事項について回答した。

- Webサイト・アプリケーションのURL
- 送信するメールの種類（ユーザー操作に伴うシステムメールのみ）
- 想定送信量（当初は1日数通程度）
- 受信者の取得方法（自ら登録したユーザーのメールアドレスのみ）
- バウンス・苦情への対応方針（SESの送信状況や抑制リストなどを利用する方針）
- パスワード再設定メールの内容例

特に、**メールの配送失敗（バウンス）や苦情への対応**まで質問されたことが印象的だった。技術的にSESから送信できる構成を作るだけでなく、**誰に、どのような目的で、どの程度送るのか**、不適切な再送信をどう防ぐのかという運用上の説明も必要だと理解した。

追加情報を提出した後、最終的に本番アクセスが**承認された**ことを確認している。ただし、承認日時や審査判断の詳細までは記録されていない。

また、申請時に示したバウンス・苦情管理は**運用方針**であり、独自の自動処理を実装したという記録ではない。ここも区別しておく。

## 8. 今回の学習のまとめ

今回理解したのは、メールの正当性を一つの認証結果だけで判断できないという点である。SPFは主にEnvelope Fromのドメインと送信元IPを検証し、DKIMは署名を検証する。しかし、それぞれがPASSしても、受信者に見えるHeader Fromとの関係は別途確認する必要がある。**DMARCはAlignmentによってこの関係を確認し、ドメイン管理者のポリシーを受信側へ示す。**

また、SESの**ドメイン検証**と**本番アクセスの承認**も異なる条件である。DNSレコードを正しく登録することに加え、メールの用途、受信者、送信量、バウンスや苦情への対応を説明する必要があった。

これらを整理できたことで、033-01で設定したカスタムMAIL FROM、SPF、DKIMと、今回設定したDMARC・SES本番アクセスの役割がつながった。次の033-03では、Spring BootからSESへメールを送信する方法として、SMTP方式とAWS SDK方式を比較する。

## 参考文献

- [DMARCの設定時に参照した記事](https://qiita.com/zhao-xy/items/5223a402a3d8c05b4ed1)
