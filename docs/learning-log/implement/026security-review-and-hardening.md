# 026 セキュリティ設定の見直しと強化 — 学習ログ

今回の見直しでは、Spring Securityによる認証・認可、ユーザー所有データへのアクセス制御、CSRF保護、アクセス拒否後のエラー処理について理解を整理した。単に設定を追加するだけでなく、「何を、どの層で防ぐのか」を意識した。

## 1. URL単位とメソッド単位のアクセス制御

`SecurityConfig`を確認したところ、`/admin/**`に対するROLEの制限が設定されていなかった。

HTMLではADMIN以外に管理者画面へのリンクを表示しないようにしていたが、`/admin/menu`を直接指定すると`ROLE_USER`でもアクセスできる状態だった。

そこで、

```java
.requestMatchers("/admin/**").hasRole("ADMIN")
```

を追加し、`/admin/**`を管理者のみアクセス可能にした。

画面上でリンクやボタンを非表示にしていても、URLを直接入力してアクセスできればセキュリティ上の問題になるため、`SecurityConfig`側でアクセスを制限した。

```java
.hasRole("ADMIN")
```

と指定すると、Spring Security側では通常`ROLE_ADMIN`というAuthorityをチェックする。

`/practice/**`は、

```java
.requestMatchers("/practice/**").permitAll()
```

としているため、URL単位では未ログインユーザーもアクセスできる。

その中でログインユーザーを前提としている処理を確認し、

```text
/practice/new/start
/practice/evaluation
```

に、

```java
@PreAuthorize("isAuthenticated()")
```

を追加した。

`/practice/count`にはすでに同じ設定が存在していたため、そのままとした。

`/practice/**`には未ログインユーザーも利用できる処理があるため、URL全体を認証必須にはせず、ユーザー固有のデータを必要とする処理だけをメソッド単位で認証必須にした。

`SecurityConfig`で`/practice/**`が`permitAll()`になっていても、`@PreAuthorize("isAuthenticated()")`を使用することで、特定のメソッドだけを認証済みユーザーに制限できる。

URL単位の制限は広い範囲に適用し、未ログイン利用を許すURL群の中では必要なメソッドにだけ認証条件を追加する、という使い分けになった。なお、`@PreAuthorize`を機能させるにはメソッドセキュリティを有効化する必要があり、今回の`SecurityConfig`には`@EnableMethodSecurity`が既に設定されていた。


---

## 2. ユーザー所有データの認可

`EvaluationService`では、

```java
key.setUserId(user.getId());
key.setQuestionId(questionId);
```

としていたため、他人の`StudyHistory`そのものを更新することはできなかった。

しかし、`questionId`がログインユーザーからアクセス可能な問題かは確認していなかったため、

```text
userId = 自分
questionId = 他人所有のAI生成由来問題
```

という`StudyHistory`を作成できる可能性があった。

`FavoriteService`にも同じ問題があった。

そこで`QuestionRepository`に、

```java
@Query("""
        SELECT COUNT(q) > 0
        FROM Question q
        WHERE q.questionId = :questionId
        AND (
            q.aiGenerated = false
            OR q.owner.id = :userId
        )
        """)
boolean existsAccessibleQuestion(
        @Param("questionId") Long questionId,
        @Param("userId") Long userId);
```

を追加した。

`EvaluationService`と`FavoriteService`では、処理前にこのメソッドを使用してアクセス可能な問題か確認するようにした。

既存の通常学習では、

```text
q.ai_generated = false
OR
(q.ai_generated = true AND owner_user_id = :userId)
```

という条件を使用していた。

今回も同じ考え方を使用し、

```text
通常問題
→ 操作可能

自分所有のAI生成由来問題
→ 操作可能

他人所有のAI生成由来問題
→ 操作不可

存在しない問題
→ 操作不可
```

となるようにした。

「ログインしているか」と「そのデータを操作する権限があるか」は別の問題である。

画面上で他人の問題が見えないだけではなく、リクエストの`questionId`を改ざんして直接処理を呼び出した場合でも防げるようにする必要がある。

そのため、ユーザー固有データでは、

```text
questionId = 操作対象
AND
userId / owner = ログインユーザー
```

のように、操作対象とログインユーザーを結び付けて確認する必要がある。

---

## 3. CSRF保護と状態変更処理のPOST化

`SecurityConfig`では、

```java
// http.csrf(csrf -> csrf.disable());
```

となっており、CSRFを無効化していないため、Spring Security標準のCSRF保護が有効になっていることを確認した。

一方、

```text
/language-variant
/pronunciation-type
```

では`@GetMapping`でDBやSessionの状態を変更していた。

そこで両方を`@PostMapping`へ変更した。

また、`user/settings.html`で使用していた`<a>`もPOSTフォームへ変更した。

データの登録・更新・削除について確認し、状態を変更する処理をPOSTに統一した。

ログアウトについても確認したが、既存実装ですでに、

```html
<form th:action="@{/logout}" method="post">
```

となっていたため、追加修正は行わなかった。

Spring SecurityではCSRF対策がデフォルトで有効になっているため、明示的に無効化していなければCSRF保護が働く。

また、DBやSessionなどの状態を変更する処理について、GETではなくPOSTを使用する構成にする必要があることを確認した。

ここで区別すべきなのは、**GETで状態変更しないこと**と、**CSRFトークンによって不正なリクエストを検証すること**は別の対策だという点である。POSTに変更しただけでCSRF対策が完了するわけではない。今回のThymeleafフォームはSpring SecurityのCSRF保護が有効な構成で使用する。


---

## 4. Security固有ログの設計

ログイン成功・ログイン失敗・アカウント凍結によるログイン失敗についてSecurity固有のログを追加した。

ログイン成功時は、

```java
log.info(
        "Login succeeded: loginId={}",
        authentication.getName());
```

とした。

通常のログイン失敗は、

```java
log.warn(
        "Login failed: loginId={}",
        loginId);
```

アカウント凍結によるログイン失敗は、

```java
log.warn(
        "Login failed because account is locked: loginId={}",
        loginId);
```

とした。

ログイン成功は正常な認証イベントなのでINFOとした。

ログイン失敗はアプリケーション自体の障害ではないためERRORとはせず、Security上確認する必要があるイベントとしてWARNとした。

また、アカウント凍結による失敗は通常のログイン失敗とメッセージを分け、ログから原因を判別できるようにした。

ログには`loginId`だけを記録し、パスワードやSession IDは出力しないようにした。

Security関連ログについて、

```text
ログイン成功
→ INFO

通常のログイン失敗
→ WARN

アカウント凍結によるログイン失敗
→ WARN
```

というログレベルの使い分けを整理できた。

---

## 5. AccessDeniedHandlerと認証情報の取得

`ROLE_USER`で`/admin/menu`へアクセスするとSpring Securityによって403 Forbiddenにはなっていたが、

```text
403 Forbidden
↓
Whitelabel Error Page
```

となっており、アクセス拒否を記録する独自ログも存在していなかった。

そこで`CustomAccessDeniedHandler`を作成した。

Handlerでは、

```java
log.warn(
        "Access denied: loginId={}, uri={}",
        loginId,
        request.getRequestURI());
```

としてアクセス拒否をWARNログへ記録し、

```java
response.sendError(
        HttpServletResponse.SC_FORBIDDEN);
```

によって403エラーを返すようにした。

`SecurityConfig`には、

```java
.exceptionHandling(exception -> exception
        .accessDeniedHandler(customAccessDeniedHandler)
)
```

を追加し、Spring Securityによるアクセス拒否時に作成したHandlerが実行されるようにした。

`AccessDeniedHandler`はアクセスを許可するかどうかを判断するものではない。

```text
/admin/**にはROLE_ADMINが必要
↓
Spring Securityがアクセス可否を判断
↓
アクセス拒否
↓
AccessDeniedHandler
```

という順番であり、Handlerはアクセス拒否が発生した**後の処理**を担当する。

また、`AccessDeniedHandler`はSpring Securityが用意しているインターフェースで、アクセス拒否時に呼ばれる`handle()`をオーバーライドして独自処理を実装する。

`CustomAccessDeniedHandler`でアクセス拒否されたユーザーの`loginId`を取得するため、

```java
Authentication authentication =
        SecurityContextHolder
                .getContext()
                .getAuthentication();
```

とした。

取得した`Authentication`から、

```java
authentication.getName()
```

を使用して`loginId`を取得した。

認証情報を取得できなかった場合でもログを残せるように、

```java
String loginId = authentication != null
        ? authentication.getName()
        : "anonymous";
```

とした。

今回の実装を通して、

```text
SecurityContextHolder
↓ getContext()
SecurityContext
↓ getAuthentication()
Authentication
```

という関係を整理した。

それぞれ、

```text
SecurityContextHolder
→ 現在のユーザーのセキュリティ情報へアクセスするための入口

SecurityContext
→ Authenticationを保持する入れ物

Authentication
→ 実際の認証情報
```

という役割を持つ。

`SecurityContextHolder.getContext()`は現在の処理に紐づく`SecurityContext`を取得するstaticメソッドであり、`SecurityContext.getAuthentication()`によって、その中に保持されている`Authentication`を取得できる。

補足として、`Authentication`が`null`ではない場合でも、匿名認証を表すオブジェクトである可能性がある。したがって、`null`でないことだけを根拠に「ログイン済み」と判断することはできない。今回のコードはアクセス拒否ログの識別名を取得するためのものであり、認可判定自体はSpring Securityが行う。


---

## 6. Spring Boot標準のエラー処理

Whitelabel Error Pageの代わりに、

```text
templates/error.html
templates/error/403.html
```

を作成した。

`error.html`は共通エラー画面、`error/403.html`は403 Forbidden専用画面として使用する。

共通エラー画面では、

```html
<h1 th:text="${status} + ' ' + ${error}">
    Error
</h1>
```

として、Spring Bootから渡されるHTTPステータスコードとエラー名を表示するようにした。

`${message}`についてはそのまま表示せず、`messages.properties`で管理するユーザー向けメッセージを表示するようにした。

`${message}`にはエラーの内容によってユーザーに見せる必要のない内部情報が含まれる可能性があるため、そのまま表示しないようにした。

代わりに、

```html
<p th:text="#{error.common.message}">
    エラーが発生しました。
</p>
```

として共通メッセージを使用した。

通常のThymeleaf画面では、

```text
/about
↓
@GetMapping("/about")
↓
return "about"
↓
about.html
```

のようにControllerからテンプレートを指定する。

一方、今回の403では独自の`@GetMapping`を作成しなくても、

```text
Spring Securityがアクセスを拒否
↓
CustomAccessDeniedHandler
↓
response.sendError(403)
↓
Servletのエラー処理
↓
Spring Bootの標準エラー処理
↓
templates/error/403.html
```

という流れで403専用ページが表示される。

`response.sendError(403)`から直接`403.html`が呼ばれているわけではなく、その間をServletやSpring Bootが処理している。

開発者が書いたコード上にはこの途中の処理が表面化しないため、

```text
自分で書いていない処理
=
処理そのものが存在しない
```

と考えないことが重要である。

Spring Bootでは、開発者が書くべき部分の間をフレームワークが自動的につないでくれることがあり、今回のエラーページ表示はその典型的な例である。

---

## 7. 今回の学習で理解したこと

今回の実装を通して、セキュリティ対策は一か所の設定だけでは完結しないことが分かった。`/admin/**`のようなURL単位の認可、`@PreAuthorize`によるメソッド単位の認証、`questionId`に対する所有者チェックは、それぞれ防ぐ対象が異なる。

また、アクセス拒否が起きた後のログ記録とエラー画面の表示は、アクセス可否の判定とは別の責務である。`AccessDeniedHandler`から`sendError(403)`を呼び出した後も、ServletとSpring Bootの標準エラー処理が続いていることを確認できた。

今後も、画面に表示されないことをアクセス制御の代わりにせず、リクエストを直接送られた場合を考慮してServiceやSecurity設定で確認することが重要だと理解した。
