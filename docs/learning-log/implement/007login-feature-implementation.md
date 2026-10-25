# 007 ログイン機能の実装

## 1. Spring Securityのログイン処理の流れ

今回のログイン機能の実装では、最初に「どのクラスがどこまでログイン処理を担当しているのか」が分かりにくかった。

特に、`LoginController` がログイン処理を行うものだと思っていたが、実際の認証処理はSpring Securityが担当しており、`LoginController` はログイン画面を表示するだけでよい。

大まかな流れは以下のようになる。

```text
login.html
    ↓
loginId・passwordをPOST
    ↓
Spring Security
    ↓
UserDetailsServiceImpl
    ↓
DBからユーザー情報を取得
    ↓
UserDetailsをSpring Securityへ返す
    ↓
Spring Security側でパスワードなどを検証
    ↓
成功 / 失敗
```

### UserDetailsServiceImplは何のためにあるのか

Spring Securityは、自分で作成した `Users` Entityや `users` テーブルの構造をそのまま理解できるわけではない。

そこで、自分のアプリケーションが持っているユーザー情報をSpring Securityへ渡す役割として `UserDetailsServiceImpl` を実装した。

```java
public class UserDetailsServiceImpl implements UserDetailsService
```

`UserDetailsService` はSpring Securityが提供するインターフェースで、これを実装すると認証時に `loadUserByUsername()` が利用される。

```java
@Override
public UserDetails loadUserByUsername(String loginId)
        throws UsernameNotFoundException {
```

Spring SecurityからログインIDを受け取り、そのログインIDを使ってDBからユーザーを検索する。

```java
Users loginUser = userAccountService.getUserOne(loginId);
```

ここで取得している `Users` は、自分で作成したEntity。

ユーザーが存在しない場合は、

```java
if (loginUser == null) {
    throw new UsernameNotFoundException("user not found"); 
}
```

とする。

`UsernameNotFoundException` はSpring Securityが提供している例外で、Spring Securityへ「そのユーザーは存在しない」と伝えるために使用する。

### UserDetailsは誰に返しているのか

最初は、

```java
return userDetails;
```

が誰に返されているのか分かりにくかった。

これはControllerや `UserAccountService` に返しているわけではなく、`loadUserByUsername()` を呼び出したSpring Securityへ返している。

```java
UserDetails userDetails = new User(
        loginUser.getLoginId(),
        loginUser.getPassword(),
        authorities
);

return userDetails;
```

自作の `Users` Entityから、

- ログインID
- パスワード
- 権限

を取り出し、Spring Securityが扱える `UserDetails` に変換して返している。

つまり `UserDetailsServiceImpl` は、

```text
自作のUsers
    ↓
Spring Securityが扱えるUserDetailsへ変換
    ↓
Spring Securityへ返す
```

という橋渡しをしていると考えると理解しやすい。

ここで重要なのは、`UserDetailsServiceImpl`自体は入力されたパスワードを照合しないこと。今回の構成では、取得した`UserDetails`を使い、Spring Securityの認証処理（`DaoAuthenticationProvider`など）が`PasswordEncoder`を利用してパスワードを検証する。ユーザー情報の取得と認証の判定は別の役割だった。

### GrantedAuthorityとSimpleGrantedAuthority

ロールの処理では、

```java
GrantedAuthority authority =
        new SimpleGrantedAuthority(
                "ROLE_" + loginUser.getRole().name()
        );
```

というSpring Security特有のクラスが登場した。

`GrantedAuthority` は、Spring Securityで「ユーザーが持っている権限」を表すインターフェース。

`SimpleGrantedAuthority` は、その `GrantedAuthority` のシンプルな実装クラス。

DBから取得したロールをそのまま渡すのではなく、Spring Securityが扱える権限情報へ変換している。

さらに、

```java
List<GrantedAuthority> authorities = new ArrayList<>();
authorities.add(authority);
```

としてListへ格納してから `UserDetails` に渡している。

現時点ではロールは1つだが、Spring Securityでは複数の権限を持てる構造になっているためListとして扱う。

---

## 2. 認証失敗時の処理で理解に時間がかかった点

ログイン失敗時の処理では、コードが入れ子になっているため、最初はどこからどこまでが一つの処理なのか分かりにくかった。

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

### failureHandlerの構造

まず、

```java
.failureHandler(...)
```

はSpring Securityに「ログインに失敗したときの処理」を設定する部分。

その中で、

```java
new AuthenticationFailureHandler() {
```

として `AuthenticationFailureHandler` をその場で実装し、さらにその中の、

```java
onAuthenticationFailure(...)
```

に実際の認証失敗時の処理を書く。

構造としては、

```text
failureHandler
    └─ AuthenticationFailureHandler
          └─ onAuthenticationFailure()
                └─ 実際の処理
```

と考えると分かりやすい。

### HttpServletRequestとHttpServletResponse

`onAuthenticationFailure()` の、

```java
HttpServletRequest request,
HttpServletResponse response,
AuthenticationException exception
```

についても、最初はそれぞれが何を表しているのか分からなかった。

今回の処理で重要なのは `request` と `response`。

`HttpServletRequest` はブラウザからサーバーへ送られてきたHTTPリクエストを表している。

そのため、

```java
HttpSession session = request.getSession(true);
```

とすることで、そのリクエストに関連するSessionを取得できる。

一方、`HttpServletResponse` はサーバーからブラウザへ返すHTTPレスポンスを扱う。

```java
response.sendRedirect("/login");
```

では、ブラウザに `/login` へ再度アクセスさせている。

`AuthenticationException` には認証失敗に関する情報が入るが、今回の処理では直接使用していない。

### Sessionを使ってリダイレクト先へ値を渡す

ログイン失敗時は、

```java
session.setAttribute(
        "loginErrorMessage",
        "login.error"
);
```

としてSessionへメッセージを保存してから、

```java
response.sendRedirect("/login");
```

でログイン画面へ戻している。

その後、`LoginController` で、

```java
String loginErrorMessage =
        (String) session.getAttribute("loginErrorMessage");

if (loginErrorMessage != null) {
    model.addAttribute("loginErrorMessage", loginErrorMessage);
    session.removeAttribute("loginErrorMessage");
}
```

とすることで、

```text
認証失敗
    ↓
Sessionにメッセージを保存
    ↓
/loginへリダイレクト
    ↓
LoginControllerがSessionから取得
    ↓
Modelへ移す
    ↓
login.htmlで表示
```

という流れを作ることができた。

---

## 3. messages.propertiesのキーをSessionに保存する

当初は、

```java
session.setAttribute(
        "loginErrorMessage",
        "ログインIDまたはパスワードが正しくありません。"
);
```

のように直接日本語を保存する方法も考えた。

しかし、このアプリでは表示言語を切り替えられるため、メッセージも `messages.properties` で管理したい。

そこで、文章そのものではなく、

```java
session.setAttribute(
        "loginErrorMessage",
        "login.error"
);
```

とメッセージキーをSessionへ保存することにした。

### `#{__${loginErrorMessage}__}` が必要になる理由

ここで分かりにくかったのが、

```html
th:text="#{__${loginErrorMessage}__}"
```

という書き方。

通常の、

```html
#{login.error}
```

であれば、`messages.properties` の `login.error` をそのまま取得できる。

しかし今回は、メッセージキー自体が、

```text
${loginErrorMessage}
```

という変数の中に入っている。

そのため `__...__` を使って中身を先に展開する。

```text
${loginErrorMessage}
    ↓
login.error
    ↓
#{login.error}
    ↓
messages.propertiesから文章を取得
```

`__...__` はThymeleafのプリプロセッシングで、変数の中に入っている値をメッセージキーとして使用したい場合に使えると理解した。

---

## 4. Sessionの値を削除するタイミング

ログイン失敗メッセージをModelへ追加した直後に、

```java
session.removeAttribute("loginErrorMessage");
```

としている。

実際にログインに失敗するとエラーメッセージが表示されるが、その状態でF5を押すとメッセージが消えた。

これは、

```text
1回目のGET /login
    ↓
SessionにloginErrorMessageがある
    ↓
Modelへ追加
    ↓
Sessionから削除
    ↓
エラー表示
```

となった後、F5による2回目のリクエストでは、すでにSessionに `loginErrorMessage` が存在しないため。

Sessionに保存したままにすると、関係のないタイミングでもログインエラーが表示され続ける可能性がある。

一度だけ表示したいメッセージは、使用後にSessionから削除するという考え方を実際の動作から確認できた。

---

## 5. ログアウト処理はログイン失敗処理と同じ構造を利用できる

ログアウト成功時には、

```java
.logoutSuccessHandler(new LogoutSuccessHandler() {
```

を使用した。

最初は別の仕組みとして考えていたが、メッセージを表示するまでの流れはログイン失敗時とほぼ同じだった。

```text
ログアウト成功
    ↓
Sessionにlogout.successを保存
    ↓
/へリダイレクト
    ↓
HomeControllerがSessionから取得
    ↓
Modelへ追加
    ↓
Sessionから削除
    ↓
home.htmlで表示
```

ログイン失敗時の、

```text
AuthenticationFailureHandler
    ↓
LoginController
```

に対して、ログアウト成功時は、

```text
LogoutSuccessHandler
    ↓
HomeController
```

となり、Sessionを使って一度だけメッセージを表示する考え方は共通していた。

ただし、**ログアウト時のSessionの扱いはログイン失敗時と異なる**。Spring Securityの標準的なログアウト処理では既存のSessionが無効化されるため、`LogoutSuccessHandler`内で`request.getSession(true)`を呼び出すと、必要に応じて新しいSessionが作られる。今回の`logout.success`は、その新しいSessionに保存してトップ画面へ渡している。

一つの処理の流れを理解すると、似た機能にもその考え方を流用できることが分かった。

---

## 6. Spring Securityが保持している認証情報を画面から利用できる

ヘッダーにログイン中のユーザーIDを表示するとき、当初はAOPなどを使ってユーザーIDを取得し、Modelへ追加する必要があるのかと考えた。

しかし、ログイン後はSpring Securityが認証情報を保持している。

そのため、

```html
sec:authentication="name"
```

とするだけで、現在ログインしているユーザーのログインIDを表示できた。今回の`UserDetails`では`loginId`をユーザー名として設定しているため、`name`が示すのはDBの主キーではなく`loginId`である。

また、

```html
sec:authorize="isAuthenticated()"
```

```html
sec:authorize="isAnonymous()"
```

を使えば、

- ログイン中
- 未ログイン

によってHTMLの表示を切り替えることもできる。

なお、`isAuthenticated()`にはRemember Meによって復元された認証も含まれる。Remember Meではない認証に限定したい場合は`isFullyAuthenticated()`を使う。

そのため、ヘッダー表示のためだけにControllerやAOPから、

```java
model.addAttribute("loginUser", ...);
```

のような処理を追加する必要はない。

すでにSpring Securityが持っている情報は、改めて自分で取得・保持しないという判断ができた。

---

## 7. ログイン・ログアウトでHTML要素を使い分ける

ヘッダーへログイン・ログアウトを追加した際、ログアウトを、

```html
<form class="dropdown-item">
    <button>ログアウト</button>
</form>
```

のようにすると、ログインリンクなどと見た目が揃わず、ボタンらしい表示になってしまった。

そこで、

```html
<form th:action="@{/logout}" method="post">
    <button type="submit"
            class="dropdown-item">
        ログアウト
    </button>
</form>
```

とし、`form` ではなく `button` に `dropdown-item` を指定した。

ログインは画面遷移なので `<a>` を使用できるが、今回のSpring Securityの設定ではログアウトをPOSTで送信するため、`<form>`と`<button>`を使用した。

同じドロップダウン内の項目でも、処理内容によってHTML要素は異なるため、Bootstrapのclassを付ける位置を調整して見た目を揃える必要があった。

---

## 8. Remember-Me認証で確認したこと

Remember-Me認証では、

```java
.rememberMe(remember -> remember
        .rememberMeParameter("remember-me")
        .tokenValiditySeconds(3600)
)
```

を設定した。

ここで最初に `rememberParameter()` と書いたところ受け付けられず、正しくは、

```java
rememberMeParameter()
```

だった。

HTML側の、

```html
name="remember-me"
```

と、

```java
.rememberMeParameter("remember-me")
```

を対応させることで、チェックされたかどうかをSpring Securityが判断できる。

実際にRemember-Meを有効にしてログインすると、開発者ツールのCookieに、

- `JSESSIONID`
- `remember-me`

の2つが存在することを確認できた。

`JSESSIONID`はサーバー側のHTTP Sessionを識別するCookieで、`remember-me`はSessionが切れた後に認証状態を復元するためのCookieである。両者は役割が異なり、Remember Meで再認証されても、以前のSessionに保存していた`languageVariant`や`pronunciationType`などの属性まで自動的に復元されるわけではない。この問題への対応は、後日007の実装ログ末尾に追加修正として記録した。

さらにSessionタイムアウト後にF5を押しても、ログイン中だけ表示されるボタンやリンクが残っていた。

単に設定を追加して終わりではなく、CookieとSessionタイムアウト後の画面の両方からRemember-Me認証が機能していることを確認できた。

---

## 9. CSRFトークンは必ず手動で追加するわけではない

Spring SecurityではCSRF対策がデフォルトで有効になっている。

当初は、POSTするHTMLすべてに、

```html
<input type="hidden"
       th:name="${_csrf.parameterName}"
       th:value="${_csrf.token}" />
```

を追加する必要があると考えていた。

しかし、Thymeleafで、

```html
th:action="@{/...}"
```

を使用したSpring MVC・ThymeleafとSpring Securityの通常の連携環境では、フォームのPOST時に必要なCSRFトークンがhiddenパラメーターとして自動挿入される。これは`th:action`単独の機能ではなく、Spring MVCのフォーム処理とSpring Securityの連携によるもの。

今回POSTしている、

- 新規登録
- ログイン
- ログアウト

はいずれも `th:action` を使用しているため、CSRFトークンを手動で記述する必要はなかった。

一方、通常の`action`属性だけを記述したフォームでは、この連携による自動挿入を前提にできない。JavaScriptの`fetch()`などでPOSTする場合も、別途CSRFトークンを送る必要がある。

そのため、Spring SecurityとThymeleafを組み合わせる場合は、単にURLを生成するためだけでなく、CSRF対策の面からも基本的に `th:action` を使用するようにしたい。

---

## 実装を終えて

今回特に理解に時間がかかったのは、Spring Securityのコードは自分のコードから直接呼び出していないメソッドが多いという点だった。

`loadUserByUsername()`、`onAuthenticationFailure()`、`onLogoutSuccess()` などは、自分で呼び出すのではなく、条件を満たしたときにSpring Securityから呼び出される。

そのためコードを上から読むだけでは、

- これは誰が呼んでいるのか
- この `return` は誰に返しているのか
- この引数はどこから来たのか

が分かりにくかった。

今回の実装では、それぞれを単独で覚えるのではなく、

```text
Spring Securityが処理を開始
    ↓
必要なタイミングで自作処理を呼び出す
    ↓
自作処理から必要な情報を返す
    ↓
Spring Securityがその後の処理を続ける
```

という視点で整理すると理解しやすかった。

また、ログイン失敗処理を理解したことで、ログアウト成功処理でも同じSessionを利用したメッセージ受け渡しを応用できた。

ヘッダーのユーザー情報についても、新たにAOPなどを追加するのではなくSpring Securityがすでに保持している認証情報を利用できた。

CSRFについても、今回のSpring MVC・ThymeleafとSpring Securityの連携では、`th:action`を使用したPOSTフォームにCSRFトークンが自動挿入されることが分かった。

今回の実装を通して、Spring Securityのようなフレームワークを使用する場合は、すぐに独自処理を追加するのではなく、フレームワーク側がすでに持っている機能や情報を利用できないか確認することが重要だと感じた。

新規登録とログインに必要な基本的な機能が揃ったため、次は一旦中断していた学習機能の実装に戻る。