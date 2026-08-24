# 011 復習モードの実装 その3 - Structureによる出題条件の追加

今回の実装では、復習モードに文法・構造（Structure）による絞り込みを追加した。

当初は `Question` が `structure` を文字列として直接保持する既存設計を利用して実装を進めたが、文法・構造の説明を表示する機能まで検討した結果、Structureそのものを独立したマスタデータとして管理する設計へ変更した。

そのため今回は、単純な復習条件の追加だけではなく、

- Structureによる復習問題の絞り込み
- Structureのマスタテーブル化
- `Question` と `Structure` のリレーション化
- 既存データの移行
- 既存の復習処理の修正
- Structureの説明表示

まで行うことになった。

---

## 1. Structureを復習条件として追加する

復習モードではこれまで、

- 理解度
- 難易度
- お気に入り

などを条件として問題を絞り込めるようにしていた。

今回はここに文法・構造を追加した。

例えば、

```text
☑ 可能補語
☑ 把構文
☐ 比較構文
☐ 因果複文
```

のように選択することで、復習したい文法・構造を指定できるようにする。

### Structureは複数選択とする

文法・構造は1種類だけではなく、

```text
可能補語 + 把構文
```

のように複数をまとめて復習することが考えられる。

そのためラジオボタンではなくチェックボックスとした。

また、文法・構造の種類が多いため、

```text
すべて選択
すべて解除
```

も用意した。

これにより、特定のStructureだけを除外するといった使い方もしやすくなった。

---

## 2. 文法・構造の選択肢が増えた場合のUIを考える

Structureの種類は現時点でも20種類存在する。

すべてをそのまま表示すると、復習メニューの文法・構造欄だけでかなりの高さを使用する。

さらに今後Structureが増える可能性もある。

そこで、初期状態では一定の高さまで表示し、

```text
すべて表示
```

を押した場合のみ全件表示するようにした。

CSSでは、

```css
.structure-list {
    max-height: 100px;
    overflow: hidden;
}

.structure-list.expanded {
    max-height: none;
}
```

としている。

通常時は `max-height` を設定し、展開時に `expanded` クラスを追加して高さ制限を解除する。

JavaScriptでは、

```javascript
const expanded =
    structureList.classList.toggle("expanded");
```

によってクラスの追加と削除を切り替えている。

このように、

```text
HTML
→ 対象となる要素を定義

CSS
→ 通常時と展開時の表示を定義

JavaScript
→ expandedを付け外しする
```

という役割分担になっている。

---

## 3. Structureを文字列のまま管理することの問題

当初の `Question` は、

```java
@Column(name = "structure", nullable = false)
private String structure;
```

となっていた。

つまり、

```text
Question 1
structure = "可能補語"

Question 2
structure = "可能補語"

Question 3
structure = "把構文"
```

のように、各Questionが文法・構造名を文字列として直接保持していた。

復習条件としてStructureを使用するだけなら、この構造でも実装できる。

実際、最初は、

```sql
SELECT DISTINCT structure
FROM question
ORDER BY structure
```

としてQuestionからStructure一覧を取得した。

しかし、文法・構造名を復習条件として表示すると別の問題が出てきた。

例えばユーザーが、

```text
可能補語
比例複文
累加複文
```

という名称を見ても、その文法・構造が具体的に何を意味するのか分からない可能性がある。

そこで、Structureごとに説明を持たせることにした。

```text
可能補語

動作や結果が実現できるか、
できないかを表す形式。
```

こうなるとStructureは単なるQuestionの属性値ではなく、

```text
Structure
├── ID
├── 名前
└── 説明
```

という独立したデータとして扱った方が自然になる。

そのため、途中でStructureをマスタテーブル化する設計へ変更した。

---

## 4. Structureをマスタテーブルとして管理する

新しく、

```text
structure
├── structure_id
├── name
├── description_zh_cn
└── description_zh_tw
```

というテーブルを作成した。

Entityも、

```java
@Entity
@Table(name = "structure")
@Getter
@Setter
public class Structure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "structure_id")
    private Long structureId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description_zh_cn", nullable = false)
    private String descriptionZhCn;

    @Column(name = "description_zh_tw", nullable = false)
    private String descriptionZhTw;
}
```

とした。

### nameをUNIQUEにする理由

`name` には、

```text
可能補語
把構文
因果複文
```

などのStructure名を保持する。

同じ「可能補語」というStructureが複数レコード存在する必要はないため、

```java
unique = true
```

として重複を禁止した。

---

## 5. Structureの説明を2種類保持する理由

当初は、

```text
description
```

を1つだけ持たせることも考えた。

しかし、本アプリでは、

```text
MAINLAND
TAIWAN
```

という学習対象言語を切り替えられる。

説明そのものが日本語であっても、説明中に中国語の代表表現を含める場合がある。

例えば因果複文なら、

```text
大陸普通話向け

原因・理由と、それによって生じる結果を表す複文。
「因为～所以～」「既然～就～」など。
```

に対して、

```text
台湾華語向け

原因・理由と、それによって生じる結果を表す複文。
「因為～所以～」「既然～就～」など。
```

となる。

そのため、

```text
description_zh_cn
description_zh_tw
```

の2つを保持することにした。

ここで重要なのは、この切り替えが**サイト表記言語ではなく学習対象言語に連動する**ことである。

```text
session.languageVariant

MAINLAND
└── descriptionZhCn

TAIWAN
└── descriptionZhTw
```

となる。

---

## 6. QuestionとStructureをManyToOneにする

Structureを独立したEntityにしたため、Questionも文字列ではなくStructureを参照するように変更した。

変更前は、

```java
@Column(name = "structure", nullable = false)
private String structure;
```

だった。

これを、

```java
@ManyToOne
@JoinColumn(name = "structure_id", nullable = false)
private Structure structure;
```

へ変更した。

関係としては、

```text
Structure
    1
    │
    │
    N
Question
```

となる。

例えば、

```text
可能補語
    │
    ├── 这么多菜，我们吃不完。
    ├── 这个箱子太重了，我搬不动。
    └── 他说得太快，我听不懂。
```

のように、1つのStructureに複数のQuestionが属する。

したがってQuestion側から見ると、

```text
多くのQuestion
        ↓
1つのStructure
```

なので `@ManyToOne` となる。

`@JoinColumn(name = "structure_id")` は、Question側でStructureとの関連を保持するDBカラムが `structure_id` であることを指定している。

---

## 7. 既存データがある状態でリレーションを変更する

今回特に注意が必要だったのが、すでに60問のQuestionが存在していたことである。

いきなり、

```java
@ManyToOne
@JoinColumn(name = "structure_id", nullable = false)
private Structure structure;
```

としてしまうと、既存60問には `structure_id` が存在しない。

そのため、段階的に移行した。

まず、

```java
@ManyToOne
@JoinColumn(name = "structure_id")
private Structure structure;
```

として一時的にNULLを許可した。

するとDBでは、

```text
question
├── question_id
├── structure       ← 旧文字列カラム
└── structure_id    ← 新しいFK
```

という新旧カラムが共存する状態になる。

この状態を利用して、

```sql
UPDATE question q
SET structure_id = s.structure_id
FROM structure s
WHERE q.structure = s.name;
```

を実行した。

つまり、

```text
question.structure
"可能補語"
        ↓
structure.name
"可能補語"
        ↓
structure.structure_id
6
        ↓
question.structure_id
6
```

という対応付けを行った。

この方法なら、既存の文字列を移行の手掛かりとして利用できる。

移行完了を確認してから、

```sql
ALTER TABLE question
DROP COLUMN structure;
```

で旧カラムを削除した。

最後に、

```java
@JoinColumn(name = "structure_id", nullable = false)
```

としてQuestionからStructureへの関連を必須に戻した。

既存データを持つカラムをリレーションへ変更する場合は、

```text
新しいカラムを追加
↓
旧データを利用して新カラムへ移行
↓
移行結果を確認
↓
旧カラムを削除
↓
NOT NULL制約を設定
```

という順番で行えば、安全に移行できる。

---

## 8. マスタ化によってStructure一覧の取得方法も変わる

Structureをマスタ化する前は、

```java
public List<String> findStructures() {
    return questionRepository.findDistinctStructures();
}
```

としていた。

これは、

```text
Question
↓
structure文字列をDISTINCT
↓
Structure一覧
```

という取得方法である。

しかしStructureテーブルを作った後は、この処理は不要になる。

```java
public List<Structure> findStructures() {
    return structureRepository.findAll();
}
```

とすればよい。

つまり、

```text
変更前

QuestionRepository
└── QuestionからStructureを逆算する
```

から、

```text
変更後

StructureRepository
└── Structureそのものを取得する
```

へ変わった。

マスタテーブルを作ったことで、Structure一覧の取得元も明確になった。

---

## 9. HTMLでは表示する値と送信する値を分ける

以前の `structures` は、

```java
List<String>
```

だった。

そのためHTMLでは、

```html
th:value="${structure}"
th:text="${structure}"
```

でよかった。

しかし現在は、

```java
List<Structure>
```

である。

そこで、

```html
<input
    name="structureIds"
    th:value="${structure.structureId}">

<span th:text="${structure.name}">
</span>
```

とした。

これにより、

```text
ユーザーに表示する値
→ structure.name
→ 可能補語

サーバーへ送信する値
→ structure.structureId
→ 6
```

と分けられる。

マスタデータを使用する場合、名称そのものではなくIDを検索条件としてやり取りすることで、DB上のリレーションとそのまま対応させられる。

---

## 10. 問題数取得と問題取得の検索条件を揃える

復習メニューには、

```text
○問
```

という問題数が表示される。

一方、「開始」を押すと実際のQuestion一覧を取得する。

そのため、

```text
問題数を数える条件
```

と、

```text
実際に問題を取得する条件
```

は一致している必要がある。

今回Structureを追加したため、問題数取得には、

```sql
AND q.structure_id IN (:structureIds)
```

を追加した。

同様に、問題取得にも、

```sql
AND q.structure_id IN (:structureIds)
```

を追加した。

もし片方だけにStructure条件を追加すると、

```text
画面表示
8問

↓

実際に開始

20問
```

のような不整合が発生する。

検索機能では、件数取得と実データ取得が別メソッドになっている場合、検索条件を両方へ反映する必要がある。

---

## 11. StructureテーブルをJOINしなくても検索できる

Structureをマスタテーブル化したので、当初は復習問題を検索するときにも、

```sql
JOIN structure s
ON q.structure_id = s.structure_id
```

が必要かと考えた。

しかし今回の検索ではStructureの名称や説明をSQL内で使用しない。

必要なのは、

```text
選択されたstructure_id
```

と、

```text
question.structure_id
```

が一致しているかどうかだけである。

そのため、

```sql
AND q.structure_id IN (:structureIds)
```

だけで検索できる。

Structureテーブルの、

```text
name
description_zh_cn
description_zh_tw
```

をSQL内で利用する場合にはJOINが必要になるが、外部キーのIDだけで判定できる場合はJOINする必要はない。

---

## 12. Structureの説明をTooltipで表示する

Structureの名称だけでは内容が分からない場合があるため、カーソルを合わせると説明を表示できるようにした。

ここではBootstrapのTooltipを使用した。

Tooltipとは、ある要素にカーソルを合わせたときに、その付近に一時的に表示される補足説明である。

HTMLでは、

```html
data-bs-toggle="tooltip"
data-bs-placement="top"
```

によってTooltipを使用する要素と表示位置を指定する。

説明内容は、

```html
th:data-bs-title="${session.languageVariant != null
                    && session.languageVariant.name() == 'TAIWAN'
                    ? structure.descriptionZhTw
                    : structure.descriptionZhCn}"
```

としている。

これにより、

```text
TAIWAN
→ descriptionZhTw

それ以外
→ descriptionZhCn
```

となる。

JavaScriptでは、

```javascript
const tooltipTriggerList =
    document.querySelectorAll('[data-bs-toggle="tooltip"]');

tooltipTriggerList.forEach(element => {
    new bootstrap.Tooltip(element);
});
```

としている。

ここでは、

```text
data-bs-toggle="tooltip"
```

が設定された要素をすべて取得し、それぞれについてBootstrapのTooltipを生成している。

さらにCSSで、

```css
.tooltip-inner {
    max-width: 400px;
    padding: 12px 16px;
    font-size: 1rem;
    text-align: left;
}
```

として、説明文を読みやすくした。

役割として整理すると、

```text
HTML
→ どの要素に何の説明を表示するか

JavaScript
→ Tooltipを実際に動作させる

CSS
→ Tooltipの見た目を調整する
```

となる。

---

## 13. 実装を終えて

復習モードの実装自体は、本来そこまで難解なものではない。

通常学習モードをすでに実装しているため、そこで作成したController、Service、Repository、問題表示画面などを使い回したり、処理を流用したりできる部分が多いからである。

復習モード特有の部分としては、メニュー画面で検索条件に応じた問題数を動的に表示するための `@ResponseBody` を使用したControllerや、複数の検索条件を組み合わせるRepositoryのクエリを作成するときに少々頭を使う程度であった。

しかし今回は、実装途中で `question` テーブルの `structure` フィールドを独立した `structure` テーブルとして管理する方向へ設計を変更したため、結果的にかなり遠回りな実装となった。

実際の流れは以下のようになった。

```text
旧データ構造でメニュー画面表示・問題取得処理を実装
（本チャプター1・2）
        ↓
データ構造の変更を決定
        ↓
requirements内の各要件定義・設計書を見直して修正
        ↓
旧データ構造から新データ構造へ移行
（本チャプター3）
        ↓
旧データ構造を前提に作成した復習処理を
新データ構造に合わせて修正
（本チャプター4）
        ↓
文法・構造のチェックボックスに説明を表示
（本チャプター5）
```

特にデータ移行では、すでに登録されている60問のQuestionを壊さないようにする必要があった。

そのため、いきなり旧 `structure` カラムを削除するのではなく、

```text
旧 structure（文字列）
+
新 structure_id（外部キー）
```

を一時的に共存させ、旧 `structure` の値と `structure.name` を対応させて `structure_id` へ変換した。

その後、すべてのQuestionに正しく `structure_id` が設定されたことを確認してから旧 `structure` カラムを削除し、最後に `structure_id` を必須とした。

既存データを維持したままデータ構造を変更するという、通常の新規機能追加とは少し異なる作業も経験することになった。

## 実装途中で2回の設計変更が発生した

今回特に反省すべき点は、実装途中で設計変更が2回発生したことである。

最初の変更は、

```text
Question.structure
String
```

として問題ごとに文法・構造名を直接保持する設計から、

```text
Structure
    1
    │
    N
Question
```

としてStructureを独立したマスタテーブルにする変更である。

さらにStructureを作成した後、

```text
Structure
├── structureId
├── name
└── description
```

とするだけでは不十分であることに気づいた。

本アプリケーションでは大陸普通話と台湾華語を切り替えて学習でき、説明の中に、

```text
因为～所以～
```

のような中国語を含める場合、大陸普通話と台湾華語では、

```text
因为～所以～
因為～所以～
```

のように表記が異なる。

そこでさらに、

```text
Structure
├── structureId
├── name
├── descriptionZhCn
└── descriptionZhTw
```

へ変更した。

つまり、

```text
1回目
StructureをQuestionの文字列フィールドから
独立したマスタテーブルへ変更

2回目
Structureのdescriptionを
大陸普通話向け・台湾華語向けに分離
```

という2回の路線変更が発生した。

これは、要件定義の段階でStructureをどのように利用するのかを十分に詰め切れていなかったことが原因であり、要件定義の甘さが露呈した部分ではある。

## 一方で、実装したからこそ気づけたことでもある

ただし、今回の変更を単純に「要件定義に失敗した」と考えるべきかというと、それも少し違うように思う。

今回、

> 「文法・構造」から問題を絞れたらもっと便利ではないか

と考えたことからStructureによる絞り込みを追加した。

そして実際に復習メニューへ、

```text
可能補語
因果複文
比例複文
累加複文
```

などを並べてみたことで、別の問題に気づいた。

中国語は、文法・構造の正式名称を覚えてから使用するというより、実際の表現を見たり使ったりしながら感覚的に身につけることも多い。

そのため、

```text
可能補語
比例複文
累加複文
```

といった文法名だけを見せられても、

> 「これはどの表現のことだったか？」

とすぐにはイメージできない場合がある。

そこで、

```text
可能補語
    ↓ カーソルを合わせる
動作や結果が実現できるか、
できないかを表す形式。
```

のように、その場で簡単な説明を確認できれば便利なのではないかと考えた。

しかし説明を持たせようとすると、Questionが `structure` を単なる文字列として保持する現在のデータ構造では不自然になる。

そこからStructureを独立したマスタデータとして扱う必要性に気づいた。

さらに実際に説明文を作成していく中で、簡体字と繁体字の違いにも気づき、`descriptionZhCn` と `descriptionZhTw` を分ける必要性も見えてきた。

この一連の気づきは、要件定義書だけを見ながら机上で設計していた段階で同じように発見できたかというと疑問がある。

むしろ、中国語学習者・中国語話者として自分自身でアプリケーションを少しずつ実装し、実際の画面を確認していたからこそ、

```text
これでは文法名だけ見ても分かりにくい
↓
その場で説明を確認できた方がよい
↓
では説明はどこに保持するのか
↓
Structureを独立させた方がよい
↓
説明中の中国語は普通話と國語で表記が違う
```

と段階的に気づくことができたとも言える。

その意味では、今回の設計変更は単純な手戻りではなく、実際にアプリケーションを作りながら要求そのものを発見した結果でもある。

## 気づいた段階で設計変更したことについて

Structureを独立させる必要性に気づいた段階で、そのまま旧設計による復習モードの実装を完成させることはせず、一度開発を止めてデータ構造そのものを変更した。

これは、現時点ではまだQuestionが60問程度であり、Structureを利用している機能も限定されていたためである。

この段階であれば、

```text
requirements修正
↓
DB変更
↓
既存60問を移行
↓
既存コードを修正
```

という手戻りで済む。

一方、このまま旧設計で、

```text
問題検索
文法・構造ガイド
AI生成問題
管理者向け問題管理
```

などStructureを利用する機能をさらに増やしてから変更すると、修正範囲はさらに大きくなる。

そのため、変更が必要だと判断した時点で開発を一度止め、requirementsまで遡って修正したこと自体は適切だったと思う。

## 今回の経験から

要件定義時にすべてを見通し、その内容だけで最後まで開発できるのであれば、それに越したことはない。

今回も、最初から、

```text
Structureは独立したマスタデータ
descriptionは普通話・國語の2種類
```

と決められていれば、本チャプター1・2で作成した処理を本チャプター4で再び修正する必要はなかった。

その点については、今後の要件定義でより先の利用方法まで考える必要がある。

一方で、実際にアプリケーションを開発し、画面を操作して初めて気づける要求も存在する。

特に今回の、

```text
文法名だけでは分かりにくい
カーソルを合わせるだけで説明を確認できると便利
```

という発想は、実際に復習メニューへ文法・構造を並べたことで具体的に見えてきたものである。

したがって、実装途中で設計変更が発生したこと自体を必要以上にマイナスに捉えるのではなく、

```text
なぜ要件定義時に気づかなかったのか

要件定義時に気づくことが可能だったのか

今変更するのと後から変更するのではどちらがよいのか
```

を判断することが重要だと感じた。

今回については要件定義の詰めが甘かった部分がある一方、実装して実際にアプリケーションを使ったからこそ発見できた要求もあった。

そして、必要性に気づいた時点で後回しにせず、データ構造とrequirementsまで遡って修正できたことは、今後の機能追加を考えれば結果的によかったと思う。