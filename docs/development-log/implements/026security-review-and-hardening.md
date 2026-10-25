# 026 セキュリティ設定の見直しと強化

## このチャプターで行ったこと

既存のSpring Security設定と認証・認可に関係する処理を見直し、アクセス制御の不足、状態変更に使用するHTTPメソッド、セキュリティ関連ログ、エラー表示を整理した。

主な変更は以下のとおり。

- `/admin/**`へのアクセスを`ROLE_ADMIN`に限定
- ゲスト利用を許可する通常学習のうち、認証が必要な処理に`@PreAuthorize`を追加
- 理解度・お気に入り操作時に、対象問題へのアクセス権を確認
- 学習対象言語と発音表記の変更をGETからPOSTへ変更
- ログイン成功・失敗、アクセス拒否に関するログを追加
- `CustomAccessDeniedHandler`と403専用・共通エラーページを追加

## 1. URL単位とメソッド単位のアクセス制御

### 1.1 管理者URLを`ROLE_ADMIN`に限定

見直し前は`/admin/**`に対するロール制限がなく、一般ユーザーがURLを直接指定して管理者画面へアクセスできる状態だった。

`SecurityConfig`に次の設定を追加した。

```java
.requestMatchers("/admin/**").hasRole("ADMIN")
```

これにより、`/admin/**`へのアクセスには`ROLE_ADMIN`が必要となる。画面上で管理メニューを非表示にするだけでなく、URLへの直接アクセス自体を制限することが目的である。

### 1.2 通常学習の一部メソッドに認証条件を追加

通常学習はゲストも利用できるため、`SecurityConfig`の設定は維持した。

```java
.requestMatchers("/practice/**").permitAll()
```

ただし、`/practice/**`配下のすべての操作をゲストに許可するわけではない。ログインユーザーの情報を前提とする次のメソッドに認証条件を追加した。

```java
@PreAuthorize("isAuthenticated()")
@GetMapping("/practice/new/start")
```

```java
@PreAuthorize("isAuthenticated()")
@PostMapping("/practice/evaluation")
```

`/practice/count`には、以前から同じ`@PreAuthorize("isAuthenticated()")`が設定されていたため、変更していない。

URL全体はゲストに公開しつつ、認証が必要な操作をメソッド単位で制限する構成とした。

## 2. ユーザー所有問題へのアクセスチェック

`EvaluationService`と`FavoriteService`ではログインユーザーの`userId`を利用していたが、リクエストで指定された`questionId`が、そのユーザーにとって操作可能な問題かどうかを確認していなかった。

そのため、他のユーザーが所有するAI生成由来問題を指定して、理解度やお気に入りを操作できないようチェックを追加した。

### 2.1 `QuestionRepository`に判定メソッドを追加

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

判定条件は次のとおり。

- AI生成問題ではない通常問題（`aiGenerated = false`）はアクセス可能
- AI生成由来問題は所有者の`owner.id`がログインユーザーの`userId`と一致する場合のみアクセス可能
- どちらにも該当しない場合、または指定した問題が存在しない場合は`false`

### 2.2 理解度・お気に入り更新前に確認

`EvaluationService`と`FavoriteService`の両方で、更新処理の前に次のチェックを追加した。

```java
if (!questionRepository.existsAccessibleQuestion(
        questionId,
        user.getId())) {

    throw new IllegalArgumentException(
            messageSource.getMessage(
                    "question.error.accessDenied",
                    null,
                    locale));
}
```

これにより、単にログイン済みであることだけでなく、**操作対象の問題にアクセスできること**も確認する。

エラーメッセージとして`messages.properties`に次を追加し、簡体字・繁体字のメッセージファイルにも対応する文言を設定した。

```properties
question.error.accessDenied=アクセスできない問題です。
```

なお、ここで実装したのはService側のアクセス可否判定と`IllegalArgumentException`の送出である。後述するSpring Securityの`AccessDeniedHandler`による403応答とは別の経路である。

## 3. 設定変更処理をGETからPOSTへ変更

学習対象言語と発音表記の変更は、DBやSessionの状態を書き換える処理であるにもかかわらず、GETリクエストで実行していた。

対象は以下の2つ。

| URL | 変更前 | 変更後 |
|---|---|---|
| `/language-variant` | `@GetMapping` | `@PostMapping` |
| `/pronunciation-type` | `@GetMapping` | `@PostMapping` |

それぞれ`LanguageVariantController`、`PronunciationTypeController`のマッピングを変更した。

併せて`user/settings.html`のリンクをPOSTフォームへ変更した。学習対象言語の変更例は以下のとおり。

```html
<form th:action="@{/language-variant}" method="post">
    <input type="hidden"
           name="languageVariant"
           value="MAINLAND">
    <input type="hidden"
           name="redirect"
           value="/user/settings">
    <button type="submit">...</button>
</form>
```

発音表記も同様に変更した。

```html
<form th:action="@{/pronunciation-type}" method="post">
    <input type="hidden"
           name="pronunciationType"
           value="PINYIN">
    <button type="submit">...</button>
</form>
```

状態を変更する操作をGETから切り離し、POSTで送信する構成とした。ログアウトはすでにPOSTフォームを使用していたため変更していない。

## 4. 認証・アクセス拒否のログを追加

025では一般的な業務ログを整理した。今回はその続きとして、Spring Securityに関係する認証・認可イベントのログを追加した。

| イベント | ログレベル | 記録内容 |
|---|---|---|
| ログイン成功 | INFO | `loginId` |
| ログイン失敗 | WARN | 入力された`loginId` |
| アカウント凍結によるログイン失敗 | WARN | 入力された`loginId` |
| アクセス拒否 | WARN | `loginId`、アクセス先URI |

### 4.1 ログイン成功

`LoginSuccessHandler`に`@Slf4j`を追加し、成功時のログを出力するようにした。

```java
log.info(
        "Login succeeded: loginId={}",
        authentication.getName());
```

### 4.2 ログイン失敗

`SecurityConfig`にも`@Slf4j`を追加し、ログインフォームに入力されたIDを取得する。

```java
String loginId = request.getParameter("loginId");
```

通常の認証失敗と、アカウント凍結による失敗を区別して記録する。

```java
log.warn(
        "Login failed: loginId={}",
        loginId);
```

```java
log.warn(
        "Login failed because account is locked: loginId={}",
        loginId);
```

パスワードはログへ出力しない。

## 5. アクセス拒否Handlerとエラーページの追加

### 5.1 `CustomAccessDeniedHandler`

権限不足によるアクセス拒否を独自に処理するため、`AccessDeniedHandler`を実装した。

```java
@Component
@Slf4j
public class CustomAccessDeniedHandler
        implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        String loginId = authentication != null
                ? authentication.getName()
                : "anonymous";

        log.warn(
                "Access denied: loginId={}, uri={}",
                loginId,
                request.getRequestURI());

        response.sendError(
                HttpServletResponse.SC_FORBIDDEN);
    }
}
```

このHandlerは、拒否されたユーザーとアクセス先をWARNログに記録し、HTTP 403を返す。

`SecurityConfig`でHandlerをDIし、`SecurityFilterChain`へ登録した。

```java
private final CustomAccessDeniedHandler customAccessDeniedHandler;
```

```java
.exceptionHandling(exception -> exception
        .accessDeniedHandler(customAccessDeniedHandler))
```

### 5.2 共通エラーページと403専用ページ

Whitelabel Error Pageの代わりにアプリケーション独自の画面を表示するため、次のテンプレートを追加した。

```text
src/main/resources/templates/
├── error.html
└── error/
    └── 403.html
```

共通エラーページ`error.html`ではステータスとエラー情報を表示する。

```html
<h1 th:text="${status} + ' ' + ${error}">Error</h1>
<p th:text="#{error.common.message}">
    エラーが発生しました。
</p>
<a th:href="@{/}" th:text="#{error.back.home}">
    ホーム画面に戻る
</a>
```

403専用ページ`error/403.html`では、アクセス権限がないことを表示する。

```html
<h1 th:text="#{error.403.title}">
    アクセス権限がありません
</h1>
<p th:text="#{error.403.message}">
    このページを表示する権限がありません。
</p>
<a th:href="@{/}" th:text="#{error.back.home}">
    ホーム画面に戻る
</a>
```

表示文言は`messages.properties`に追加し、簡体字・繁体字のファイルにも対応する文言を設定した。

```properties
error.common.title=エラー
error.common.message=エラーが発生しました。
error.403.title=アクセス権限がありません
error.403.message=このページを表示する権限がありません。
error.back.home=ホーム画面に戻る
```

## 6. 動作確認

原稿で確認結果が記録されている項目は以下のとおり。

| 確認項目 | 確認結果 |
|---|---|
| `ROLE_USER`で`/admin/menu`へアクセス | 403 Forbiddenとなり、`error/403.html`を表示 |
| 管理者画面へのアクセス拒否 | `CustomAccessDeniedHandler`からWARNログを出力 |
| 存在しないURLへのアクセス | 共通エラーページ`error.html`を表示 |

一方、`/practice/new/start`・`/practice/evaluation`の認証制御、他ユーザー所有問題の操作拒否、設定変更のPOST化については、元の記録に具体的な動作確認結果が記載されていないため、ここでは検証済みとは扱わない。

## 7. まとめ

今回の見直しでは、URLやメソッドへのアクセス制御だけでなく、**ログイン後に操作するデータの所有者確認**も追加した。

また、状態変更を行うGETリクエストをPOSTへ変更し、認証・認可に関するログとエラー画面を整備した。

変更後の主な責務は次のようになる。

```text
SecurityConfig
├── URL単位の認証・認可
├── /admin/** → ROLE_ADMIN
├── ログイン失敗時のログ
└── AccessDeniedHandlerの登録

PracticeController
└── @PreAuthorizeによるメソッド単位の認証制御

QuestionRepository
└── 問題へのアクセス可否判定

EvaluationService / FavoriteService
└── 更新前の問題アクセスチェック

LoginSuccessHandler
└── ログイン成功ログ

CustomAccessDeniedHandler
├── アクセス拒否ログ
└── 403レスポンス

templates/
├── error.html
└── error/403.html
```

なお、今回の変更によってアプリケーション全体のセキュリティを網羅的に検証できたわけではない。実装内容と、記録のある動作確認結果を区別して残す。
