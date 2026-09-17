# 026 Security Review and Hardening

## 概要

既存のSpring Security設定および認証・認可に関係する処理をアプリケーション全体で確認し、不足していたアクセス制御やSecurityログ、エラー処理を追加した。

主な変更内容は以下の通り。

```text
・/admin/** のADMIN限定化
・PracticeControllerの認証制御追加
・ユーザー所有問題のアクセスチェック追加
・ユーザー設定変更処理のPOST化
・Security固有ログの追加
・アクセス拒否Handlerの追加
・403専用および共通エラーページの追加
```

## 1. 管理者URLのアクセス制御を追加

### SecurityConfig

`/admin/**`に対するROLE制限が設定されておらず、`ROLE_USER`でもURLを直接指定することで管理者画面へアクセスできる状態になっていた。

`SecurityConfig`に以下を追加した。

```java
// 管理者のみアクセス可能
.requestMatchers("/admin/**").hasRole("ADMIN")
```

変更後は、

```text
/admin/**
→ ROLE_ADMINのみアクセス可能
```

となる。

## 2. PracticeControllerの認証制御を追加

`/practice/**`は未ログインユーザーでも通常学習を利用できるよう、`SecurityConfig`では引き続き、

```java
.requestMatchers("/practice/**").permitAll()
```

とする。

一方、ログインユーザーの情報を必要とする以下の処理に認証制御を追加した。

### `/practice/new/start`

```java
@PreAuthorize("isAuthenticated()")
@GetMapping("/practice/new/start")
```

### `/practice/evaluation`

```java
@PreAuthorize("isAuthenticated()")
@PostMapping("/practice/evaluation")
```

既存の`/practice/count`については、すでに以下が設定されていたため変更していない。

```java
@PreAuthorize("isAuthenticated()")
@GetMapping("/practice/count")
```

## 3. ユーザー所有問題のアクセスチェックを追加

`EvaluationService`および`FavoriteService`ではログインユーザーの`userId`を使用していたが、リクエストから受け取った`questionId`が、そのユーザーからアクセス可能な問題であるかを確認していなかった。

そのため、他のユーザーが所有するAI生成由来問題の`questionId`を指定できないようアクセスチェックを追加した。

### QuestionRepository

以下のメソッドを追加した。

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

以下の条件に該当する問題をアクセス可能とする。

```text
通常問題
OR
owner.id = ログインユーザーのuserId
```

### EvaluationService

理解度を更新する前に、対象問題へのアクセス可否を確認する処理を追加した。

```java
// 操作可能な問題か確認
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

### FavoriteService

お気に入りを更新する前にも同じアクセスチェックを追加した。

```java
// 操作可能な問題か確認
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

### messages.properties

アクセスできない問題が指定された場合のメッセージを追加した。

```properties
question.error.accessDenied=アクセスできない問題です。
```

簡体字・繁体字のメッセージファイルにも対応するメッセージを追加した。

## 4. ユーザー設定変更処理をPOSTへ変更

DBおよびSessionの状態を変更していた以下の処理をGETからPOSTへ変更した。

```text
/language-variant
/pronunciation-type
```

### LanguageVariantController

変更前：

```java
@GetMapping("/language-variant")
```

変更後：

```java
@PostMapping("/language-variant")
```

### PronunciationTypeController

変更前：

```java
@GetMapping("/pronunciation-type")
```

変更後：

```java
@PostMapping("/pronunciation-type")
```

### user/settings.html

設定変更に使用していた`<a>`をPOSTフォームへ変更した。

学習対象言語については、

```html
<form th:action="@{/language-variant}" method="post">

    <input type="hidden"
           name="languageVariant"
           value="MAINLAND">

    <input type="hidden"
           name="redirect"
           value="/user/settings">

    <button type="submit">
        ...
    </button>

</form>
```

の形式へ変更した。

発音表記についても、

```html
<form th:action="@{/pronunciation-type}" method="post">

    <input type="hidden"
           name="pronunciationType"
           value="PINYIN">

    <button type="submit">
        ...
    </button>

</form>
```

の形式へ変更した。

ログアウトについては既存実装ですでにPOSTフォームを使用していたため変更していない。

## 5. Security固有ログを追加

### LoginSuccessHandler

`@Slf4j`を追加した。

```java
@Component
@Slf4j
public class LoginSuccessHandler
extends SavedRequestAwareAuthenticationSuccessHandler {
```

ログイン成功時に以下のINFOログを出力するようにした。

```java
log.info(
        "Login succeeded: loginId={}",
        authentication.getName());
```

### SecurityConfig

`SecurityConfig`にも`@Slf4j`を追加した。

```java
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {
```

ログイン失敗時に入力された`loginId`を取得する。

```java
String loginId =
        request.getParameter("loginId");
```

通常のログイン失敗では以下を出力する。

```java
log.warn(
        "Login failed: loginId={}",
        loginId);
```

アカウント凍結によるログイン失敗では以下を出力する。

```java
log.warn(
        "Login failed because account is locked: loginId={}",
        loginId);
```

Security関連ログは以下のレベルで記録する。

```text
ログイン成功
→ INFO

ログイン失敗
→ WARN

アカウント凍結によるログイン失敗
→ WARN

アクセス拒否
→ WARN
```

## 6. アクセス拒否Handlerを追加

`ROLE_USER`など権限を持たないユーザーによるアクセス拒否を独自に処理するため、`CustomAccessDeniedHandler`を追加した。

### CustomAccessDeniedHandler

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

        // 認証情報を取得
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        // ログインIDを取得
        String loginId = authentication != null
                ? authentication.getName()
                : "anonymous";

        // アクセス拒否を記録
        log.warn(
                "Access denied: loginId={}, uri={}",
                loginId,
                request.getRequestURI());

        // 403エラーを返す
        response.sendError(
                HttpServletResponse.SC_FORBIDDEN);
    }
}
```

アクセス拒否時には、

```text
Access denied: loginId=mawsonlakes_user, uri=/admin/menu
```

の形式でWARNログを出力する。

### SecurityConfig

`CustomAccessDeniedHandler`をDIする。

```java
private final CustomAccessDeniedHandler customAccessDeniedHandler;
```

さらに`SecurityFilterChain`へ以下を追加した。

```java
.exceptionHandling(exception -> exception
        .accessDeniedHandler(customAccessDeniedHandler)
)
```

これにより、Spring Securityによるアクセス拒否時に`CustomAccessDeniedHandler`が実行される。

## 7. エラーページを追加

Whitelabel Error Pageの代わりにアプリケーション独自のエラー画面を表示するため、共通エラーページと403専用エラーページを追加した。

```text
src/main/resources/templates/
├── error.html
└── error/
    └── 403.html
```

### error.html

専用ページを用意していないエラーに使用する共通エラーページを追加した。

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">

<head>
    <meta charset="UTF-8">
    <title th:text="#{error.common.title}">エラー</title>
</head>

<body>

    <h1 th:text="${status} + ' ' + ${error}">
        Error
    </h1>

    <p th:text="#{error.common.message}">
        エラーが発生しました。
    </p>

    <a th:href="@{/}"
       th:text="#{error.back.home}">
        ホーム画面に戻る
    </a>

</body>

</html>
```

### error/403.html

403 Forbidden専用ページを追加した。

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">

<head>
    <meta charset="UTF-8">
    <title th:text="#{error.403.title}">
        アクセス権限がありません
    </title>
</head>

<body>

    <h1 th:text="#{error.403.title}">
        アクセス権限がありません
    </h1>

    <p th:text="#{error.403.message}">
        このページを表示する権限がありません。
    </p>

    <a th:href="@{/}"
       th:text="#{error.back.home}">
        ホーム画面に戻る
    </a>

</body>

</html>
```

### messages.properties

エラー画面用のメッセージを追加した。

```properties
error.common.title=エラー
error.common.message=エラーが発生しました。
error.403.title=アクセス権限がありません
error.403.message=このページを表示する権限がありません。
error.back.home=ホーム画面に戻る
```

簡体字・繁体字のメッセージファイルにも対応する文言を追加した。

## 8. 動作確認

以下のアクセス制御とエラー画面を確認した。

### 管理者画面

```text
ROLE_USER
↓
/admin/menu
↓
403 Forbidden
↓
error/403.html
```

アクセス拒否時には`CustomAccessDeniedHandler`からWARNログが出力される。

### 共通エラー画面

存在しないURLへアクセスし、専用エラーページが存在しないエラーについて`error.html`が表示されることを確認した。

## 変更後の構成

今回の変更後、主なSecurity処理は以下の構成となった。

```text
SecurityConfig
├── URL単位の認証・認可
├── /admin/** → ROLE_ADMIN
├── ログイン失敗処理
└── AccessDeniedHandlerの登録

PracticeController
└── @PreAuthorizeによる認証制御

QuestionRepository
└── 問題へのアクセス可否確認

EvaluationService
└── 評価前の問題アクセスチェック

FavoriteService
└── お気に入り変更前の問題アクセスチェック

LoginSuccessHandler
└── ログイン成功ログ

CustomAccessDeniedHandler
├── アクセス拒否ログ
└── 403レスポンス

templates/
├── error.html
└── error/
    └── 403.html
```
