# 007 ログイン機能の実装

## 1. 基本的なログイン機能の実装

**Commit**

    git commit -m "feat: implement basic login authentication"

Spring Securityを使用した基本的なログイン機能を実装する。

### 準備① pom.xml

Spring Securityと、ThymeleafからSpring Securityの認証情報を扱うための拡張ライブラリを使用する。

```xml
<!-- Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Thymeleaf拡張ライブラリ（セキュリティ） -->
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

### 準備② application.yml

ログイン機能実装前に設定していたSpring Securityの自動設定除外を削除する。

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### SecurityConfig

アクセス制御、ログイン、ログアウトの基本設定を追加する。

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    
    // アクセス制御の設定
    http.authorizeHttpRequests(authorize -> authorize
            .requestMatchers(
                    PathRequest.toStaticResources().atCommonLocations()
            ).permitAll()
            .requestMatchers("/").permitAll()
            .requestMatchers("/login").permitAll()
            .requestMatchers("/practice/**").permitAll()
            .requestMatchers("/signup", "/signup/**").permitAll()
            .requestMatchers("/complete").permitAll()
            .anyRequest().authenticated()
        )
        
        // ログインの設定
        .formLogin(login -> login
            .loginPage("/login")
            .usernameParameter("loginId")
            .passwordParameter("password")
            .defaultSuccessUrl("/", false)
            .permitAll()
        )
        
        // ログアウトの設定
        .logout(logout -> logout
            .logoutUrl("/logout")
        );
    
    // CSRFを無効化
    http.csrf(csrf -> csrf.disable());
    
    return http.build();
}
```

`authorizeHttpRequests()` ではURLごとのアクセス制御を設定する。

```java
.requestMatchers(
        PathRequest.toStaticResources().atCommonLocations()
).permitAll()
```

CSSやJavaScriptなどの静的リソースへのアクセスを許可する。

```java
.requestMatchers("/").permitAll()
.requestMatchers("/login").permitAll()
.requestMatchers("/practice/**").permitAll()
.requestMatchers("/signup", "/signup/**").permitAll()
.requestMatchers("/complete").permitAll()
.anyRequest().authenticated()
```

指定したURLは未ログインでもアクセス可能とし、それ以外はログインを必須とする。

`formLogin()` ではフォームログインについて設定する。

```java
.loginPage("/login")
.usernameParameter("loginId")
.passwordParameter("password")
.defaultSuccessUrl("/", false)
```

- `/login` をログイン画面として使用
- ログインIDのパラメーター名を `loginId` に設定
- パスワードのパラメーター名を `password` に設定
- ログイン成功後の基本遷移先を `/` に設定

`logout()` ではログアウトについて設定する。

```java
.logout(logout -> logout
    .logoutUrl("/logout")
);
```

`/logout` へのリクエストでSpring Securityのログアウト処理を実行する。

この段階ではCSRF対策を一時的に無効化しておき、ログイン機能完成後に有効へ戻す。

### UserDetailsServiceImpl

Spring SecurityとDB上のユーザー情報をつなぐ `UserDetailsServiceImpl` を作成する。

```java
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserAccountService userAccountService;
    
    @Override
    public UserDetails loadUserByUsername(String loginId)
            throws UsernameNotFoundException {
        
        // ユーザー情報取得
        Users loginUser = userAccountService.getUserOne(loginId);
        
        // ユーザーが存在しない場合
        if (loginUser == null) {
            throw new UsernameNotFoundException("user not found"); 
        }
        
        // ロールList作成
        GrantedAuthority authority =
                new SimpleGrantedAuthority(
                        "ROLE_" + loginUser.getRole().name()
                );

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(authority);
        
        // UserDetails生成
        UserDetails userDetails = new User(
                loginUser.getLoginId(),
                loginUser.getPassword(),
                authorities
        );

        return userDetails;
    }
}
```

`loadUserByUsername()` は、Spring Securityが認証時にユーザー情報を取得するために呼び出すメソッド。

```java
Users loginUser = userAccountService.getUserOne(loginId);
```

ログイン画面から送られたログインIDをもとに、DBからユーザーを取得する。

```java
if (loginUser == null) {
    throw new UsernameNotFoundException("user not found"); 
}
```

ユーザーが存在しない場合は `UsernameNotFoundException` を発生させ、Spring Securityへユーザーが見つからなかったことを伝える。

```java
GrantedAuthority authority =
        new SimpleGrantedAuthority(
                "ROLE_" + loginUser.getRole().name()
        );

List<GrantedAuthority> authorities = new ArrayList<>();
authorities.add(authority);
```

DB上のユーザーが持つロールから、Spring Securityが扱う権限情報を作成する。

```java
UserDetails userDetails = new User(
        loginUser.getLoginId(),
        loginUser.getPassword(),
        authorities
);
```

取得したログインID、パスワード、権限を使って、Spring Securityが扱える `UserDetails` を生成する。

```java
return userDetails;
```

生成した `UserDetails` をSpring Securityへ返し、その情報を使って認証処理が続行される。

処理全体は以下の流れになる。

    Spring Security
        ↓
    UserDetailsServiceImpl
        ↓
    ログインIDからDBのユーザーを取得
        ↓
    ユーザーの権限を作成
        ↓
    UserDetailsを生成
        ↓
    Spring Securityへ返す

### UserAccountService

ログインIDからユーザーを取得する処理を追加する。

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountService {
    
    private final UserRepository userRepository;
    
    public Users getUserOne(String loginId) {

        log.debug("ユーザー検索 userId={}", loginId);

        return userRepository.findByLoginId(loginId)
                .orElse(null);
    }
}
```

### LoginController

ログイン画面を表示するControllerを追加する。

```java
@Controller
public class LoginController {

    @GetMapping("/login")
    public String getLogin() {
        
        return "/login";
    }
}
```

ログイン認証自体はSpring Securityが行うため、Controllerではログイン画面の表示のみ行う。

### login.html

ログイン画面を作成する。

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/layout}">
<head>
    <meta charset="UTF-8">
    <title th:text="#{login.title}">ログイン</title>
</head>
<body>

    <div class="container mt-5" layout:fragment="content">

        <div class="row justify-content-center">
            <div class="col-md-6 col-lg-4">

                <form method="post" th:action="@{/login}">

                    <h2 class="text-center mb-4"
                        th:text="#{login.title}">
                        ログイン
                    </h2>

                    <!-- ログインID -->
                    <div class="mb-3">
                        <input type="text"
                               class="form-control"
                               th:placeholder="#{login.loginId}"
                               name="loginId">
                    </div>

                    <!-- パスワード -->
                    <div class="mb-3">
                        <input type="password"
                               class="form-control"
                               th:placeholder="#{login.password}"
                               name="password">
                    </div>

                    <!-- ログイン -->
                    <button type="submit"
                            class="btn btn-primary w-100"
                            th:text="#{login.button}">
                        ログイン
                    </button>

                    <!-- 新規登録 -->
                    <div class="text-center mt-3">
                        <a th:href="@{/signup}"
                           th:text="#{login.signup}">
                            新規登録はこちら
                        </a>
                    </div>

                    <!-- Topへ戻る -->
                    <div class="text-center mt-3">
                        <a th:href="@{/}"
                           class="btn btn-secondary"
                           th:text="#{login.backToTop}">
                            Topに戻る
                        </a>
                    </div>

                </form>

            </div>
        </div>

    </div>

</body>
</html>
```

### 各種messages.properties

```properties
login.title=ログイン
login.loginId=ログインID
login.password=パスワード
login.button=ログイン
login.signup=新規登録はこちら
login.backToTop=Topに戻る
```

他言語は省略。

### 実行

`http://localhost:8080/login` にアクセスして、既存アカウントのログインIDとパスワードを入力する。

![](../../images/0007-01.png)

ログインに成功すると遷移し、失敗すると `/login?error` とエラーを伴ってログイン画面に差し戻される。

![](../../images/0007-02.png)

---

## 2. ログイン失敗時の処理を追加

**Commit**

    git commit -m "feat: add login failure handling"

ログインに失敗した際、何も表示されない状態を改善し、エラーメッセージを表示する。

ログイン成功時については、そのままHome画面へ戻すため特別なメッセージは追加しない。

### SecurityConfig

`failureHandler` を追加する。

```java
.failureHandler(new AuthenticationFailureHandler() {
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {

        HttpSession session = request.getSession(true);

        session.setAttribute(
                "loginErrorMessage",
                "login.error"
        );

        response.sendRedirect("/login");
    }
})
```

`failureHandler()` は、ログイン認証に失敗した場合の処理を設定する。

```java
new AuthenticationFailureHandler() {
```

認証失敗時の処理を定義する `AuthenticationFailureHandler` を、その場で実装する。

```java
public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception)
        throws IOException
```

`onAuthenticationFailure()` は認証失敗時にSpring Securityから呼び出される。

ここでは、

- `request`：ブラウザから送られてきたHTTPリクエスト
- `response`：ブラウザへ返すHTTPレスポンス
- `exception`：認証失敗時に発生した例外

を受け取る。

```java
HttpSession session = request.getSession(true);
```

リクエストからSessionを取得する。

```java
session.setAttribute(
        "loginErrorMessage",
        "login.error"
);
```

Sessionにログインエラー用のメッセージキーを保存する。

```java
response.sendRedirect("/login");
```

ブラウザを `/login` へリダイレクトする。

そのため、ログイン失敗時は、

    認証失敗
        ↓
    failureHandler
        ↓
    Sessionにlogin.errorを保存
        ↓
    /loginへリダイレクト

という流れになる。

### LoginController

Sessionに保存したエラーメッセージをログイン画面へ渡す。

```java
@GetMapping("/login")
public String getLogin(Model model, HttpSession session) {
    
    String loginErrorMessage =
            (String) session.getAttribute("loginErrorMessage");

    if (loginErrorMessage != null) {
        model.addAttribute("loginErrorMessage", loginErrorMessage);
        session.removeAttribute("loginErrorMessage");
    }
    
    return "/login";
}
```

Sessionから `loginErrorMessage` を取得し、存在する場合はModelへ追加する。

Modelへ追加した後はSessionから削除し、ログイン失敗直後に一度だけ表示されるようにする。

### login.html

ログインエラーの表示領域を追加する。

```html
<!-- ログインエラー -->
<div class="text-danger mb-3"
     th:if="${loginErrorMessage}"
     th:text="#{__${loginErrorMessage}__}">
</div>
```

```html
th:if="${loginErrorMessage}"
```

`loginErrorMessage` が存在する場合のみエラー表示領域を表示する。

```html
th:text="#{__${loginErrorMessage}__}"
```

`loginErrorMessage` に格納されている値をメッセージキーとして使用する。

今回の場合は、

    loginErrorMessage
        ↓
    login.error
        ↓
    messages.properties
        ↓
    ログインIDまたはパスワードが正しくありません。

となる。

`__...__` は変数の値を先に展開するためのThymeleafのプリプロセッシング。

### 各messages.properties

```properties
login.error=ログインIDまたはパスワードが正しくありません。
```

他言語は省略。

### 実行

`http://localhost:8080/login` にアクセスしてログイン失敗を試すと、エラーメッセージとともにログイン画面へ差し戻される。

![](../../images/0006-05.png)

エラーメッセージ表示後にF5を押すと、Sessionからメッセージを削除しているため表示が消える。

---

## 3. ログアウト成功時の処理を追加

**Commit**

    git commit -m "feat: add logout success handling"

ログアウト成功後にHome画面へ戻し、「ログアウトしました。」と表示する。

### SecurityConfig

`logoutSuccessHandler` を追加する。

```java
.logoutSuccessHandler(new LogoutSuccessHandler() {
    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException {

        HttpSession session = request.getSession(true);
        session.setAttribute(
                "logoutMessage",
                "logout.success"
        );

        response.sendRedirect("/");
    }
})
```

`logoutSuccessHandler()` は、ログアウトが成功した後の処理を設定する。

```java
public void onLogoutSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication)
        throws IOException
```

`onLogoutSuccess()` はログアウト成功時にSpring Securityから呼び出される。

ここでは、

- `request`：Sessionの取得
- `response`：リダイレクト
- `authentication`：ログアウトしたユーザーの認証情報

を扱える。今回は `authentication` は使用しない。

```java
HttpSession session = request.getSession(true);
session.setAttribute(
        "logoutMessage",
        "logout.success"
);
```

Sessionを取得し、ログアウト成功メッセージのキーを保存する。

```java
response.sendRedirect("/");
```

その後、Home画面へリダイレクトする。

処理の流れは以下になる。

    ログアウト成功
        ↓
    logoutSuccessHandler
        ↓
    Sessionにlogout.successを保存
        ↓
    /へリダイレクト

### HomeController

Sessionに保存されたログアウト成功メッセージを取得する。

```java
String logoutMessage =
        (String) session.getAttribute("logoutMessage");

if (logoutMessage != null) {
    model.addAttribute("logoutMessage", logoutMessage);
    session.removeAttribute("logoutMessage");
}
```

```java
String logoutMessage =
        (String) session.getAttribute("logoutMessage");
```

`SecurityConfig` でSessionに保存した `logoutMessage` を取得する。

```java
if (logoutMessage != null) {
    model.addAttribute("logoutMessage", logoutMessage);
    session.removeAttribute("logoutMessage");
}
```

メッセージが存在する場合はModelへ渡し、その後Sessionから削除する。

これによって、

    SecurityConfig
        ↓
    Sessionにメッセージを保存
        ↓
    HomeController
        ↓
    Sessionから取得
        ↓
    Modelへ追加
        ↓
    Sessionから削除
        ↓
    home.html

という流れで、ログアウト直後に一度だけメッセージを表示する。

### 各messages.properties

```properties
logout.success=ログアウトしました。
```

他言語は省略。

### home.html

```html
<div class="alert alert-success d-inline-block"
     th:if="${logoutMessage}"
     th:text="#{__${logoutMessage}__}">
</div>
```

### 実行

ログイン後にログアウトすると、Home画面にログアウト成功メッセージが表示される。

![](../../images/0007-04.png)

---

## 4. ログイン・ログアウトボタンの追加

**Commit**

    git commit -m "feat: add login and logout buttons to header"

ハンバーガーアイコン内に以下を追加する。

- ログイン中 → ログアウト
- 非ログイン中 → ログイン

### header.html

Spring SecurityのThymeleaf拡張を使用するため、名前空間を追加する。

```html
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
```

ハンバーガーメニュー内にログイン・ログアウトを追加する。

```html
<!-- 未ログイン時 -->
<li sec:authorize="isAnonymous()">
    <a class="dropdown-item"
       th:href="@{/login}"
       th:text="#{header.login}">
        ログイン
    </a>
</li>

<!-- ログイン時 -->
<li sec:authorize="isAuthenticated()">
    <form th:action="@{/logout}" method="post">
        <button type="submit"
                class="dropdown-item"
                th:text="#{header.logout}">
            ログアウト
        </button>
    </form>
</li>
```

### 各messages.properties

```properties
header.login=ログイン
header.logout=ログアウト
```

他言語は省略。

### 実行

ログイン中：

![](../../images/0007-05.png)

非ログイン中：

![](../../images/0007-06.png)

---

## 5. ヘッダーにユーザーIDを表示

**Commit**

    git commit -m "feat: add user dropdown menu to header"

ヘッダーのユーザーアイコンをクリックするとドロップダウンを表示する。

ログイン中：

- ユーザーID
- プロフィール

非ログイン中：

- ゲスト
- 新規登録

### header.html

```html
<!-- ユーザーアイコン -->
<button class="btn btn-outline-light dropdown-toggle"
        type="button"
        data-bs-toggle="dropdown"
        aria-expanded="false">
    <i class="bi bi-person"></i>
</button>

<ul class="dropdown-menu dropdown-menu-end">

    <!-- ログイン中：ユーザーID -->
    <li sec:authorize="isAuthenticated()">
        <span class="dropdown-item-text fw-bold"
              sec:authentication="name">
            userId
        </span>
    </li>

    <!-- 未ログイン：ゲスト -->
    <li sec:authorize="isAnonymous()">
        <span class="dropdown-item-text fw-bold"
              th:text="#{header.guest}">
            ゲスト
        </span>
    </li>

    <li><hr class="dropdown-divider"></li>

    <!-- ログイン中：プロフィール -->
    <li sec:authorize="isAuthenticated()">
        <a class="dropdown-item"
           th:href="@{/user/profile}"
           th:text="#{header.profile}">
            プロフィール
        </a>
    </li>

    <!-- 未ログイン：新規登録 -->
    <li sec:authorize="isAnonymous()">
        <a class="dropdown-item"
           th:href="@{/signup}"
           th:text="#{header.signup}">
            新規登録
        </a>
    </li>

</ul>
```

### 各messages.properties

```properties
header.guest=ゲスト
header.profile=プロフィール
header.signup=新規登録
```

他言語は省略。

### 実行

ログイン時：

![](../../images/0007-07.png)

非ログイン時：

![](../../images/0007-08.png)

---

## 6. Remember-Me認証の作成

**Commit**

    git commit -m "feat: add remember-me authentication"

Sessionが切れても自動的にログイン状態を維持するRemember-Me認証を実装する。

### login.html

```html
<!-- Remember-Me -->
<div class="form-group mt-3 text-center">
    <input class="form-check-input"
           type="checkbox"
           id="remember-me"
           name="remember-me">

    <label class="form-check-label"
           for="remember-me"
           th:text="#{login.rememberMe}">
        ログイン状態を保持
    </label>
</div>
```

### 各messages.properties

```properties
login.rememberMe=ログイン状態を保持
```

他言語は省略。

### SecurityConfig

```java
.rememberMe(remember -> remember
        .rememberMeParameter("remember-me")
        .tokenValiditySeconds(3600)
);
```

Remember-Me Cookieの有効期限は1時間（3600秒）に設定する。

#### 追加修正 9月26日

```text
git commit -m "fix: set remember-me cookie validity to 7 days"
```

Remember-Me Cookieの有効期限は1週間（7 * 24 * 60 * 60秒）に設定する。

```java
.rememberMe(remember -> remember
        .rememberMeParameter("remember-me")
        .tokenValiditySeconds(7 * 24 * 60 * 60)
);
```

### 実行

ログイン画面でRemember-Meのチェックボックスにチェックを入れてログインする。

![](../../images/0007-09.png)

ログイン後、開発者ツールの「Application」→「Cookies」を確認する。

![](../../images/0007-10.png)

`JSESSIONID` に加えて `remember-me` Cookieが作成されていることを確認した。

Sessionタイムアウト後にF5を押しても、ログイン中のみ表示されるボタンやリンクが残り、Remember-Me認証が機能していることを確認した。

---

## 7. CSRF対策

**Commit**

    git commit -m "security: enable CSRF protection"

Spring SecurityではCSRF対策がデフォルトで有効になっている。

これまでは実装途中のため無効化していたが、ログイン関連機能が完成したためCSRF対策を有効に戻す。

### SecurityConfig

以下のCSRF無効化設定をコメントアウトする。

```java
// CSRFを無効化
// http.csrf(csrf -> csrf.disable());
```

### signup.html

CSRFトークンを手動で明示する場合は以下のように記述できる。

```html
<!-- CSRF対策(手動で明示する場合必要)
<input type="hidden"
       th:name="${_csrf.parameterName}" 
       th:value="${_csrf.token}" />
-->
```

ただし今回はコメントアウトしたままとする。

`th:action` を使用すると、Spring Securityが必要とするCSRFトークンのhiddenパラメーターが自動的に追加されるため、手動で記述する必要はない。

現在POSTを使用している、

- `signup.html`：新規登録
- `login.html`：ログイン
- `header.html`：ログアウト

はいずれも `th:action` を使用している。

---

## 次回の実装

ログイン機能の基本実装が完了したため、一旦中断していた学習ページの実装に戻る。

次は、ログインしないと使用できない学習機能を実装する。

### 追加修正：Remember Meによる再認証後のセッション情報復元

**Commit**

```bash
git commit -m "fix: restore user session preferences after remember-me authentication"
```

前述の実装では、Remember MeのCookieを利用することで、HTTPセッションの有効期限が切れた後もログイン状態を維持できるようにした。

しかし、その後の動作確認で、**Remember Meによる自動認証後に、一部のユーザー設定が正しく復元されない問題**が見つかった。

具体的には、ログイン状態は維持されているにもかかわらず、学習対象言語が國語から普通話に戻り、問題数も正しく表示されなくなるという現象である。

今回は、この問題の原因を調査し、Spring SecurityのFilterチェーンに独自Filterを追加して対応した。

また、Filterを自作するのは今回が初めてだったため、Servlet Filterの基本的な仕組みや、Spring SecurityにおけるFilterの実行順序についても整理する。

#### 1. 発生した問題

本アプリケーションでは、ログインユーザーごとに以下の設定を保存している。

- `languageVariant`：学習対象言語（普通話／國語）
- `pronunciationType`：発音表記（ピンイン／注音符号）

これらの設定はデータベースの`users`テーブルに保存しており、通常のフォームログイン時には、`LoginSuccessHandler`で取得してHTTPセッションにも格納している。

該当する処理は以下のとおりである。

```java
Users user = userAccountService.getUserOne(authentication.getName());

HttpSession session = request.getSession();

session.setAttribute(
        "languageVariant", user.getLanguageVariant());

session.setAttribute(
        "pronunciationType", user.getPronunciationType());
```

通常のログイン直後は、この処理によってユーザー設定がセッションに保存されるため、画面にも正しく反映される。

しかし、Remember Meを有効にしてログインし、30分以上操作せずにページを更新すると、以下の現象が発生した。

- ログイン状態は維持されている。
- 学習対象言語が國語（TAIWAN）から普通話（MAINLAND）に戻っている。
- AI生成問題数が0問と表示される。
- 通常のフォームログインをやり直すと、元の設定に戻る。

つまり、**Remember Meによる認証自体は成功しているにもかかわらず、ユーザー設定が正しく復元されていない**状態だった。

#### 2. 原因：Remember MeはHTTPセッションの属性を復元しない

今回の問題を理解するには、HTTPセッションとRemember Meの役割を区別する必要がある。

##### HTTPセッションとRemember Meの違い

HTTPセッションは、サーバー側でユーザーごとの情報を保持する仕組みである。

一方、Remember Meは、HTTPセッションが失われた後でも、Cookieを利用してユーザーを再認証できるようにする仕組みである。

[Spring Security 使い方メモ Remember-Me（Qiita）](https://qiita.com/opengl-8080/items/7c34053c74448d39e8f5)では、`JSESSIONID`を削除した後も、Remember MeのCookieを利用して認証状態が復元されることを検証している。

このことから、HTTPセッションとRemember MeのCookieは別々の役割を持つことが分かる。

本アプリケーションでは、修正前のHTTPセッションのタイムアウトは30分、Remember Meの有効期限は7日間としていた。

そのため、一定時間操作しなかった場合、以下の状態になる。

| 項目 | 状態 |
|---|---|
| HTTPセッション | 有効期限切れ |
| Remember MeのCookie | 有効期間内 |
| ログイン状態 | Remember Meによって復元可能 |
| `languageVariant` | 以前のセッション属性は失われている |
| `pronunciationType` | 以前のセッション属性は失われている |

重要なのは、**Remember Meが復元するのは認証状態であり、以前のHTTPセッションに保存されていた任意の属性まで復元するわけではない**という点である。

今回のケースでは、Remember Meによってログイン状態は復元されるものの、以前のセッションに保存していた`languageVariant`と`pronunciationType`は失われていた。

##### LoginSuccessHandlerが実行されない

さらに、通常のフォームログインとRemember Meでは、認証処理の経路が異なる。

通常のフォームログインでは、`SecurityConfig`に設定した`LoginSuccessHandler`が実行される。

```java
.formLogin(login -> login
        .loginPage("/login")
        .usernameParameter("loginId")
        .passwordParameter("password")
        .successHandler(loginSuccessHandler)
)
```

しかし、Remember Meによる自動認証では、フォームログイン用の`LoginSuccessHandler`は実行されない。

そのため、フォームログイン時に行っていたセッション属性の初期化処理も実行されない。

今回の現象を整理すると、以下の流れになる。

```text
通常のフォームログイン
    ↓
LoginSuccessHandlerが実行される
    ↓
DBからユーザー設定を取得
    ↓
HTTPセッションに保存
    ↓
正常に画面へ反映

一定時間操作しない
    ↓
HTTPセッションの有効期限切れ
    ↓
セッション属性が失われる
    ↓
Remember Meによる自動認証
    ↓
ログイン状態は復元される
    ↓
LoginSuccessHandlerは実行されない
    ↓
languageVariantなどが復元されない
    ↓
画面の表示に問題が発生
```

本アプリケーションでは、`languageVariant`を取得できない場合に普通話を初期値として扱う処理があるため、学習対象言語も変わってしまった。

また、AI生成問題数が0問になる現象については、言語設定が変わったことで抽出条件が一致しなくなった可能性がある。ただし、この表示については原因を個別に追跡して確定したものではない。

以上から、今回の問題はRemember Meの認証失敗ではなく、**再認証後にアプリケーション独自のセッション属性を復元する処理が存在しなかったこと**にあると判断した。

#### 3. 対策：独自Filterでセッション属性を復元する

今回の対策では、Remember Meによる自動認証後に、必要なセッション属性が存在しない場合、データベースからユーザー設定を取得して復元する。

ただし、Remember Me専用の処理にはせず、認証済みユーザーのセッション属性が欠けている場合に復元する仕組みとする。

処理の流れは以下のとおりである。

```text
HTTPリクエスト
    ↓
Spring Securityによる認証処理
    ↓
Remember Meによる自動認証
（必要な場合）
    ↓
独自Filter
    ↓
認証済みユーザーか確認
    ↓
セッション情報を確認
    ↓
必要な属性が存在するか？
    ├── Yes
    │     ↓
    │   復元処理をスキップ
    │
    └── No
          ↓
        DBからユーザー設定を取得
          ↓
        セッションに保存
    ↓
後続のFilterへ処理を渡す
    ↓
Controller
```

この処理を実現するため、`UserSessionRestorationFilter`というクラスを新規作成する。

ただし、今回初めて独自Filterを作成するため、先にFilterの仕組みを整理する。

#### 4. Spring BootのFilterとは

Filterとは、HTTPリクエストがServletやControllerに到達する前、またはレスポンスを返す過程で、共通処理を実行する仕組みである。

FilterはSpring Boot独自の機能ではなく、Jakarta Servletの仕様に基づいている。

[Spring Securityについて理解する①（DelegatingFilterProxy、FilterChainProxy、SecurityFilterChain）（Qiita）](https://qiita.com/devtkms/items/6b78a354f82a5874f1bc)では、Spring Securityの処理がServlet Filterの仕組みを利用して実行されることや、複数のFilterを組み合わせる構成について解説されている。

Filterの主な用途には、以下のようなものがある。

- HTTPリクエストやレスポンスのログ出力
- 文字エンコーディングの設定
- 認証・認可に関する処理
- セキュリティ対策
- リクエストやレスポンスの加工

Spring MVCアプリケーションでは、HTTPリクエストは概念的に次のような流れで処理される。

```text
ブラウザ
    ↓
Servlet Filter
    ↓
Servlet Filter
    ↓
DispatcherServlet
    ↓
Controller
    ↓
Service
    ↓
Repository
```

複数のFilterを順番に実行する仕組みを、**Filterチェーン**と呼ぶ。

FilterはControllerより前の段階で共通処理を実行できるため、複数のControllerにまたがる処理を実装する場合に利用できる。

今回のケースでは、Controllerが`languageVariant`などのセッション属性を参照する前に、それらを復元する必要がある。

そのため、各Controllerに復元処理を追加するのではなく、Controllerより前に実行されるFilterを利用することにした。

#### 5. Spring Securityでは多くのFilterが標準で動作している

Spring Securityを利用する場合、開発者がすべてのFilterを作成する必要はない。

Spring Securityには、認証・認可・CSRF対策などを担当するFilterが標準で用意されている。

[Hello World で学ぶ Spring Security の仕組み（Qiita）](https://qiita.com/opengl-8080/items/d4971ec4d2365c85ff99)では、Spring Securityが複数のFilterを組み合わせて認証やアクセス制御を実現する仕組みが説明されている。

代表的なFilterには、以下のようなものがある。

| Filter | 役割 |
|---|---|
| `SecurityContextHolderFilter` | 認証情報を扱うSecurityContextの管理 |
| `CsrfFilter` | CSRF対策 |
| `UsernamePasswordAuthenticationFilter` | フォームログインの認証処理 |
| `RememberMeAuthenticationFilter` | Remember Meによる自動認証 |
| `AnonymousAuthenticationFilter` | 匿名認証情報の設定 |
| `AuthorizationFilter` | アクセス権限の確認 |

実際に登録されるFilterは、Spring Securityの設定によって異なる。

本アプリケーションでは、`SecurityConfig`にフォームログインとRemember Meの設定を記述している。

```java
.formLogin(login -> login
        .loginPage("/login")
        .successHandler(loginSuccessHandler)
)
```

```java
.rememberMe(remember -> remember
        .rememberMeParameter("remember-me")
        .tokenValiditySeconds(7 * 24 * 60 * 60)
)
```

これらの設定に基づいて、Spring Securityが必要なFilterを構成する。

つまり、開発者が`RememberMeAuthenticationFilter`などを直接生成する必要はない。

##### Spring SecurityのFilterチェーンを管理するクラス

前述の[Spring Securityについて理解する①（Qiita）](https://qiita.com/devtkms/items/6b78a354f82a5874f1bc)では、`DelegatingFilterProxy`、`FilterChainProxy`、`SecurityFilterChain`の関係が整理されている。

それぞれの役割は以下のとおりである。

| クラス | 役割 |
|---|---|
| `DelegatingFilterProxy` | ServletコンテナとSpring管理のFilterを橋渡しする |
| `FilterChainProxy` | Spring SecurityのFilterチェーンを実行する |
| `SecurityFilterChain` | リクエストに適用するFilterの一覧などを保持する |

これらの関係を簡略化すると、次のようになる。

```text
HTTPリクエスト
    ↓
DelegatingFilterProxy
    ↓
FilterChainProxy
    ↓
SecurityFilterChain
    ├── SecurityContextHolderFilter
    ├── CsrfFilter
    ├── ...
    ├── RememberMeAuthenticationFilter
    ├── AnonymousAuthenticationFilter
    └── AuthorizationFilter
    ↓
DispatcherServlet
    ↓
Controller
```

※ 実際のFilter構成や順序は設定によって異なる。上記は説明のため一部を省略している。

`SecurityConfig`で使用している`HttpSecurity`は、この`SecurityFilterChain`を構築するための設定を行う。

今回のセッション属性復元処理は、Spring Securityが標準で提供する認証処理とは別の、アプリケーション固有の処理である。

そのため、独自Filterを作成し、Spring SecurityのFilterチェーンへ追加する必要がある。

#### 6. OncePerRequestFilterとは

今回の独自Filterでは、Spring Frameworkが提供する`OncePerRequestFilter`を継承する。

[Servlet Filter の概要とカスタマイズ方法について（Spring Security 6.0）（Qiita）](https://qiita.com/3087KT/items/a37ba6372211a4a6791e)では、`OncePerRequestFilter`を継承して独自Filterを作成する方法が紹介されている。

`OncePerRequestFilter`は、Servlet Filterを実装するための抽象クラスである。

名前のとおり、同一リクエストのディスパッチ内で、Filterの処理が重複して実行されないように管理する仕組みを持っている。

ただし、非同期処理やエラー処理では別のディスパッチが発生する場合があるため、HTTPリクエスト全体を通じて絶対に一度だけ実行されるという意味ではない。

この点は、[Spring FrameworkのOncePerRequestFilter Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html)でも説明されている。

今回のように、HTTPリクエストごとに共通処理を実行したい場合に利用できる。

##### doFilterInternal()とは

`OncePerRequestFilter`を継承したクラスでは、`doFilterInternal()`をオーバーライドして独自の処理を実装する。

```java
@Override
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain)
        throws ServletException, IOException {

    // 独自の処理

    filterChain.doFilter(request, response);
}
```

前述のQiitaの記事でも、`OncePerRequestFilter`を継承したクラスで`doFilterInternal()`をオーバーライドする実装方法が紹介されている。

`doFilterInternal()`は、`OncePerRequestFilter`で宣言されている抽象メソッドである。

開発者は、このメソッドにFilterで実行したい処理を記述する。

実行時には、親クラスの`doFilter()`が実行済み判定などを担当し、必要に応じて`doFilterInternal()`を呼び出す。

```text
HTTPリクエスト
    ↓
OncePerRequestFilter.doFilter()
    ↓
実行済みかどうかなどを確認
    ↓
UserSessionRestorationFilter.doFilterInternal()
    ↓
独自のセッション復元処理
```

今回のケースでは、この`doFilterInternal()`に、認証情報の取得、HTTPセッションの確認、データベースからのユーザー設定取得、セッション属性の復元を記述する。

##### filterChain.doFilter()とは

`filterChain.doFilter(request, response)`は、現在のFilterから後続のFilterへ処理を渡すためのメソッドである。

```java
filterChain.doFilter(request, response);
```

前述の[Servlet Filter の概要とカスタマイズ方法について（Qiita）](https://qiita.com/3087KT/items/a37ba6372211a4a6791e)でも、独自Filter内で`filterChain.doFilter()`を呼び出し、後続の処理へリクエストを渡す実装が紹介されている。

後続のFilterが存在する場合は次のFilterが実行され、Filterチェーンを通過すると通常はServletへ処理が渡される。

Spring MVCでは、その先で`DispatcherServlet`やControllerが処理を担当する。

```text
独自Filter
    ↓
filterChain.doFilter()
    ↓
後続のFilter
    ↓
DispatcherServlet
    ↓
Controller
```

今回の目的は、Controllerの実行前にセッション情報を復元することである。

そのため、復元処理を`filterChain.doFilter()`より前に記述する。

#### 7. UserSessionRestorationFilterを作成

`security`パッケージに`UserSessionRestorationFilter.java`を新規作成する。

```java
package io.github.mawsonlakes790913.chineseoutputforge.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class UserSessionRestorationFilter extends OncePerRequestFilter {

    private final UserAccountService userAccountService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // 現在の認証情報を取得
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        // 認証済みユーザーの場合のみ処理
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {

            // 既存のHTTPセッションを取得
            HttpSession session = request.getSession(false);

            // セッションまたはユーザー設定が存在しない場合
            if (session == null
                    || session.getAttribute("languageVariant") == null
                    || session.getAttribute("pronunciationType") == null) {

                // DBからユーザー設定を取得
                Users user = userAccountService.getUserOne(
                        authentication.getName());

                // セッションを取得（存在しなければ作成）
                session = request.getSession(true);

                // ユーザー設定を復元
                session.setAttribute(
                        "languageVariant", user.getLanguageVariant());

                session.setAttribute(
                        "pronunciationType", user.getPronunciationType());

                log.info("User session preferences restored: {}",
                        authentication.getName());
            }
        }

        // 後続のFilter・Controllerへ処理を渡す
        filterChain.doFilter(request, response);
    }
}
```

以下、各処理について説明する。

##### 7.1. 認証情報の取得

まず、`SecurityContextHolder`から現在の認証情報を取得する。

```java
Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
```

`SecurityContextHolder`は、Spring Securityで現在の処理に関連する`SecurityContext`を取得するためのクラスである。

`SecurityContext`には、認証情報を表す`Authentication`が格納される。

今回のケースでは、この認証情報からユーザーのログインIDを取得し、データベースに保存されている設定を検索する。

続いて、以下の条件で処理対象を限定する。

```java
if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
```

| 条件 | 意味 |
|---|---|
| `authentication != null` | 認証情報が存在する |
| `authentication.isAuthenticated()` | 認証済みとして扱われている |
| `!(authentication instanceof AnonymousAuthenticationToken)` | 匿名認証ではない |

Spring Securityでは、匿名ユーザーにも`AnonymousAuthenticationToken`が設定される場合がある。

この仕組みは、後述する`AnonymousAuthenticationFilter`によって実現される。

そのため、`isAuthenticated()`だけでログイン済みユーザーかどうかを判断せず、匿名認証を明示的に除外する。

##### 7.2. セッション情報の確認

次に、現在のHTTPセッションを取得する。

```java
HttpSession session = request.getSession(false);
```

`getSession(false)`は、有効なHTTPセッションが存在すれば取得し、存在しなければ`null`を返す。

新しいセッションは作成しない。

続いて、必要な属性が存在するか確認する。

```java
if (session == null
        || session.getAttribute("languageVariant") == null
        || session.getAttribute("pronunciationType") == null) {
```

ここでは、以下のいずれかに該当する場合に復元処理を実行する。

- HTTPセッション自体が存在しない。
- `languageVariant`が存在しない。
- `pronunciationType`が存在しない。

反対に、両方の属性が存在する場合は復元処理をスキップする。

これにより、通常のリクエストでは不要なデータベースへの問い合わせを避けられる。

##### 7.3. データベースから設定を復元

セッション属性が存在しない場合は、データベースからユーザー情報を取得する。

```java
Users user = userAccountService.getUserOne(
        authentication.getName());
```

`authentication.getName()`から認証済みユーザーのログインIDを取得し、`UserAccountService`を通じてユーザー情報を検索する。

続いて、HTTPセッションを取得する。

```java
session = request.getSession(true);
```

`getSession(true)`は、有効なセッションが存在すればそれを返し、存在しなければ新しいセッションを作成する。

最初に使用した`getSession(false)`との違いは以下のとおりである。

| メソッド | セッションが存在しない場合 |
|---|---|
| `getSession(false)` | `null`を返す |
| `getSession(true)` | 新しいセッションを作成する |

今回、最初に`getSession(false)`を使用するのは、セッションの有無を確認するだけで新しいセッションを作成しないためである。

一方、復元処理を実行する段階ではセッションが必要になるため、`getSession(true)`を使用する。

最後に、取得した設定をセッションへ保存する。

```java
session.setAttribute(
        "languageVariant", user.getLanguageVariant());

session.setAttribute(
        "pronunciationType", user.getPronunciationType());
```

これにより、Remember Meによる再認証後でも、データベースに保存されたユーザー設定を復元できる。

##### 7.4. 後続のFilterへ処理を渡す

最後に、後続のFilterへ処理を渡す。

```java
filterChain.doFilter(request, response);
```

第6節で説明したとおり、`filterChain.doFilter()`は後続のFilterやServletへ処理を進めるためのメソッドである。

今回のFilterは、認証済みユーザーのセッション属性を復元することが目的であり、HTTPリクエスト自体を終了させるものではない。

そのため、復元処理が必要な場合も不要な場合も、最後に`filterChain.doFilter()`を呼び出して処理を続行する。

また、今回のFilterでは`sendRedirect()`を使用していないため、GETリクエストはGETのまま、POSTリクエストはPOSTのまま処理を続行できる。

ただし、セッション期限切れによるCSRFトークンの失効は別の問題であり、このFilterだけで解決できるものではない。

#### 8. SecurityConfigに独自Filterを登録

`UserSessionRestorationFilter`を作成しただけでは、そのクラスが自動的にSpring SecurityのFilterチェーンへ追加されるわけではない。

そのため、`SecurityConfig`に以下のコードを追加する。

```java
http.addFilterAfter(
        new UserSessionRestorationFilter(userAccountService),
        AnonymousAuthenticationFilter.class
);
```

##### 8.1. addFilterAfter()とは

[Spring Security で JWT を使用し認可の判定を行う（Zenn）](https://zenn.dev/kiyotatakeshi/articles/d12a850f232d71)では、Spring SecurityのFilterチェーンに独自Filterを追加する際、既存のFilterを基準に実行位置を指定する方法が紹介されている。

この記事ではJWT認証を扱っているが、**既存のFilterを基準に独自Filterの実行位置を指定する**という仕組みは今回も同じである。

`addFilterAfter()`は、指定したFilterを基準となるFilterの後に配置するためのメソッドである。

基本的な構文は以下のとおりである。

```java
http.addFilterAfter(
        追加するFilter,
        基準となるFilter.class
);
```

今回の場合は、

```java
new UserSessionRestorationFilter(userAccountService)
```

によって独自Filterを生成し、

```java
AnonymousAuthenticationFilter.class
```

を基準として、その後に配置する。

また、`SecurityConfig`には以下のフィールドを追加する。

```java
private final UserAccountService userAccountService;
```

`SecurityConfig`には`@RequiredArgsConstructor`が付いているため、`UserAccountService`がコンストラクタインジェクションされる。

そのインスタンスを独自Filterへ渡している。

##### 8.2. RememberMeAuthenticationFilterとは

`RememberMeAuthenticationFilter`は、Remember Meによる自動認証を担当するFilterである。

[Spring Security 使い方メモ Remember-Me（Qiita）](https://qiita.com/opengl-8080/items/7c34053c74448d39e8f5)では、Remember Meの認証処理が`RememberMeAuthenticationFilter`から開始され、Remember Me関連のサービスへ処理が委譲される仕組みが解説されている。

このFilterは、現在の認証情報が存在しない場合に、Remember MeのCookieを利用して認証を試みる。

認証に成功すると、Spring Securityが現在のリクエストで使用する認証情報を設定する。

今回のケースでは、このFilterによってRemember Meの認証が完了した後に、独自Filterでユーザー設定を復元する必要がある。

##### 8.3. AnonymousAuthenticationFilterとは

`AnonymousAuthenticationFilter`は、認証情報が存在しない場合に、匿名ユーザーとしての認証情報を設定するFilterである。

前述の[Spring SecurityでJWTを使用し認可の判定を行う（Zenn）](https://zenn.dev/kiyotatakeshi/articles/d12a850f232d71)では、`AnonymousAuthenticationFilter`を含むFilterチェーンの動作が説明されている。

このFilterは、すでに認証済みのユーザーに対して、認証情報を匿名ユーザーのものへ置き換えるわけではない。

今回の独自Filterでは、以下の条件によって匿名ユーザーを処理対象から除外している。

```java
!(authentication instanceof AnonymousAuthenticationToken)
```

そのため、`AnonymousAuthenticationFilter`によって匿名認証情報が設定される仕組みを理解しておく必要がある。

##### 8.4. AuthorizationFilterとは

`AuthorizationFilter`は、HTTPリクエストに対するアクセス権限を確認するFilterである。

認証が「誰であるかを確認すること」であるのに対して、認可は「そのユーザーが対象の操作やリソースへアクセスできるか判断すること」である。

前述のZennの記事でも、`AuthorizationFilter`がアクセス権限の判定を行う仕組みについて説明されている。

本アプリケーションでは、例えば以下の設定が認可に関係する。

```java
.requestMatchers("/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

`/admin/**`へのアクセスにはADMINロールが必要となる。

今回のセッション復元Filterは、こうしたアクセス権限の判定とは別の役割を持つ。

##### 8.5. なぜAnonymousAuthenticationFilterの後に配置するのか

今回のFilterは、Spring Securityが設定した認証情報を取得して、ログイン済みユーザーかどうかを確認する。

```java
Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
```

そのため、認証情報が設定された後に実行する必要がある。

[Spring Security公式ドキュメントのServlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)では、Spring SecurityのFilterチェーンの構成やFilterの実行順序について説明されている。

Spring Securityの標準的なFilterの実行順序では、今回関係する部分は次のようになる。

```text
RememberMeAuthenticationFilter
    ↓
AnonymousAuthenticationFilter
    ↓
UserSessionRestorationFilter
    ↓
ExceptionTranslationFilter
    ↓
AuthorizationFilter
    ↓
Controller
```

※ 説明のため、その他のFilterは省略している。

`RememberMeAuthenticationFilter`はRemember Meによる認証を試みる。

その後、`AnonymousAuthenticationFilter`は、認証情報が存在しない場合に匿名認証情報を設定する。

したがって、`AnonymousAuthenticationFilter`の後に独自Filterを配置すれば、Remember Meによる認証結果を取得でき、匿名ユーザーも判別できる。

ただし、`AnonymousAuthenticationFilter`の直後に配置することが絶対条件ではない。

今回の本質的な要件は、Remember Meによる認証が終わった後、かつControllerでセッション属性を参照する前に復元処理を実行することである。

つまり、今回の配置は、Spring SecurityのFilterの役割と実行順序を踏まえた**本アプリケーション固有の設計判断**である。

なお、今回のFilterは`AuthorizationFilter`より前に実行されるため、アクセス権限のないリクエストでも、条件によってはユーザー情報の取得処理が実行される可能性がある。

この点は、必要に応じて今後の改善事項として検討する。

##### 8.6. @Componentを付けない理由

今回の`UserSessionRestorationFilter`には、`@Component`を付与していない。

Servlet Filterには、`@Component`や`FilterRegistrationBean`などを利用してServletコンテナ側に登録する方法もある。

しかし、今回はSpring Securityの認証処理との実行順序が重要であるため、`http.addFilterAfter()`でSpring SecurityのFilterチェーンへ明示的に登録している。

`@Component`を付けてServlet Filterとしても自動登録されると、Spring Security側の登録と重複し、意図しない順序で実行される可能性がある。

この点については、[Spring Security公式ドキュメントのServlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)でも、FilterをSpring Beanとして登録した場合に、Servletコンテナ側にも登録されてしまう可能性があることが説明されている。

今回のケースでは、Servletコンテナ側にFilterを追加する必要はなく、Spring SecurityのFilterチェーン内でのみ実行したい。

そのため、`@Component`を付けず、`SecurityConfig`から登録する方式とした。

#### 9. 動作確認

修正後、ローカル環境でRemember Meを有効にしてログインし、HTTPセッションの有効期限が切れた後に、ユーザー設定が復元されるか確認した。

検証時点ではHTTPセッションのタイムアウトは30分としていた。

14:45にログインし、30分以上経過した15:20にページを更新する方法で検証した。

初期状態は以下のとおりである。

| 項目 | 初期状態 |
|---|---|
| ログイン状態 | ログイン済み |
| 学習対象言語 | 國語（TAIWAN） |
| 初級問題数 | 118問 |
| 中級問題数 | 116問 |
| 上級問題数 | 115問 |

確認する項目は以下の4点である。

1. Remember Meによってログイン状態が維持されていること。
2. 学習対象言語が國語のままになっていること。
3. 問題数が更新前と一致すること。
4. セッション復元処理が実行されたことをログで確認できること。

##### 検証結果：成功

15:20にページを更新し、以下の項目がすべて正常であることを確認した。

- Remember Meによってログイン状態が維持されている。
- 学習対象言語が國語（TAIWAN）のまま維持されている。
- 問題数が更新前と変わらず、初級118問・中級116問・上級115問と表示されている。
- コンソールにセッション復元処理のINFOログが出力されている。

コンソールには、以下のログが出力された。

```text
User session preferences restored: mawsonlakes_admin
```

このログは、`UserSessionRestorationFilter`内の以下のコードによるものである。

```java
log.info("User session preferences restored: {}",
        authentication.getName());
```

これにより、HTTPセッションの有効期限が切れた後でも、Remember Meによる自動認証とともに、データベースに保存されたユーザー設定がセッションへ復元されることを確認できた。

**今回の不具合は解消された。**

#### 10. application.ymlでセッションのタイムアウトを60分に変更

今回の不具合は独自Filterの追加によって解消されたが、HTTPセッションのタイムアウトについても見直すことにした。

本アプリケーションは中国語の瞬間作文を行う学習アプリケーションであり、ユーザーが問題を読んだり、中国語の文章を考えたりしている間は、サーバーへ新しいリクエストが送信されない場合がある。

HTTPセッションのタイムアウトは、基本的に最後にサーバーへアクセスしてからの経過時間で判定される。

そのため、30分間操作がないだけでセッションが失われる設定では、長時間考えている途中にセッションが切れる可能性がある。

今回のFilterによって`languageVariant`と`pronunciationType`は復元できるようになったが、通常学習の進行状況など、HTTPセッションにのみ保存されている情報まで復元できるわけではない。

そこで、セッション切れが発生する頻度を減らすため、`application.yml`に以下の設定を追加した。

```yaml
server:
  servlet:
    session:
      timeout: 60m
```

`server.servlet.session.timeout`は、Spring BootでHTTPセッションのタイムアウトを指定するための設定である。

`60m`は60分を意味する。

これにより、HTTPセッションのタイムアウトを30分から60分に延長した。

Remember Meの有効期限は引き続き7日間としている。

| 項目 | 変更前 | 変更後 |
|---|---|---|
| HTTPセッション | 30分 | 60分 |
| Remember Me | 7日間 | 7日間 |
| セッション属性の復元 | なし | 独自Filterで実行 |

なお、セッションの保持時間が長くなると、共用端末などでログイン状態が残る時間も長くなるため、利便性とセキュリティのバランスを考慮する必要がある。

今回は、学習アプリケーションとしての使いやすさを優先し、60分とした。

**Commit**

```bash
git commit -m "config: extend session timeout to 60 minutes"
```

#### 11. まとめ

今回の問題は、Remember Meによる認証が失敗していたのではなく、認証成功後にアプリケーション独自のセッション情報が復元されていなかったことが原因だった。

Remember Meは、HTTPセッションの有効期限が切れた後でも認証状態を復元する仕組みである。しかし、以前のHTTPセッションに保存されていた任意の属性まで復元するものではない。

また、Remember Meによる自動認証では、フォームログイン用の`LoginSuccessHandler`が実行されないため、通常のログイン時に行っていたセッション属性の初期化処理も実行されない。

この問題を解消するため、`OncePerRequestFilter`を継承した`UserSessionRestorationFilter`を作成した。

さらに、`SecurityConfig`の`addFilterAfter()`を使用して、`AnonymousAuthenticationFilter`の後に独自Filterを配置した。

これにより、認証済みユーザーのセッション属性が存在しない場合に、データベースからユーザー設定を取得して復元する処理を追加できた。

また、セッション属性がすでに存在する場合はデータベースへの問い合わせを行わないため、通常のリクエストで不要なDBアクセスが発生しない構成としている。

動作確認では、HTTPセッションの有効期限が切れた後でも、Remember Meによってログイン状態が維持され、学習対象言語や問題数が正常に表示されることを確認できた。

さらに、学習中のセッション切れを減らすため、`application.yml`でセッションのタイムアウトを60分に延長した。

今回の修正を通じて、HTTPセッションとRemember Meの役割の違いに加え、Spring SecurityのFilterチェーンの仕組みや、独自Filterの作成・登録方法について理解を深めることができた。

特に、**Spring Securityが管理する認証状態と、アプリケーションが独自に管理するセッション属性は別のものである**という点は、今後のセッション管理を設計するうえでも重要な知識となった。