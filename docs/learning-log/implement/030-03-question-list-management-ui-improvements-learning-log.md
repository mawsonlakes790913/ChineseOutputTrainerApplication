# 030-03 問題リスト機能⑥｜問題一覧からのリスト操作とUI改善 - 学習ログ

## はじめに

030-01では通常学習・AI生成学習へのリスト機能の統合、030-02では問題一覧の検索条件にリストを追加する際のSQL設計を整理した。

本章では、**問題一覧から問題のリスト登録状態を操作するための既存APIの再利用**と、JavaScriptによる非同期更新、画面ごとのUI更新方法、共通モーダルや表の表示改善について振り返る。元の030学習ログの第12〜16・20〜22項を扱い、コード例と具体例は維持する。

---

# 1. 同じバックエンドAPIは別画面から再利用できる

問題一覧からリスト登録状態を変更する機能では、新しいバックエンド処理をほとんど作らなかった。

すでにquestion画面用に、

```text
GET /user/question-list/selection
```

で現在の登録状態を取得し、

```text
POST /user/question-list/item/update
```

で登録状態を更新し、

```text
POST /user/question-list/create-modal
```

で新しいリストを作成できるようになっていた。

問題一覧画面でも必要な操作は同じだったため、このAPIをそのまま利用した。

つまり、

```text
practice/question
review/question
ai-practice/question
user/question/list
```

という異なる画面から、同じバックエンド処理を利用している。

これは、

```text
画面ごとにControllerを作る
```

のではなく、

```text
操作そのものをAPIとして考える
```

ことで再利用できた例だった。

---

---

# 2. UIが違っても、データ操作が同じなら処理を共通化できる

question画面ではリスト更新後、

```text
モーダルを閉じる
↓
そのまま問題を解き続ける
```

という動作だった。

一方、問題一覧では、

```javascript
location.reload();
```

を利用した。

これはリスト検索中に問題をそのリストから削除した場合、

```text
現在表示中の問題
↓
リストから外す
↓
現在の検索条件では対象外になる
```

ためである。

そこで保存後に現在のURLを再読み込みすれば、

```text
現在の検索条件を維持
↓
再検索
↓
対象外になった問題が消える
```

という自然な動作になる。

バックエンドAPIは同じでも、**API実行後にUIをどう更新するかは画面の目的に合わせて変えてよい**ということが分かった。

---

---

# 3. JavaScriptでは画面によって存在しない要素を考慮する必要がある

通常学習メニューでは、ログインユーザーだけリスト選択UIを操作できる。

そのためJavaScriptで、

```javascript
const practiceQuestionList =
    document.getElementById(
        "practiceQuestionList"
    );
```

としても、画面状態によっては対象要素が存在しない。

そのまま、

```javascript
practiceQuestionList.addEventListener(...)
```

とすると、

```text
null.addEventListener(...)
```

となりJavaScriptエラーになる。

そこで、

```javascript
if (practiceQuestionList) {

    practiceQuestionList.addEventListener(
        "change",
        ...
    );
}
```

とした。

ThymeleafやSpring Securityによって、

```html
sec:authorize
th:if
```

などでHTML要素そのものが出たり消えたりする場合、JavaScript側も、

```text
この要素は必ず存在する
```

と考えてはいけない。

---

---

# 4. URLSearchParamsを使うとform形式のPOSTデータをJavaScriptから組み立てられる

リスト登録状態の更新では、

```javascript
const params =
    new URLSearchParams();

params.append(
    "questionId",
    questionListQuestionId
);
```

として送信データを作った。

さらに複数のリストIDは、

```javascript
selectedListIds.forEach(
    function (listId) {

        params.append(
            "selectedListIds",
            listId
        );
    }
);
```

とする。

例えば、

```text
questionId = 100
selectedListIds = 1
selectedListIds = 3
selectedListIds = 5
```

という形式になる。

これを、

```javascript
fetch("/user/question-list/item/update", {

    method: "POST",

    headers: {
        "Content-Type":
            "application/x-www-form-urlencoded",
        [csrfHeader]:
            csrfToken
    },

    body:
        params.toString()
});
```

と送れば、Spring側では通常のフォーム送信と同じように受け取れる。

特に、

```text
同じパラメータ名を複数回append
↓
List<Long> selectedListIds
```

として扱える点が、チェックボックスの複数選択と相性がよい。

---

---

# 5. 非同期処理後に「イベントを擬似的に発生させる」より、処理を関数として切り出した方が分かりやすい

リスト操作モーダルでは、

```javascript
loadQuestionListSelection();
```

としてリスト一覧取得を関数化している。

これによって、

```text
モーダルを開いたとき
↓
loadQuestionListSelection()

新しいリストを作成したとき
↓
loadQuestionListSelection()
```

と同じ処理を再利用できる。

もし、

```javascript
questionListButton.click();
```

のようにクリックイベントを人工的に発生させて再取得しようとすると、

```text
本当にボタンを押したいのか
データを再取得したいだけなのか
```

が分かりにくくなる。

再利用したい処理そのものを、

```javascript
function loadQuestionListSelection() {
    ...
}
```

として分離しておけば、

```text
イベント
↓
必要な処理を呼ぶ
```

という構造になる。

---

---

# 6. 機能追加ではバックエンドだけでなく、UI上の情報量も調整する必要がある

問題一覧へリスト操作列を追加した結果、テーブルの列数が増えた。

そこで最終的に、

```text
別解列
```

を削除した。

別解そのものを削除したわけではなく、

```text
一覧
→ 主要情報だけ

詳細モーダル
→ 別解を含む詳細情報
```

と役割を分けた。

さらに、

```text
日本語 22% → 27%
中国語 22% → 27%
生成元 → 6%
```

と列幅も調整した。

機能を追加し続けると、

```text
表示できる情報は全部表示する
```

という方向になりやすい。

しかし、情報を増やした結果一覧性が落ちるのであれば、

```text
一覧に必要な情報
詳細画面にあればよい情報
```

を分ける必要がある。

---

---

# 7. disabledの操作を表示することで「機能がない」と「操作できない」を区別できる

以前の削除列では、

```text
AI生成問題
→ 削除ボタン表示

APP問題
→ 削除ボタン非表示
```

だった。

しかしこれでは、APP問題を見たユーザーからすると、

```text
なぜここには削除ボタンがないのか
```

が分かりにくい。

そこで、

```html
th:disabled="${!question.aiGenerated}"
```

を使い、

```text
AI
→ 削除可能

APP
→ 削除ボタンは存在するがdisabled
```

へ変更した。

さらにTooltipで、

```text
あなたが保存したAI生成由来の問題のみ削除できます
```

と説明する。

これによって、

```text
削除機能そのものが存在しない
```

のではなく、

```text
この問題は削除対象ではない
```

ことをUIから判断できるようになった。

---

---

# 8. 共通UIは専用CSSへ分離すると複数画面で同じ見た目を維持しやすい

リスト操作モーダルは、

```text
通常学習
復習
AI生成学習
問題一覧
```

など複数画面で利用する。

そこでチェックボックスの見た目を、

```css
#questionListSelectionArea .form-check-input {
    border: 2px solid #777;
}
```

として、

```text
question-list-modal.css
```

へ分離した。

画面ごとのCSSへ同じスタイルを書くと、

```text
practiceでは変更済み
reviewでは古いまま
```

のような差が生まれやすい。

同じ役割のUIを複数画面で使う場合、

```text
そのUI専用CSS
```

としてまとめることで変更箇所を1つにできる。

---

---

# まとめ

今回の学習で重要だったのは、**データ操作を共通化することと、画面ごとの表示方法まで同一にすることは別**だという点である。リストの取得・登録・解除・作成は既存APIを再利用しつつ、問題一覧では保存後に再読み込みして検索条件に合わなくなった問題を表示から外す構成とした。

JavaScriptでは、ThymeleafやSpring Securityの条件によって要素が存在しない場合への対応、`URLSearchParams`を用いた複数IDの送信、非同期処理を再利用可能な関数に分ける考え方を整理した。

また、問題一覧に機能を追加する際は情報を増やすだけでなく、別解列を詳細画面に移すなど、**一覧性を維持するために表示内容を減らす判断**も必要だった。操作不可のボタンを`disabled`で示すことや、共通モーダルのCSSをまとめることも、複数画面で一貫したUIを提供するための工夫となった。
