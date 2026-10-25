# 029-03 問題リスト機能③｜Spring MVC・Thymeleafによる詳細画面の再設計【学習ログ】

## 概要

029-02では、問題リストの詳細を表示するために、JavaScriptの`fetch()`で問題データをJSONとして取得し、画面上のテーブルを動的に生成していた。

この方法でも問題一覧は表示できたが、スマートフォンでの表示確認を通して、横長のテーブルでは文章が細かく折り返され、読みづらくなることが分かった。そこで029-03では、**問題リストの詳細を独立したページに分け、Spring MVCとThymeleafでHTMLを描画する構成**へ変更した。

今回の学習ログでは、設計変更を通して確認した、`@ResponseBody`とビュー名の違い、Modelを使ったデータの受け渡し、ThymeleafとJavaScriptの責務分担、画面遷移とリダイレクト、レスポンシブ表示について整理する。

---

## 1. JSONを返すControllerとHTMLを返すControllerの違い

旧設計では、Controllerに`@ResponseBody`を付けて、問題リストの内容をJSONとして返していた。

```java
@GetMapping("/user/question-list/detail")
@ResponseBody
public List<QuestionListItemDto> getUserQuestionListDetail(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        Locale locale) {

    Users user = getLoginUser(loginUser);

    return questionListItemService.getQuestionListItems(
            user, listId, locale);
}
```

この場合、Controllerの戻り値はビュー名ではなくレスポンス本文として扱われる。ブラウザ側では、JavaScriptがJSONを受け取り、テーブルを組み立てる必要がある。

```text
旧設計

Controller
    ↓
@ResponseBody
    ↓
List<QuestionListItemDto>をJSONとして返す
    ↓
JavaScriptがHTMLを生成
```

新設計では`@ResponseBody`を削除し、戻り値を`String`に変更した。

```java
@GetMapping("/user/question-list/detail")
public String getUserQuestionListDetail(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        Model model,
        Locale locale,
        HttpSession session) {

    Users user = getLoginUser(loginUser);

    List<QuestionListItemDto> listItems =
            questionListItemService.getQuestionListItems(
                    user, listId, locale);

    model.addAttribute("listItems", listItems);

    PronunciationType pronunciationType =
            (PronunciationType) session.getAttribute("pronunciationType");

    if (pronunciationType == null) {
        pronunciationType = PronunciationType.PINYIN;
    }

    model.addAttribute("pronunciationType", pronunciationType);

    return "user/question-list/detail";
}
```

```text
新設計

Controller
    ↓
Modelへデータを格納
    ↓
ビュー名を返す
    ↓
ThymeleafがHTMLを描画
```

今回の変更で、**同じURLのControllerでも、`@ResponseBody`でデータを返すのか、Modelとビュー名でHTMLを返すのかによって、ブラウザ側の処理が大きく変わる**ことを確認できた。

---

## 2. 表示方法を変更してもRepository・Service・DTOは再利用できる

今回の設計変更では、問題リストに登録された問題を取得するSQLや`QuestionListItemDto`を作り直す必要はなかった。

もともと、データ取得処理は次のように分かれていた。

```text
Repository
    ↓
Service
    ↓
QuestionListItemDto
    ↓
Controller
```

旧設計では、この後にJSONを返してJavaScriptで表示していた。一方、新設計では同じDTOをModelに渡してThymeleafで表示する。

```text
                  QuestionListItemDto
                           ↓
                  QuestionListController
                      ↙          ↘
            旧：JSON応答      新：Modelへ格納
                 ↓                ↓
          JavaScript生成      Thymeleaf描画
```

つまり、**データの取得方法と、取得したデータをどのように画面へ表示するかは別の問題**である。

表示方式を変える際にRepositoryやServiceまで修正しなくて済んだことから、表示用DTOを介して層を分けておく利点を理解できた。

ただし、これは今回のデータ取得要件が変わらなかったためであり、表示項目や検索条件自体を変える場合はRepository・Service側の修正も必要になる。

---

## 3. ThymeleafとJavaScriptはどちらか一方に統一する必要はない

旧設計の`list.js`では、JSONの取得だけでなく、問題一覧テーブルの生成、お気に入り操作、問題詳細モーダル、理解度の変更、リストからの問題削除なども扱っていた。

新設計では、問題一覧のHTMLを`detail.html`に移し、**JavaScriptは画面上の操作を担当する**構成に変更した。

```text
Thymeleaf
    ├─ 問題一覧を表示
    ├─ 問題の各項目をHTMLへ埋め込む
    └─ PC用テーブル・スマートフォン用カードを描画

JavaScript（detail.js）
    ├─ 問題詳細モーダルを開く
    └─ モーダルへ必要な値を設定する
```

この分担では、一覧の初期表示のためにJavaScriptが別途JSONを取得してHTMLを組み立てる必要がない。一方、ユーザーがクリックしたときにモーダルを開くような操作には引き続きJavaScriptを使う。

今回の実装から、**「動的な画面だからすべてJavaScriptで生成する」というわけではなく、サーバー側で決められる表示内容はThymeleaf、ユーザー操作に応じた変化はJavaScriptという分担もできる**ことを確認した。

なお、これはJavaScriptでHTMLを生成する方法そのものが誤りという意味ではない。今回のアプリの構成と、一覧画面に求める表示方法に合わせた選択である。

---

## 4. 画面を分離すると不要なHTMLとJavaScriptも整理できる

問題リストの一覧と、リスト内の問題一覧を同じ`list.html`に表示していたときは、一覧管理用の要素と詳細表示用の要素が同じファイルに存在していた。

新設計では、画面の役割を次のように分けた。

```text
question-list/list.html
    ├─ リスト一覧
    ├─ 新規リスト作成
    ├─ リスト名編集
    ├─ リスト削除
    └─ 詳細ページへのリンク

question-list/detail.html
    ├─ 選択したリスト内の問題一覧
    ├─ 問題詳細モーダル
    └─ 問題の削除
```

これに伴い、旧設計のインライン詳細表示領域、旧モーダル、旧メッセージ用要素などを`list.html`から削除した。

また、`static/js/user/question-list/list.js`は旧詳細表示専用の処理だけを持っており、リストの作成・編集・削除に必要な処理は含まれていなかったため、ファイルごと削除できた。

ここで学んだのは、**新しい画面を追加するだけでは設計変更は完了しない**ということだった。使われなくなったDOM要素、イベント処理、スクリプトの読み込みを残しておくと、どの処理が現役なのか分かりにくくなる。

機能の移動後には、旧実装の依存関係を確認し、不要になったコードを整理する必要がある。

---

## 5. 画面の表示に必要な設定値もModelへ渡す

問題リストの詳細画面では、問題本文だけでなく、ユーザーの設定に応じて拼音または注音を表示する必要があった。

新しいControllerでは、問題一覧を渡すだけでなく、`HttpSession`から`pronunciationType`も取得してModelへ格納した。

```java
PronunciationType pronunciationType =
        (PronunciationType) session.getAttribute("pronunciationType");

if (pronunciationType == null) {
    pronunciationType = PronunciationType.PINYIN;
}

model.addAttribute("pronunciationType", pronunciationType);
```

ここで`listItems`は「何を表示するか」というデータであり、`pronunciationType`は「どのように表示するか」を決める設定値である。

```text
listItems
    → 表示対象の問題データ

pronunciationType
    → 発音表記の表示条件
```

今回の実装から、**Thymeleafの画面に必要なのはDBから取得したデータだけではなく、セッションに保持された表示設定なども含まれる**ことを確認できた。

また、セッション値が`null`の場合の既定値をController側で用意しておくことで、テンプレートが未設定値を前提に複雑な分岐を持つことを避けられる。

---

## 6. POST後のリダイレクト先も画面設計の一部である

独立した詳細画面を作成した後、リストから問題を削除すると、問題リストの一覧画面へ戻ってしまうことが分かった。

原因は、削除処理の最後が旧設計のままだったことである。

```java
return "redirect:/user/question-list/list";
```

新設計では、問題を削除した後も同じリストの詳細画面にとどまる方が操作しやすい。そのため、`listId`を使ってリダイレクト先を変更した。

```java
return "redirect:/user/question-list/detail?listId=" + listId;
```

```text
問題リスト詳細画面（listId = 3）
        ↓
問題を削除（POST）
        ↓
同じlistIdを指定してリダイレクト
        ↓
問題リスト詳細画面（listId = 3）
```

このとき、削除成功メッセージは`RedirectAttributes`のFlash属性で渡している。

```java
redirectAttributes.addFlashAttribute(
        "successMessage",
        messageSource.getMessage(
                "questionListItem.delete.success",
                null,
                locale));
```

リダイレクトでは別のGETリクエストが発生するため、通常のModel属性をそのまま引き継ぐのではなく、Flash属性を使って次の画面にメッセージを表示する。

今回の実装から、**画面を新設・分離した場合は、表示用GETだけでなく、POST後にどこへ戻るかまで確認する必要がある**ことを学んだ。

---

## 7. レスポンシブ対応はテーブルを縮小するだけではない

新しい詳細ページでも、最初はPC向けのテーブル形式で問題を表示していた。

しかし、スマートフォンで確認すると、中国語や日本語の文章が細かく折り返され、項目を確認するために横スクロールも必要だった。

そこで、**画面幅に応じて同じ問題データを別のレイアウトで表示する**方式へ変更した。

```text
PC
    → テーブル形式

スマートフォン
    → カード形式
```

PC用のテーブルにはBootstrapの表示クラスを指定した。

```html
<div th:unless="${#lists.isEmpty(listItems)}"
     class="table-responsive d-none d-md-block">
```

ここで、

- `d-none`：通常は非表示
- `d-md-block`：`md`以上の画面幅で表示

という指定により、スマートフォンではテーブルを非表示にする。

別途スマートフォン用のカードを用意することで、狭い画面でも問題ごとに情報を縦に並べられるようにした。

今回の実装から、**レスポンシブ対応は同じ表を無理に小さく表示することだけではなく、端末の画面幅に合わせて情報の配置自体を変える方法もある**と理解できた。

また、表示形式を二つ用意しても、どちらも同じ`listItems`を利用するため、問題データを別々に取得する必要はない。

---

## まとめ

029-03では、JavaScriptによる問題一覧の動的生成から、Spring MVCとThymeleafによる独立した詳細ページへ設計を変更した。

特に重要だったのは、**データの取得と画面への表示は分けて考えられる**という点である。Repository・Service・DTOを基本的に再利用しながら、Controllerの戻り値とテンプレートの構成を変えることで、画面の作り方を変更できた。

また、画面を分離する際には、旧HTMLやJavaScriptの削除、セッションに保持された表示設定の受け渡し、削除後のリダイレクト先など、表示処理以外にも確認すべき箇所があることを学んだ。

最後に、スマートフォンでの実機確認によって、PCで問題なく表示できるテーブルが狭い画面では使いにくいことが分かった。PC用テーブルとスマートフォン用カードを切り替える構成を採用したことで、**同じデータでも利用環境に応じて適切な表示形式を選ぶ必要がある**ことを確認できた。

これで029-01から029-03までの学習ログを通して、問題リスト機能のDB設計、JavaScriptによる操作、そして画面構成の再設計までを整理できた。
