# 023-01 アカウント凍結状態の認証処理への反映

## はじめに

Admin機能では、`users`テーブルの`accountLocked`を更新することで、ユーザーのアカウントを凍結・凍結解除できるようにした。しかし、この時点ではログイン認証に凍結状態が反映されておらず、凍結中のユーザーでもログインできた。

本章では、DBに保存した凍結状態をSpring Securityの認証処理へ反映し、凍結中のログインを拒否する。また、通常の認証失敗とアカウント凍結を区別してエラーメッセージを表示する。

```bash
git commit -m "feat: block login for locked accounts"
```

## 1. Spring Securityのアカウントロック判定を利用する

独自のログイン拒否処理を`SecurityConfig`に追加するのではなく、`UserDetailsServiceImpl`が返す`UserDetails`に凍結状態を設定する方針とした。

Spring Securityの`UserDetails`には、アカウントがロックされていないかを示す`accountNonLocked`がある。これを`false`に設定すると、標準の認証処理でロック状態を検出できる。

### UserDetailsServiceImplの変更

**変更前**

```java
UserDetails userDetails = new User(
        loginUser.getLoginId(),
        loginUser.getPassword(),
        authorities
);
```

従来のコンストラクタでは、DBの凍結状態を指定していなかった。

**変更後**

```java
UserDetails userDetails = new User(
        loginUser.getLoginId(),
        loginUser.getPassword(),
        true,                           // enabled
        true,                           // accountNonExpired
        true,                           // credentialsNonExpired
        !loginUser.isAccountLocked(),   // accountNonLocked
        authorities
);

return userDetails;
```

変更後のコンストラクタでは、ユーザー名・パスワード・権限に加え、アカウントの状態を指定できる。

| 引数 | 意味 | 今回の設定 |
| --- | --- | --- |
| `username` | ユーザー名 | ログインID |
| `password` | 暗号化済みパスワード | DBから取得した値 |
| `enabled` | アカウントが有効か | `true` |
| `accountNonExpired` | アカウントの有効期限が切れていないか | `true` |
| `credentialsNonExpired` | 認証情報の有効期限が切れていないか | `true` |
| `accountNonLocked` | アカウントがロックされていないか | `!loginUser.isAccountLocked()` |
| `authorities` | ユーザーの権限 | 取得済みの権限情報 |

### DBとSpring Securityで真偽値の意味が逆になる

DBの`accountLocked`は`true`が「凍結中」を意味する。一方、Spring Securityの`accountNonLocked`は`true`が「ロックされていない」を意味する。そのため、`!`演算子で値を反転する。

| DBの`accountLocked` | `accountNonLocked` | 認証上の状態 |
| --- | --- | --- |
| `false` | `true` | ロックされていない |
| `true` | `false` | ロック中 |

この対応により、アカウント凍結を判定する独自の例外送出処理を追加せずに、Spring Securityの標準機能を利用できる。

## 2. 認証失敗時のメッセージを分岐する

従来の`AuthenticationFailureHandler`は、認証失敗の原因にかかわらず`login.error`をセッションに保存していた。そこで、`LockedException`の場合だけ専用のメッセージキーを保存するよう変更した。

### SecurityConfigのAuthenticationFailureHandler

**変更前**

```java
@Override
public void onAuthenticationFailure(HttpServletRequest request,
                                    HttpServletResponse response,
                                    AuthenticationException exception)
        throws IOException {

    HttpSession session = request.getSession(true);
    session.setAttribute("loginErrorMessage", "login.error");

    response.sendRedirect("/login");
}
```

**変更後**

```java
@Override
public void onAuthenticationFailure(HttpServletRequest request,
                                    HttpServletResponse response,
                                    AuthenticationException exception)
        throws IOException {

    HttpSession session = request.getSession(true);

    if (exception instanceof LockedException) {
        session.setAttribute("loginErrorMessage", "login.error.locked");
    } else {
        session.setAttribute("loginErrorMessage", "login.error");
    }

    response.sendRedirect("/login");
}
```

`LockedException`は、アカウントのロックによって認証が拒否された場合に使用されるSpring Securityの例外である。`instanceof`で例外の種類を判定することで、凍結時のみ専用のメッセージを表示し、それ以外の認証失敗には従来のメッセージを使用する。

### messages.properties

```properties
login.error.locked=このアカウントは凍結されています
```

## 3. 動作確認

### 凍結中のログイン

Admin画面で`mawsonlakes_user`が凍結中であることを確認した。

![](../../images/0023-01.png)

凍結中のユーザーでログインを試みた。

![](../../images/0023-02.png)

ログインは拒否され、アカウント凍結を通知するメッセージが表示された。

![](../../images/0023-03.png)

### 凍結解除後のログイン

Admin画面から同じユーザーの凍結を解除した。

![](../../images/0023-04.png)

再度ログインを試みた。

![](../../images/0023-02.png)

凍結解除後はログインに成功し、Home画面へ遷移した。

![](../../images/0023-05.png)

## まとめ

Admin画面で管理する`accountLocked`を、`UserDetailsServiceImpl`を通じてSpring Securityの`accountNonLocked`へ反映した。これにより、凍結中のアカウントのログインを標準の認証処理で拒否できるようになった。

また、`AuthenticationFailureHandler`で`LockedException`を判定し、凍結による認証失敗を通常のログインエラーと区別して表示できるようにした。

今回の修正では、**管理画面で変更できる状態を、実際にその状態を利用する認証処理まで反映すること**が重要だった。
