# 012 ユーザーメニューの実装その1 学習ログ

今回はユーザーメニューを作成し、その中でも特に規模の大きい「問題一覧・検索機能」を実装した。

問題一覧では単純にQuestionを一覧表示するだけではなく、

- 文法・構造
- 学習状況
- 理解度
- お気に入り
- 学習対象言語
- キーワード
- ページネーション
- 詳細表示
- 理解度・お気に入りの変更

まで扱う必要があり、Repository・DTO・Service・Controller・HTML・JavaScriptをまたいだ実装となった。

今回特に学習になった点を以下にまとめる。


# 1. 複数テーブルの情報を1つの一覧画面にまとめる

今回の問題一覧では、DBに登録されている問題を表示しながら、現在のユーザーの理解度やお気に入り状態も同時に表示する必要があった。

そのため、単純に`question`テーブルだけを検索すればよいわけではなかった。

必要な情報は、

```text
question
    → 問題文、別解、難易度、発音記号など

structure
    → 文法・構造名、説明

study_history
    → 現在のユーザーの理解度

favorite
    → 現在のユーザーのお気に入り状態
```

のように複数テーブルに分かれている。

そこで、

```text
question
    ├─ JOIN structure
    ├─ LEFT JOIN study_history
    └─ LEFT JOIN favorite
```

という構成で取得することにした。

## structureはJOIN、study_historyとfavoriteはLEFT JOIN

`structure`は各問題に対応する文法・構造を取得するためにJOINする。

一方、`study_history`と`favorite`はすべての問題についてレコードが存在するわけではない。

例えば未学習問題には`study_history`が存在せず、お気に入り登録していない問題には`favorite`が存在しない。

ここでINNER JOINしてしまうと、そのような問題自体が検索結果から消えてしまう。

そのため、

```sql
LEFT JOIN study_history sh
    ON q.question_id = sh.question_id
    AND sh.user_id = :userId

LEFT JOIN favorite f
    ON q.question_id = f.question_id
    AND f.user_id = :userId
```

とした。

今回の実装を通して、LEFT JOINは単に「NULLを許容して結合する」というだけではなく、**「関連データが存在しなくても基準となるデータは残したい場合に使う」**ということを具体的に理解できた。

また、`question_id`だけで結合するのではなく`user_id`も条件に含める必要がある。

`user_id`を指定しなければ、同じ問題について他ユーザーの学習履歴やお気に入りまでJOINされ、検索結果が重複する可能性があるためである。


# 2. LEFT JOINした結果のNULLを検索条件として利用する

学習状況では、

- すべて
- 学習済み
- 未学習

を検索できるようにした。

`study_history`には理解度を保存した問題だけレコードが存在するため、

```text
sh.question_id IS NULL
    → study_historyが存在しない
    → 未学習

sh.question_id IS NOT NULL
    → study_historyが存在する
    → 学習済み
```

と判定できる。

お気に入りについても同様に、

```text
f.question_id IS NOT NULL
    → お気に入り

f.question_id IS NULL
    → お気に入りではない
```

と判定できる。

今回、**LEFT JOINによって生じるNULLそのものを状態判定に利用できる**ことを実際の検索機能で使うことができた。


# 3. Entityではなく画面専用DTOを使う

今回の検索結果は`Question`そのものではない。

一覧画面では、

```text
Questionの問題情報
+
Structureの文法情報
+
StudyHistoryの理解度
+
Favoriteのお気に入り状態
```

をまとめて扱う必要がある。

そこで`UserQuestionListDto`を作成した。

特に、

- `structureDescriptionZhCn`
- `structureDescriptionZhTw`
- `pinyin`
- `zhuyin`
- `alternativeAnswerPinyin`
- `alternativeAnswerZhuyin`

は一覧表に直接表示するものではないが、Tooltipや詳細モーダルで必要になるためDTOに含めた。

また、お気に入り状態については画面側では、

```text
true
    → 赤い塗りつぶしハート

false
    → 灰色のハート
```

と扱えばよいため、`boolean`としてDTOへ渡すことにした。

今回の実装で、DTOは単なるEntityのコピーではなく、**複数のテーブルから「その画面で必要な情報」をまとめるためにも使える**ことを再確認できた。


# 4. Pageを返す場合のcountQuery

Repositoryの戻り値は、

```java
Page<UserQuestionListDto>
```

とした。

`Page`には現在表示するデータだけでなく、

- 総件数
- 総ページ数
- 現在ページ
- 次ページの有無
- 前ページの有無

なども必要になる。

そのため、現在ページのデータを取得するメインクエリとは別に、同じ検索条件に一致するデータの総件数を取得する`countQuery`も必要になる。

```text
メインクエリ
    → 現在ページに何を表示するか

countQuery
    → 同じ検索条件のデータが全部で何件あるか
```

また、`countQuery`にもメインクエリと同じJOIN・WHERE条件が必要になる。

検索条件が異なってしまうと、

```text
実際の検索結果：50件
Pageが認識する総件数：200件
```

のような食い違いが発生し、ページネーションが正しく機能しなくなる。

一方、`countQuery`は件数だけ分かればよいため、画面表示用のSELECT項目や`ORDER BY`は不要になる。


# 5. 「何も選択しない」をどう扱うか

今回の検索フォームでは、チェックボックスを何も選択しなかった場合を、

**「検索対象なし」ではなく「その条件では絞り込まない」**

という意味にした。

例えば難易度なら、

```java
if (difficulties == null || difficulties.isEmpty()) {
    difficulties = Arrays.asList(Difficulty.values());
}
```

として、

```text
BEGINNER
INTERMEDIATE
ADVANCED
```

のすべてを検索対象とする。

理解度についても同様に、

```java
if (evaluations == null || evaluations.isEmpty()) {
    evaluations = Arrays.asList(Evaluation.values());
}
```

としてHard・Good・Easyをすべて対象にする。

一方、文法・構造はEnumではなくDB管理なので、

```java
if (structureIds == null || structureIds.isEmpty()) {
    structureIds = structureRepository.findAllStructureIds();
}
```

としてDBから全IDを取得する。

同じ「何も選択されていないなら全部」という仕様でも、

```text
Enum
    → values()

DB管理
    → Repositoryから全ID取得
```

と、データの管理方法によって実現方法が異なることが分かった。


# 6. Service側でもデフォルト値を補完する

学習状況とお気に入りはselectなので、通常のフォーム操作なら必ず値が送信される。

それでもService側では、

```java
if (studyCondition == null) {
    studyCondition = StudyCondition.ALL;
}

if (favoriteCondition == null) {
    favoriteCondition = FavoriteCondition.ALL;
}
```

としている。

これはフォーム以外からアクセスされて検索条件が渡されなかった場合でも、安全に検索できるようにするためである。

今回、**「画面から正しい値が送られてくるはず」と決めつけず、Service側でも安全な状態へ補正する**という考え方を取り入れることができた。


# 7. 表示言語のデフォルトをSessionと連動させる

表示言語については、何も選択されていなければ普通話・國語の両方を検索する仕様にはしなかった。

例えば学習対象言語が普通話なら、

```text
初回アクセス
    → 普通話のみ

普通話・國語とも未選択で検索
    → 普通話のみ

普通話・國語を選択
    → 両方

國語のみ選択
    → 國語のみ
```

とした。

そのためControllerで、

```java
if (languageVariants == null || languageVariants.isEmpty()) {

    LanguageVariant languageVariant =
            (LanguageVariant) session.getAttribute("languageVariant");

    if (languageVariant == null) {
        languageVariant = LanguageVariant.MAINLAND;
    }

    languageVariants = Arrays.asList(languageVariant);
}
```

としている。

単なる検索条件の初期値ではなく、**ユーザーが現在学習対象としている言語を検索画面のデフォルトにも反映する**ことで、アプリケーション全体の設定と画面の挙動を一致させた。


# 8. ページネーションのアルゴリズム

ページ数が増えたときに、

```text
1 2 3 4 5 6 7 8 9 10
```

とすべて表示するのではなく、

```text
1 ... 3 4 [5] 6 7 ... 10
```

のようなページネーションを実装した。

## 想定したケース

```text
ケースA
1 ... 3 4 [5] 6 7 ... 10

ケースB
1 [2] 3 4 5 ... 10

または

1 ... 6 7 8 [9] 10

ケースC
[1] 2 3 4 5 ... 10

または

1 ... 6 7 8 9 [10]
```

## 現在ページの前後2ページを求める

最初は、

```java
int displayStartPage = currentPage - 2;
int displayEndPage = currentPage + 2;
```

と考えた。

しかし現在ページが先頭や末尾に近い場合には範囲外のページ番号が発生する。

そこで、

```java
int displayStartPage =
        Math.max(startPage, currentPage - 2);

int displayEndPage =
        Math.min(endPage, currentPage + 2);
```

とした。

## 先頭・末尾付近では不足分を反対側へ広げる

これだけでは、

```text
[1] 2 3 ... 10
1 [2] 3 4 ... 10
```

のように表示ページ数が少なくなる。

そこで、現在ページが先頭側に寄った分だけ右側へ、末尾側に寄った分だけ左側へ表示範囲を広げる。

```java
int shortage = 0;

if (displayStartPage == startPage) {

    shortage = 4 - (displayEndPage - displayStartPage);

    displayEndPage =
            Math.min(
                    endPage,
                    displayEndPage + shortage);

} else if (displayEndPage == endPage) {

    shortage = 4 - (displayEndPage - displayStartPage);

    displayStartPage =
            Math.max(
                    startPage,
                    displayStartPage - shortage);
}
```

これによって、

```text
[1] 2 3 4 5 ... 10
1 [2] 3 4 5 ... 10
```

のように最大5ページ分を表示できる。

## 「…」を表示する条件

1ページしか省略しない場合には「…」を表示しないことにした。

そのため、

```java
boolean showFirstEllipsis =
        displayStartPage >= 3;

boolean showLastEllipsis =
        displayEndPage <= endPage - 3;
```

とした。

このアルゴリズムは今回のAppの中でも特に考えた箇所だった。

単にコードを書き始めるのではなく、

```text
中央にいる場合
先頭付近の場合
末尾付近の場合
```

とケースを先に分けてから必要な変数と条件を考えることで実装できた。

Java Silverで学んだ`Math.max()`、`Math.min()`、条件分岐などの基本的な知識を、実際のUIロジックに応用できた。


# 9. 現在の表示件数をPageから計算する

ページネーションだけでなく、

```text
1-50 / 101件
51-100 / 101件
101-101 / 101件
```

のように現在の表示位置も出すことにした。

```java
long start =
        questionList.getNumber()
        * questionList.getSize() + 1;

long end =
        start
        + questionList.getNumberOfElements() - 1;
```

ここでは、

```text
getNumber()
    → 現在ページ（0始まり）

getSize()
    → 1ページの最大件数

getNumberOfElements()
    → 現在ページに実際に存在する件数

getTotalElements()
    → 検索結果全体の件数
```

を利用している。

特に最後のページでは必ず50件あるとは限らないため、終了位置の計算には`getSize()`ではなく`getNumberOfElements()`を利用する必要がある。


# 10. 一覧性と情報量を両立させる

問題一覧では情報を増やせば便利になるが、すべてをテーブルへ直接表示すると画面が大きくなりすぎる。

そこで情報によって表示方法を分けた。

## 長い別解は省略する

別解については、

```html
<td th:text="${question.alternativeAnswer != null
    ? (#strings.length(question.alternativeAnswer) > 20
        ? #strings.substring(question.alternativeAnswer, 0, 20) + '…'
        : question.alternativeAnswer)
    : ''}">
</td>
```

とした。

処理としては、

```text
別解なし
    → 空欄

別解あり・20文字以下
    → そのまま表示

別解あり・20文字超
    → 先頭20文字 + …
```

となる。

これによって、長い別解によって一覧表の幅が必要以上に広がることを防いだ。

## 文法説明はTooltipで表示する

文法・構造については名前だけ一覧に表示し、詳しい説明はTooltipで確認できるようにした。

さらに学習対象言語によって、

```text
普通話
    → 簡体字の説明

國語
    → 繁体字の説明
```

と表示を切り替えるようにした。

## 詳細情報はモーダルで表示する

拼音・注音符号・別解の発音などは一覧表へ直接表示せず、詳細モーダルへまとめた。

詳細ボタンでは`th:data-*`に必要な情報を保持させ、JavaScriptから取得してモーダルへ反映する。

ここでは、

```text
detailButton
    → JavaScriptがクリックを検知するための目印

data-bs-toggle / data-bs-target
    → Bootstrapがモーダルを開く

th:data-*
    → JavaScriptへ渡す問題データ
```

という役割分担になっている。

`data-*`属性をHTMLとJavaScriptの間でデータを受け渡す手段として利用できることを学んだ。


# 11. 学習状況と理解度は独立した検索条件ではない

実装途中で、学習状況と理解度の組み合わせに矛盾があることに気づいた。

理解度は学習済み問題にしか存在しない。

したがって、

```text
学習状況：未学習
理解度：Hard
```

のような指定は意味を持たない。

そこで最終的には、

```text
すべて
    → 理解度無効

学習済み
    → 理解度有効

未学習
    → 理解度無効
```

とした。

最初は「未学習の場合だけ無効にする」という方向から考えていたが、実際には、

**理解度という条件が意味を持つのは「学習済み」の場合だけ**

と考えた方が単純だった。

そのため、

```javascript
const learned =
    studyCondition.value === "LEARNED_ONLY";
```

として、`learned`がtrueのときだけ理解度を有効化する設計に変更した。

今回、条件分岐を考えるときには「どの場合に無効なのか」だけでなく、**そもそもどの場合にその機能が意味を持つのか**という方向から考えると、より単純なロジックになる場合があると分かった。


# 12. 内部の検索条件とUI表示を一致させる

Serviceでは、理解度を何も選択しなかった場合、

```java
if (evaluations == null || evaluations.isEmpty()) {
    evaluations = Arrays.asList(Evaluation.values());
}
```

としてHard・Good・Easyすべてを検索対象にしている。

しかし画面上では何もチェックされていないままだと、

```text
画面上
    → 理解度を指定していないように見える

内部
    → Hard・Good・Easyすべてを検索対象としている
```

という食い違いが起こる。

そのため「学習済み」で理解度を何も指定しなかった場合には、検索後のUIでもHard・Good・Easyすべてをチェックした状態として表示するよう修正した。

今回、**検索処理として正しいだけでは不十分で、内部で実際に適用されている条件をUIからも理解できる必要がある**と気づいた。


# 13. 検索条件を検索後も維持する

検索フォームでは、検索ボタンを押したあとに入力内容が消えてしまうと、自分が何の条件で検索したのか分からなくなる。

そのためControllerから検索に使用した条件をModelへ戻した。

```java
model.addAttribute("selectedDifficulties", difficulties);
model.addAttribute("selectedEvaluations", evaluations);
model.addAttribute("selectedStudyCondition", studyCondition);
model.addAttribute("selectedFavoriteCondition", favoriteCondition);
model.addAttribute("selectedStructureIds", structureIds);
model.addAttribute("japaneseKeyword", japaneseKeyword);
model.addAttribute("chineseKeyword", chineseKeyword);
```

HTMLでは、

- `th:checked`
- `th:selected`
- `th:value`

を使って検索条件を復元する。

ここでEnumのListについては、

```html
th:checked="${selectedDifficulties == null
    or selectedDifficulties.![name()].contains('BEGINNER')}"
```

のように`name()`へ変換して比較する。

一方、`structureIds`は`List<Long>`なので、

```html
th:checked="${selectedStructureIds == null
    or selectedStructureIds.contains(structure.structureId)}"
```

とそのまま`contains()`で判定できる。

同じチェック状態の復元でも、**Modelから渡されるデータ型によってThymeleaf側の比較方法が変わる**ことを学んだ。


# 14. JavaScriptとBootstrapの役割を区別する

今回の画面ではTooltip、モーダル、理解度変更、お気に入り変更などJavaScriptを使う箇所が多かった。

例えば、

```html
data-bs-toggle="modal"
data-bs-target="#questionDetailModal"
```

はBootstrap側がモーダルを開くための設定である。

一方、

```html
class="detailButton"
```

は自作JavaScriptがクリックイベントを登録するための目印として利用している。

同様に、

```html
class="favoriteButton"
th:data-question-id="${question.questionId}"
```

では、

```text
favoriteButton
    → JavaScriptがボタンを取得する

data-question-id
    → どの問題を変更するかJavaScriptへ渡す
```

という役割になる。

今回、HTMLに書かれている属性が、

```text
Bootstrapが使うもの
自作JavaScriptが使うもの
Thymeleafがサーバー側で生成するもの
```

のどれなのかを意識して読む必要があると分かった。


# 15. Bootstrap JavaScriptの二重読み込みによる不具合

実装途中で、問題一覧ページを開くとヘッダーのDropdownが反応しなくなる問題が発生した。

Consoleには直接原因となるJavaScriptエラーが出ていなかったため、原因特定に苦戦した。

原因は、共通レイアウトですでにBootstrap JavaScriptを読み込んでいるにもかかわらず、問題一覧HTMLでもBootstrap JavaScriptを読み込んでいたことだった。

`user/question/list.html`は、

```html
layout:decorate="~{layout/layout}"
```

によって共通レイアウトを利用している。

そのため両方でBootstrapを読み込むと、最終的には同じJavaScriptが二重に読み込まれる。

最終的には、

```text
layout/layout.html
    → Bootstrapなどの共通ライブラリ

user/question/list.html
    → そのページ固有のCSS・JavaScript
```

と役割を分けた。

今回の経験から、JavaScriptの不具合では自分が書いたJavaScriptだけを見るのではなく、**共通レイアウトを含めた最終的なHTMLで何が読み込まれているのかを確認する必要がある**と学んだ。


# 16. 実装を終えて

今回の問題一覧・検索機能は、このAppの中でも特に負荷の大きい実装だった。

特に、

- 抽出するデータが複数テーブルのJOINを前提としていた
- 複数テーブルの情報を画面へ渡すためのDTOが必要になった
- ページネーションの見た目を整えるため、専用のServiceとDTOを作成した
- ユーザーが検索条件を何も選択しなかった場合の処理を考える必要があった
- バックエンドだけでなくHTML・JavaScript側にも多くの処理が必要になった

という点で、これまで実装した機能よりも影響範囲が広かった。

また、実装を進める中で、

- 拼音・注音も詳細画面に必要
- 別解の拼音・注音も必要
- 文法・構造の説明をTooltipに表示したい
- 普通話と國語で説明を切り替えたい
- 理解度と学習状況を連動させる必要がある
- 検索後も検索条件を維持したい

など、初期構想時より表示したいものや必要な処理が増えた。

そのたびに、

```text
Repository
    ↓
DTO
    ↓
Service
    ↓
Controller
    ↓
HTML
    ↓
JavaScript
```

の複数箇所に修正が必要となり、時間を要した。

ここは今回の反省点でもある。

画面に必要な情報を実装前にもう少し具体的に洗い出し、

```text
画面に何を表示するか
    ↓
そのためにどのデータが必要か
    ↓
どのテーブルから取得するか
    ↓
DTOに何を持たせるか
    ↓
どの操作をJavaScriptで行うか
```

まで整理しておけば、途中変更をある程度減らせたと思う。

一方で、実際に画面を作って動かすことで初めて必要性に気づく機能もあった。

そのため今後は、最初に画面とデータの流れをできるだけ整理しつつ、変更が発生すること自体は前提として、1つの変更がどこまで影響するのかを意識して実装したい。

今回特に大きかった学びは、**一覧検索画面は単なるSELECT結果の表示ではなく、DB・SQL・DTO・Service・Controller・Session・Thymeleaf・JavaScript・UI設計がすべてつながった機能である**ということである。

これまで個別に学んできたJOIN、DTO、Page、Thymeleaf、JavaScriptなどを、1つの実用的な機能として組み合わせる経験になった。


# 次にやること

ユーザーメニューその2として、プロフィール表示とユーザー情報変更機能を実装する。