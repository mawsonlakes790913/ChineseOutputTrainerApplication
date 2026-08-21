# 008 通常学習の実装 その3 学習ログ

## 1. 今回の実装

今回はログインユーザー向けの通常学習機能として、以下を実装した。

- 理解度の保存
- お気に入り登録・解除
- 未学習問題のみを出題する機能

未学習問題は `study_history` に学習履歴が存在しない問題として判定するため、先に理解度保存機能を実装し、その後に未学習問題の出題機能を実装した。

---

## 2. 理解度とお気に入りの役割

今回、理解度とお気に入りという2種類のユーザーごとの情報を保存できるようにしたが、この2つは用途が異なる。

理解度は、その問題をどの程度できたかを記録し、後の復習に利用するためのもの。

自分の場合はおおよそ、

- **Easy**：ほぼ無意識に答えられ、文法的にも問題ない
- **Good**：答えは合っているが、少し考える時間が必要だった
- **Hard**：文法や文章の根幹を間違えた、または答えるまでかなり時間がかかった

という基準で使っている。

ただし、この基準はアプリ側で固定するものではなく、学習者自身が決めればよい。

一方、お気に入りは理解度とは関係なく、

- 負荷がちょうどいい問題
- 表現が気に入った問題
- 何度も見返したい問題

などを自由に残すための機能である。

つまり、**理解度は「どこまで学習し、何ができて何ができないか」を管理するもの、お気に入りは「理由を問わず何度も見たい問題」を残すもの**として使い分ける。

---

## 3. 理解度保存機能

問題ごとに `HARD` / `GOOD` / `EASY` を保存できるようにした。

ユーザーと問題の組み合わせで学習履歴を管理するため、`study_history` では `user_id` と `question_id` を複合主キーとしている。

### 複合主キーについて

今回改めて整理したのが、

- `@EmbeddedId`
- `@Embeddable`
- `Serializable`

の役割。

`@Embeddable` を付けた `StudyHistoryKey` に `userId` と `questionId` をまとめ、それを `StudyHistory` の `@EmbeddedId` として使用する。

また、JPAで複合主キーとして使用するクラスは `Serializable` を実装する必要がある。

個別には以前にも使用したことがあるが、今回のような「ユーザー × 問題」という中間的な情報を扱うテーブルでは複合主キーを使う場面が多いため、役割を改めて整理できた。 :contentReference[oaicite:4]{index=4}

### INSERTとUPDATEに独自クエリは不要

理解度保存では、

- まだ履歴がない → INSERT
- すでに履歴がある → UPDATE

という2つのDB操作が必要になる。

最初に必要なSQLを考えるとSELECT・INSERT・UPDATEの3種類が必要に見えるが、INSERTとUPDATEについては `JpaRepository` の `save()` が利用できる。

そのため独自に必要なのは、現在の `user_id + question_id` の組み合わせが存在するか確認する処理だけでよかった。

JPAを使う場合、必要なSQLをそのまま全部Repositoryへ書くのではなく、**JpaRepositoryが標準で提供している処理で代用できないか先に確認する**ことも重要だと感じた。 

### 同じ理解度による上書きは許可する

例えば1月1日に `HARD` と記録した問題に対して、2月1日に再び `HARD` を選択すると、理解度は同じ `HARD` のまま更新日時だけが新しくなる。

同じ理解度ならUPDATEしない仕様にすることもできるが、今回はその判定を実装しなかった。

このアプリでは理解度の変更履歴をすべて残すのではなく、**現在の理解度だけを保持する**。

そのため同じ理解度で上書きされてもデータ上の矛盾はなく、わざわざ判定処理を増やすメリットも小さい。

必要性の低い条件分岐を増やすより、理解度が選択されるたびに同じ保存処理を行う単純な仕様にした。 :contentReference[oaicite:6]{index=6}

---

## 4. お気に入り登録機能

問題画面にハートアイコンを追加し、お気に入りの登録・解除をできるようにした。

お気に入りもユーザーと問題の組み合わせなので、`favorite` では `user_id + question_id` を複合主キーとして管理する。

### `@ManyToOne` はどちらを基準に考えるのか

今回特に理解を整理する必要があったのが、`Favorite` Entityに付けた以下のアノテーション。

- `@ManyToOne`
- `@MapsId`
- `@JoinColumn`

アノテーションごとの説明だけを覚えると、指定する値も似ているため曖昧になりやすい。

特に `@ManyToOne` は、**どちらのEntityを基準に「Many」「One」と言っているのか**を先に理解する必要がある。

例えば1人のユーザーが100問をお気に入り登録している場合、

```text
Favorite(user=1, question=1)   ─┐
Favorite(user=1, question=2)   ─┤
Favorite(user=1, question=3)   ─┤
...                             ├──→ Users(id=1)
Favorite(user=1, question=100) ─┘
        ↑                              ↑
      Many                            One
```

となる。

したがって、

```text
Favorite → Users = ManyToOne
```

である。

**`@ManyToOne` は、そのアノテーションが書かれているEntityを基準に考える。**

ここを曖昧にしたまま「FavoriteとUsersはManyToOne」とだけ覚えると、逆方向から見たときに混乱する。

関連付けを理解するときは、まず「どちらからどちらを見ているのか」を明確にする必要がある。 :contentReference[oaicite:7]{index=7}

### `@MapsId` と `@JoinColumn` の役割

今回の `Favorite` では、

```text
@ManyToOne
@MapsId("userId")
@JoinColumn(name = "user_id")
private Users user;
```

という組み合わせを使用した。

それぞれを整理すると、

- `@ManyToOne`  
  → `Favorite` から `Users` を見た関連が多対1である

- `@MapsId("userId")`  
  → `user` が参照している `Users` の主キーを `FavoriteKey.userId` としても使う

- `@JoinColumn(name = "user_id")`  
  → その関連に使用する `favorite` テーブル側のカラムが `user_id`

となる。

つまり、Favorite Entityに `FavoriteKey` と `Users user` の両方があっても、DBに同じIDを二重保存しているわけではない。

`@MapsId` によって、関連Entityの主キーと複合主キーの値を同じものとして対応付けている。

このあたりはアノテーション単体の意味を覚えるより、**実際にどのJavaフィールドとどのDBカラムを結び付けているのかを追った方が理解しやすかった。** 

### IDだけ入れたQuestionを`new`するのは不自然だった

お気に入りINSERT処理を最初に書いたときは、以下のように実装していた。

```java
// INSERT
Question question = new Question();
question.setQuestionId(questionId);

Favorite favorite = new Favorite();
favorite.setFavoriteKey(key);
favorite.setUser(user);
favorite.setQuestion(question);

favoriteRepository.save(favorite);
```

つまり、

```text
Questionをnewする
↓
questionIdだけsetする
↓
Favorite.questionへ渡す
```

という実装である。

しかし、今回は新しいQuestionを作成したいわけではなく、**すでにDBに存在しているQuestionをFavoriteから参照したいだけ**である。

それにもかかわらず `new Question()` すると、

- 既存のQuestionを参照したいだけなのに新しいインスタンスを作っている
- `questionId` 以外がほぼ空のQuestionインスタンスになる
- QuestionにはNOT NULLのフィールドも多く、実際の既存Entityを表すオブジェクトとして不自然

という問題がある。

そこで、`QuestionRepository#getReferenceById()` を使う形に変更した。

```java
// INSERT
Question question =
        questionRepository.getReferenceById(questionId);

Favorite favorite = new Favorite();
favorite.setFavoriteKey(key);
favorite.setUser(user);
favorite.setQuestion(question);

favoriteRepository.save(favorite);
```

`getReferenceById()` は、指定した主キーを持つ既存Entityへの「参照」を取得するために使用できる。

今回はQuestionの各フィールドの値を使いたいわけではなく、

**「このFavoriteはquestionIdのQuestionに紐づいている」**

という関連付けを行いたいだけなので、この方法が適している。

今回の実装から、**既存Entityとの関連付けに主キーだけが必要だからといって、Entityを`new`してIDだけ設定するのではなく、既存Entityへの参照を取得する方法を使う**という考え方を学んだ。

---

## 5. 未学習問題から出題する機能

`study_history` に対象ユーザーの履歴が存在しない問題を取得し、未学習問題だけをトレーニングできるようにした。

難易度も複数指定できるようにしている。

未学習問題は「存在する履歴」を探すのではなく「履歴が存在しない問題」を探すため、`question` から `study_history` を `LEFT JOIN` し、

```text
sh.question_id IS NULL
```

となる問題を取得する。

また、単純に `question_id` だけでJOINすると、他のユーザーが学習した問題まで学習済みになってしまう。

そのためJOIN条件には `user_id` も含め、**現在のユーザーにとって未学習かどうか**を判定する。

native SQLへ難易度を渡す部分では、Java側の `List<Difficulty>` を `List<String>` へ変換した。

DBでは `BEGINNER` などの文字列として保存されているため、`name()` を利用して確実にDB側の値へ合わせている。 :contentReference[oaicite:10]{index=10} 

---

## 6. 実装中に発生した問題

### `<p>` の中に `<p>` は配置できない

別解が存在しない問題でも「別解：」だけが表示される問題があった。

原因はThymeleafの `th:if` ではなく、外側と内側の両方に `<p>` を使用していたことだった。

HTMLでは `<p>` の中に `<p>` を配置できないため、ブラウザが外側の `<p>` を自動的に閉じてしまう。

その結果、コード上では `th:if` が全体を囲っているように見えても、実際のDOMでは囲えていなかった。

外側を `<div>` に変更して解決した。

Thymeleafの表示条件がおかしく見える場合でも、まずHTMLそのものが正しい構造になっているか確認する必要がある。 

### EclipseのJRE System Library

実装中に、

```text
型 java.util.function.Predicate を解決できません。
It is indirectly referenced from required type java.util.ArrayList
```

というエラーが突然発生した。

コードの問題ではなく、Eclipseの `JRE System Library [JavaSE-21]` を一度削除して追加し直すことで解決した。

Java標準APIに対して突然不可解なエラーが大量に出た場合は、コードだけでなくEclipse側のJDK参照も疑う。

### Spring Beanの登録

`SearchConditionConverter` をDIしようとした際には、Beanが見つからないというエラーが発生した。

通常のJavaクラスを作っただけではSpring Beanにはならないため、DIするクラスは `@Component` などでSpringの管理対象にする必要がある。

---

## 7. その他気づいた点

### 問題に文法・単語の解説があってもよい

現在の問題は、

```text
日本語を見る
↓
中国語で答える
↓
解答を確認する
```

という瞬間作文としての機能が中心になっている。

ただ、一部の問題については解答だけでなく、

- なぜこの文法を使うのか
- 似た表現との違い
- 単語の使い方
- 台湾と中国大陸での表現差

などの短い解説があれば、間違えたときにその場で原因まで確認でき、さらに使いやすくなりそうだと感じた。

特に `HARD` にした問題は単に答えを見直すだけでなく、「なぜ間違えたのか」を確認できると復習にもつなげやすい。

一方、AIによって毎回生成する問題については、必ずしも個別の解説まで必要ではないかもしれない。

固定問題には必要に応じて質の高い解説を用意し、AI問題はアウトプット量を増やすことを主目的にする、という役割分担も考えられる。 

### ユーザー独自の問題リスト

お気に入りとは別に、ユーザー自身が問題を分類して独自のリストを作れる機能も考えられる。

例えば、

```text
離合詞＋可能補語
```

というリストを作って、

```text
昨天晚上太熱了，我一直睡不著覺。
我今天不太舒服，吃不下飯。
```

などを登録する。

別に、

```text
政治
```

というリストを作って、

```text
即使執政黨在立法院占多數，也不能忽視在野黨的意見。
```

のような問題を登録することもできる。

お気に入りが単純な「残しておきたい問題」の集合なのに対し、この機能では**ユーザー自身が目的別・文法別・テーマ別に問題集を作れる**。

学習者によって苦手分野や学習目的は異なるため、自分専用の問題セットを作れるようになるとアプリの自由度はかなり上がる。

現在の開発範囲には含めず、基本機能が完成した後のVersion 2で実装を検討したい。 :contentReference[oaicite:14]{index=14}

---

## 8. 実装を終えて

今回の内容は以前にも一度実装したことがある機能が中心だったため、個々の処理そのものにはそこまで難しさを感じなかった。

一方で、今回はログインユーザーを前提とする機能が多く、どの処理でも「どのユーザーのデータなのか」を考える必要があった。

また、

- 既存のControllerやServiceへ追加する処理
- 新しくControllerやServiceを作る処理
- EntityやRepositoryを新設する処理
- HTMLやJavaScriptへ追加する処理

が混在していた。

そのため、コードを書くことそのものよりも、**この処理をどこに書くべきなのかを既存コードと照らし合わせながら確認すること**に時間を要した。

一度実装経験があるため大きく詰まることはなかったが、変更箇所が多い実装では、処理内容を理解していることと、正しい場所へ実装できることは別の問題だと感じた。

特に今回は `@ManyToOne` をどちら側から見るのか、`@MapsId` が何を対応付けているのか、既存Entityを関連付けるだけなら `new` ではなく `getReferenceById()` を使えることなど、以前使った技術について曖昧だった部分を改めて整理できた。

---

## 9. 次回の実装

次は以下を実装する。

- 復習モードの実装
- ユーザーメニューの実装

今回 `study_history` に理解度を保存できるようになったため、次はそのデータを利用して理解度などの条件から問題を抽出し、復習できるようにする。