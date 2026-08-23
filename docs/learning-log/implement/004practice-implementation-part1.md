# 004 通常学習の実装 その1

今回は、DBに登録されている問題を使用する通常学習機能の基本部分を実装した。

この学習ログでは実装内容をすべて記録するのではなく、今回新しく確認したこと、設計上検討したこと、実装後の動作確認によって気づいた問題を中心に記録する。


# 1. 通常学習で問題を取得するための条件

通常学習では、以下の条件から問題セットを取得する。

```text
LanguageVariant
    +
Difficulty
    +
Range
```

`LanguageVariant` では、

```java
public enum LanguageVariant {
    MAINLAND,
    TAIWAN
}
```

として、

- `MAINLAND`：普通話
- `TAIWAN`：國語

を区別している。

そのため、例えば同じ初級・1～50問でも、

```text
MAINLAND + BEGINNER + 1～50
TAIWAN   + BEGINNER + 1～50
```

は別の問題セットとして取得する必要がある。


# 2. EnumをRepositoryの条件として使用する

各難易度の問題数を取得するため、Repositoryに以下を定義した。

```java
long countByLanguageVariantAndDifficulty(
        LanguageVariant languageVariant,
        Difficulty difficulty
);
```

DB上では、

```text
MAINLAND
TAIWAN

BEGINNER
INTERMEDIATE
ADVANCED
```

のような文字列として保存されている。

当初は、

```java
languageVariant.name()
difficulty.name()
```

によって `String` に変換してRepositoryへ渡す必要があるのではないかと考えた。

しかし、Entity側で、

```java
@Enumerated(EnumType.STRING)
private LanguageVariant languageVariant;
```

```java
@Enumerated(EnumType.STRING)
private Difficulty difficulty;
```

としているため、Spring Data JPAのメソッド名からクエリを生成する場合はEnumをそのまま引数として使用できる。

```java
questionRepository.countByLanguageVariantAndDifficulty(
        languageVariant,
        Difficulty.BEGINNER
);
```

一方、今回問題セットの取得に使用したNative Queryでは、

```java
@Query(value = """
        SELECT *
        FROM question
        WHERE language_variant = :languageVariant
        AND difficulty = :difficulty
        ORDER BY question_id
        LIMIT 100 OFFSET :offset
        """, nativeQuery = true)
```

としているため、

```java
questionRepository.findQuestionsByLanguageVariantAndDifficulty(
        languageVariant.name(),
        difficulty.name(),
        offset
);
```

のように文字列として渡している。


# 3. 問題数とRangeをDTOにまとめる

通常学習メニューでは、各難易度について、

```text
初級
├── 問題総数
└── Range一覧

中級
├── 問題総数
└── Range一覧

上級
├── 問題総数
└── Range一覧
```

という情報が必要になる。

そこで、画面表示に必要な情報を `PracticeMenuDto` にまとめた。

```java
@Data
public class PracticeMenuDto {

    private long beginnerCount;
    private List<Range> beginnerRanges;

    private long intermediateCount;
    private List<Range> intermediateRanges;

    private long advancedCount;
    private List<Range> advancedRanges;
}
```

Controllerへ個別に多数の値を渡すのではなく、学習メニューで必要となる情報を1つのDTOとしてまとめて扱える。


# 4. Rangeをクラスとして扱う

通常学習では問題を50問単位の問題セットに分割する。

例えば125問存在する場合、

```text
1～50
51～100
101～125
```

となる。

この開始位置と終了位置を扱うために `Range` クラスを作成した。

```java
public class Range {

    private long start;
    private long end;

    public Range(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public String getDisplayText() {
        return start + "～" + end;
    }
}
```

これによって、

```java
range.getStart()
range.getEnd()
```

をプログラム上の値として利用しながら、

```java
range.getDisplayText()
```

によって、

```text
1～50
```

という表示にも利用できる。


# 5. 問題セットをSessionで管理する

通常学習開始時に取得した問題セットはSessionへ保存する。

```java
session.setAttribute("practiceQuestions", questions);
session.setAttribute("practiceCurrentPage", 0);
```

それぞれ、

```text
practiceQuestions
    → 現在取り組んでいる問題セット

practiceCurrentPage
    → 中断した位置
```

を管理する。

これによってHTTPリクエストをまたいで同じ問題セットを使用でき、中断した場合も後から同じ位置から再開できる。


# 6. 実装後に気づいた点

通常学習の基本フローを完成させ、実際に一連の操作を行ったところ、設計段階では気づかなかった問題が2つ見つかった。


## A. 学習対象言語を変更しても問題セットがSessionに残る

例えば以下の操作を行う。

```text
学習対象言語：普通話

↓

普通話の問題セット50問でトレーニング開始

↓

10 / 50で中断

↓

問題セットと中断位置をSessionに保存

↓

ヘッダーから学習対象言語を國語へ変更
```

学習対象言語は、

```text
languageVariant = TAIWAN
```

へ変更される。

しかし、Sessionには、

```text
practiceQuestions = 普通話の問題セット
practiceCurrentPage = 9
```

が残っている。

その結果、國語へ切り替えた後の学習メニューにも、

```text
中断しているトレーニングがあります
10 / 50
[再開する]
```

と表示される。

ここで「再開する」を押すと、現在の学習対象言語は國語であるにもかかわらず、以前中断した普通話の問題が表示される。

つまり、

```text
languageVariant
    ↓
TAIWAN

practiceQuestions
    ↓
MAINLANDの問題
```

という不整合が発生している。

プログラム上のエラーにはならないものの、ユーザーから見ると現在の学習対象言語と再開される問題の言語が一致しておらず、非常に分かりにくい状態になる。

そのため、直ちに修正することにした。


## B. 解答に拼音・注音が表示されていない

問題画面を実際に完成させた段階で、中国語本文は表示できているものの、拼音・注音が存在しないことに気づいた。

これはHTMLに表示処理を書き忘れたという問題ではなく、そもそも現在のDBおよび `Question` Entityに拼音・注音を保持するための項目が存在しない。

つまり、

```text
DB設計
↓
Entity作成
↓
問題データ登録
↓
出題画面実装
```

と進んだ後になって、必要なデータそのものが不足していることに気づいた。

本来であればDBを定義した時点で気づいておくべきだった。

拼音・注音については次のチャプターで対応する。


# 7. Aの問題を解決する方法を検討

学習対象言語とSession内の問題セットが一致しなくなる問題について、2つの解決方法を検討した。


## 方法① 普通話と國語の問題セットを別々にSessionへ保存する

例えば、

```text
mainlandPracticeQuestions
mainlandPracticeCurrentPage

taiwanPracticeQuestions
taiwanPracticeCurrentPage
```

のように、普通話と國語の学習状態を別々にSessionへ保存する。

そして学習メニューを表示するときは、現在の `LanguageVariant` に対応するSessionだけを確認する。

例えば、

```text
普通話で10 / 50まで学習
↓
中断
↓
國語へ変更
```

した場合、

```text
MAINLAND
    ↓
中断データあり

TAIWAN
    ↓
中断データなし
```

という状態になる。

現在の学習対象言語がTAIWANならTAIWAN側のSessionだけを確認するため、普通話の中断データがSessionに残っていても國語の学習メニューには表示されない。

その後MAINLANDへ戻せば、以前中断した普通話の問題セットを再開できる。


### メリット

普通話と國語それぞれについて中断したトレーニングを保持できる。

```text
普通話を途中まで学習
↓
國語へ切り替えて学習
↓
普通話へ戻る
↓
以前の続きから再開
```

という使い方が可能になる。


### デメリット

管理するSession情報が増える。

今後、学習モードが増えた場合、それぞれについてMAINLAND / TAIWANの状態を管理するとSession管理が複雑になる可能性がある。


## 方法② 学習対象言語変更時にPractice Sessionを破棄する

もう1つは、

> 学習対象言語を変更した時点で、通常学習（Practice）の問題セット関連情報をすべて破棄する

という方法である。

```text
普通話で学習
↓
中断
↓
國語へ変更
↓
普通話の中断データを削除
```

実装としては、

```java
session.removeAttribute("practiceQuestions");
session.removeAttribute("practiceCurrentPage");
```

によって、通常学習のSession情報を削除する。


# 8. 今回は方法②を採用

今回は方法②を採用した。


## 理由① Session管理を複雑にしたくない

方法①なら普通話と國語それぞれの学習状態を保持できる。

しかし、そのためにはLanguageVariantごとにSession属性を増やす必要がある。

今後アプリケーションの機能が増えた場合、

```text
学習モード
    ×
LanguageVariant
    ×
学習状態
```

という組み合わせを管理することになる可能性がある。

現時点で必要性の低い機能のためにSession管理を複雑にする必要はないと判断した。


## 理由② 普通話と國語を同時進行する利用方法を想定していない

例えば、

```text
普通話を10問
↓
國語へ変更
↓
國語を10問
↓
普通話へ変更
↓
普通話の続き
```

というように、普通話と國語を頻繁に切り替えながら並行して学習する利用方法は中心的な使い方として想定していない。

基本的には学習者が自分の学習対象言語を選択し、その言語を継続して学習することを想定している。

そのため、

```text
LanguageVariantを変更する
        ↓
現在の通常学習を終了する
        ↓
別の学習対象へ切り替える
```

という仕様のほうが単純で分かりやすいと判断した。


# 9. 学習メニューでの言語変更後の遷移先も改善

Sessionの問題を修正するのと同時に、学習メニュー上でLanguageVariantを変更した場合の画面遷移も見直した。


## 改善前

以前は、

```text
通常学習メニュー
↓
ヘッダーから普通話 → 國語
↓
Home
↓
もう一度通常学習メニューを開く
```

という流れになっていた。

学習メニュー上で学習する言語を変更しただけなのに、一度Homeへ戻されてしまう。

ユーザーからすると、目的の画面にすでにいたにもかかわらず別の画面へ移動させられ、再び学習メニューへ入り直す必要がある。

これは少したらい回しにされているように感じる。


## 改善後

以下の仕様に変更した。

```text
学習メニューで学習対象言語を変更
        ↓
学習メニューに残る

それ以外のページで学習対象言語を変更
        ↓
Homeへ移動
```

学習メニューでは、Controllerから言語変更後の戻り先をModelへ渡す。

```java
model.addAttribute(
        "languageVariantRedirect",
        "/practice/menu"
);
```

headerでは、この値をLanguageVariant変更時のリクエストへ含める。

```html
th:href="@{/language-variant(
    languageVariant='TAIWAN',
    redirect=${languageVariantRedirect}
)}"
```

`LanguageVariantController` では `redirect` を受け取り、

```java
@RequestParam(required = false) String redirect
```

学習メニューから呼ばれた場合のみ、

```java
if ("/practice/menu".equals(redirect)) {
    return "redirect:/practice/menu";
}
```

として学習メニューへ戻す。

それ以外の場合はHomeへ戻す。

これによって、共通ヘッダーの言語変更機能を使用しながら、ユーザーが操作した画面に応じて自然な遷移先を選択できるようになった。


# 10. 高機能にすることが必ずしも最適ではない

今回検討した方法①は、方法②より機能としては高機能である。

普通話と國語の中断状態をそれぞれ保持できるため、できることは多い。

しかし、その機能を実現するためにはSession管理が複雑になる。

今回想定している利用方法では、普通話と國語のトレーニングを同時進行できることのメリットはそれほど大きくない。

そのため、

```text
高機能だが複雑な方法①
```

ではなく、

```text
機能は限定されるが単純な方法②
```

を選択した。

設計では単純に機能を増やすのではなく、

```text
その機能をどの程度利用するのか

      と

その機能を追加することで
どの程度管理が複雑になるのか
```

のバランスを考える必要がある。


# 11. 実際の操作フローから設計上の問題を発見できる

今回のSession不整合は、各機能を個別に確認しているだけでは発見しにくかった。

例えば、

```text
問題セットを開始できる
中断できる
再開できる
LanguageVariantを変更できる
```

という機能をそれぞれ単独で確認すれば、すべて正常に動作している。

しかし、

```text
普通話で開始
↓
途中で中断
↓
國語へ変更
↓
中断した問題を再開
```

という複数機能をまたいだ操作を行うことで、初めて不整合が発覚した。

同様に、

```text
学習メニュー
↓
言語変更
↓
Homeへ戻される
↓
学習メニューへ入り直す
```

という動作も、プログラムとしては正常だが、実際に操作すると不自然だった。

そのためUIを伴う機能では、

> 各機能が単独で正常に動作すること

だけでなく、

> **ユーザーが実際に行いそうな操作を一連の流れとして確認すること**

が重要である。


# 12. DB設計後も実際の画面まで確認する必要がある

拼音・注音をDB定義の段階で入れ忘れていたことも、今回の反省点である。

DB設計時には、

```text
question_id
language_variant
japanese_text
chinese_text
alternative_answer
condition
difficulty
allow_ai_variation
template
subject_type
verb_variation
```

という項目を定義した。

しかし実際の出題画面を作ったところ、

```text
日本語
↓
中国語
↓
拼音 / 注音
```

という学習画面に必要な情報のうち、拼音・注音が存在しないことに気づいた。

データベースを設計するときはテーブルだけを見るのではなく、

> **そのデータが最終的にどの画面で、どのように使われるのか**

まで想定して確認する必要がある。

今回であれば、Questionテーブルを作成した段階で出題画面に必要な情報を一覧化していれば、拼音・注音の不足にもっと早く気づくことができた。


# 13. 今回特に学んだこと

今回の実装では、通常学習の基本的な処理以上に、実装後の動作確認から得た設計上の学びが大きかった。

特に重要だったのは以下。

- 学習対象言語とSession内の問題セットの整合性を保つ必要がある
- 機能を高機能にすることより、利用方法に合った単純な設計を選ぶほうが適切な場合がある
- 共通ヘッダーからの操作でも、現在いる画面に応じて自然な遷移先を考える
- 各機能を単独で確認するだけでなく、複数機能をまたいだ実際のユーザー操作で動作確認する
- DB設計時には、最終的な画面で必要になるデータまで確認する

---

# 追記：Session未設定時のデフォルト値の扱い（2026年8月16日）

## 発生した問題

Spring Bootを再起動して通常学習メニュー（`/practice/menu`）へアクセスすると、初級・中級・上級の問題数がすべて0問になった。

![](../../images/0003-09.png)

一度学習対象言語を切り替えると、正常に問題数が表示された。

## 原因

学習対象言語は `languageVariant` としてSessionに保存しているが、新しいSessionではまだ値が設定されていない。

そのため、学習対象言語を一度も切り替えていない状態では、

```java
session.getAttribute("languageVariant")
```

の結果は、

```text
null
```

となる。

一方、画面上では `languageVariant == null` の場合も普通話として表示していたため、

```text
画面上
languageVariant == null
    ↓
普通話として表示

問題数取得
languageVariant == null
    ↓
nullのまま検索条件として使用
    ↓
0件
```

という食い違いが発生していた。

## 修正

`PracticeController#getPracticeMenu()` でSessionから学習対象言語を取得し、未設定の場合は `MAINLAND` として扱うようにした。

```java
// 学習対象言語を取得
LanguageVariant languageVariant =
        (LanguageVariant) session.getAttribute("languageVariant");

// 未設定の場合は普通話
if (languageVariant == null) {
    languageVariant = LanguageVariant.MAINLAND;
}

// 通常問題数を取得
PracticeMenuDto menu =
        practiceService.countPracticeQuestions(languageVariant);

model.addAttribute("practiceMenu", menu);
```

これにより、学習対象言語を一度も変更していない状態でも、普通話の問題数を正常に取得できるようになった。

## 学んだこと

Sessionに保存する設定値には、ユーザーがまだ一度も設定を変更していない状態が存在する。

そのため、

```text
Sessionに値が存在しない
```

ことと、

```text
アプリケーション上のデフォルト設定
```

は分けて考える必要がある。

今回の場合は、

```text
languageVariant == null
        ↓
LanguageVariant.MAINLAND
```

として扱うことで、Sessionが未設定でも普通話をデフォルトとして処理できる。

また、画面上でデフォルト値として表示しているだけでは、Sessionにその値が保存されていることにはならない。

```text
画面上で「普通話」と表示されている
≠
SessionにMAINLANDが保存されている
```

この違いには注意が必要。

現時点では普通話（`MAINLAND`）をデフォルトとする。

今後ログインユーザーごとにデフォルトの学習対象言語を設定できるようにする場合は、ユーザー設定を優先して初期値を決定する仕組みを検討する。

---

# 追記：structureとconditionの役割を分離する（2026年8月23日）

問題データの分類方法を見直し、`Question` に新しく `structure` を追加した。

これまで `Question` には `condition` が存在していたが、`condition` と問題そのものの文法・構造は必ずしも一致しない。

## structureとconditionの違い

`structure` は、模範解答となる中国語文を客観的に分析したとき、その文の根幹となっている文法・構造を表す。

例えば、

```text
他笑着跟我说话。
→ structure：動態助詞（着）

这么多菜，我们吃不完。
→ structure：可能補語
```

のように分類する。

一方、`condition` は文そのものを分類するための項目ではない。

`condition` は、

> この問題では、この文法・表現を使って解答してほしい

という出題者側の意図によって設定する条件である。

そのため、

```text
structure
→ 模範解答となる中国語文を客観的に分類したもの
→ 全問題に必ず存在する

condition
→ 出題者がその問題に設定する解答上の条件
→ 必要な問題にだけ存在する
```

という違いがある。

## conditionは文法分類には適さない

例えば、

```text
雨が降っても、私は予定どおり出発します。

即使下雨，我也会按计划出发。
```

という問題では、模範解答は譲歩関係を持つため、

```text
structure = 譲歩複文
```

と分類できる。

しかし、この問題で必ず `即使～也～` を使用させる必要があるとは限らない。

```text
就算下雨，我还是会……
哪怕下雨，我也会……
```

など、意味を適切に表す別の表現を認めるのであれば、

```text
condition = NULL
```

でよい。

つまり、

```text
模範解答にある文法が使われている
＝
その文法をconditionとして指定する
```

ではない。

この違いから、`condition` を問題検索や復習時の文法分類として利用すると、出題者が条件を設定した問題しか分類できず、検索条件として扱いにくいことが分かった。

そこで、問題そのものを分類する `structure` を別に持たせることにした。

## conditionが必要になるケース

一方、特定の文法を使うこと自体が問題の目的である場合は `condition` を設定する。

例えば、

```text
携帯電話を家に忘れました。

我把手机忘在家里了。
```

という問題を「把構文を使って表現する練習」として出題する場合は、

```text
structure = 把構文
condition = 「把」構文を使う
```

となる。

また、

```text
彼に何度電話しても、ずっとつながりません。

我给他打了好几次电话，一直都打不通。
```

を可能補語のアウトプット練習として出題する場合は、

```text
structure = 可能補語
condition = 可能補語を使う
```

となる。

この場合、`structure` と `condition` の内容は似ているが意味は異なる。

```text
structure = 可能補語
→ 模範解答を分析した結果

condition = 可能補語を使う
→ この問題で学習者に課す条件
```

として区別する。

## 実際にはconditionが必要な問題はかなり少なかった

`structure` と `condition` の役割を整理した後、既存のサンプル問題60問について、改めて `condition` が本当に必要かを1問ずつ確認した。

当初は、比較構文、譲歩複文、逆接複文など、特徴的な文法を使用している問題には広く `condition` を設定することも考えられた。

例えば、

```text
雨が降っても、私は予定どおり出発します。

即使下雨，我也会按计划出发。
```

であれば、

```text
condition = 「即使～也～」を使う
```

と設定することもできる。

しかし、この日本語を自然な中国語に訳すことが問題の目的であれば、

```text
就算下雨，我还是会……
哪怕下雨，我也会……
```

などの表現を排除する必要はない。

模範解答が `即使～也～` を使用しているという理由だけで、それを解答条件にする必要はないことが分かった。

同じ基準で既存の60問を確認すると、`condition` が本当に必要だと判断できる問題はごく少数だった。

代表的なのは、

```text
我把手机忘在家里了。
→ condition：「把」構文を使う

我必须在今天之内把这份材料交上去。
→ condition：「把」構文を使う

我给他打了好几次电话，一直都打不通。
→ condition：可能補語を使う
```

など、**特定の文法を使用して解答すること自体を問題の目的とするケース**である。

最終的には、60問のサンプルデータのうち `condition` を設定する必要があると判断したのは5～6問程度で、大部分の問題は、

```text
condition = NULL
```

で問題ないという結果になった。

これは `condition` の利用頻度が低すぎるという問題ではなく、むしろ `condition` の役割を適切に限定した結果と考えられる。

以前は、

```text
この問題は比較構文である
この問題は譲歩複文である
この問題は因果複文である
```

といった「問題の文法分類」と、

```text
この問題ではこの文法を使って解答する
```

という「出題上の制約」が混同されやすかった。

`structure` を追加したことで、

```text
structure
→ 全問題に存在する
→ 問題そのものを文法・構造によって分類する

condition
→ 一部の問題にのみ存在する
→ 特定の文法・表現を使用させる必要がある場合だけ設定する
```

と役割を明確に分けられた。

実際の60問を分類した結果、ほとんどの問題で `structure` は必要だった一方、`condition` は不要だったことからも、両者を別フィールドとして扱う設計が適切だったことを確認できた。

また、NULLを許可するフィールドについて、

> 値を入れられるから入れる

のではなく、

> その値が本当にそのレコードに必要なのか

を判断することも重要だと分かった。

`condition` はNULLであること自体に意味があり、

```text
condition = NULL
→ 特定の文法・表現を強制せず、自然な解答を認める問題
```

と解釈できる。

## structureはEnumではなくStringで保持する

`structure` は全問題に設定する必須項目とするが、JavaではEnumにしないことにした。

例えば現時点でも、

```text
平叙文
形容詞述語文
「是」判断文
疑問文（疑問詞）
把構文
比較構文
強調構文
時量表現
可能補語
条件複文
譲歩複文
逆接複文
時間複文
比例複文
並列複文
累加複文
因果複文
```

など複数の分類が存在する。

さらに今後問題を追加すると、

```text
動態助詞（着）
結果補語
方向補語
程度補語
被構文
「是…的」構文
```

など、現在の問題データには存在しない `structure` が追加される可能性がある。

`Difficulty` のように取り得る値が最初から固定されている項目とは性質が異なるため、`structure` をEnumにすると、新しい分類を追加するたびにJavaコードの変更が必要になる。

そのため、

```java
@Column(name = "structure", nullable = false)
private String structure;
```

として、文字列として管理することにした。

## 文法分類では分類の粒度も重要

既存問題へ実際に `structure` を設定する過程で、文法的に正しい分類をそのまま使用すればよいわけではないことにも気づいた。

例えば、

```text
我坐地铁去公司。
他骑自行车去学校了。
周末我和朋友去看电影。
```

などは文法的には連動文として分析できる。

しかし、本システムの `structure` は文法研究のための分類ではなく、学習者が問題を検索・選択するための分類でもある。

そのため、このような一般的な文について細かく「連動文」と分類するより、

```text
平叙文
```

として扱う方が、検索カテゴリとして理解しやすいと判断した。

同様に介詞句についても、

```text
介詞句（跟）
介詞句（在）
介詞句（随着）
```

のように使用されている介詞そのもので分類するのではなく、

```text
介詞句（場所・時間）
介詞句（対象・相手）
介詞句（方向・経路）
介詞句（原因・理由）
介詞句（手段・方法）
介詞句（変化・推移）
```

のように、その介詞句が文中で果たす意味・機能によって分類する方針とした。

これにより、異なる介詞を使用していても同じ意味機能を持つ問題を同じカテゴリから検索できる。

## 今回学んだこと

`structure` と `condition` を分離したことで、データ項目は「どちらも文法に関係する」という表面的な共通点だけで設計してはいけないことが分かった。

重要なのは、

```text
その値は誰が決めるのか
その値は何を表しているのか
その値はどの機能で利用するのか
```

を区別することである。

今回の場合、

```text
structure
→ 中国語文そのものから決まる客観的な分類
→ 問題検索・復習条件などに利用する

condition
→ 出題者の意図によって決まる主観的な条件
→ 問題を解く際の制約・ヒントとして利用する
```

となる。

似た値が入る可能性があっても、役割が異なるのであれば別のデータとして保持する方が、後から検索機能や出題機能を拡張しやすい。



---

# 14. 次回

次のチャプターでは、今回の動作確認で判明した、

> **解答に拼音・注音が表示されない**

問題に対応する。

現在はDBおよび `Question` Entityに拼音・注音を保持するための項目自体が存在しないため、まずデータ構造を見直した上で、問題出題画面への表示まで実装する。