# 013 ユーザーメニューの実装その2 学習ログ

今回はユーザーメニューその2として、主に以下の機能を実装した。

- プロフィール画面
- ユーザーID変更
- パスワード変更
- アカウント削除
- ユーザーID・パスワード変更フォームのUI改善

今回の実装では、単純に`users`テーブルの情報を更新・削除するだけではなく、Spring Securityの認証状態やSession、バリデーション、関連テーブルなども考慮する必要があった。

特に、アカウント情報はDB上のユーザー情報だけで完結しているわけではないという点が重要だった。


# 1. 各実装において工夫した点

## 1-1. ユーザーID変更後は再ログインさせるようにした

ユーザーID変更では、DB上の`loginId`を変更するだけで処理を終了させず、変更後に一度ログアウト状態にして、新しいユーザーIDでログインし直してもらうようにした。

ユーザーIDの変更処理自体は、

```java
@Transactional
public void updateLoginId(String currentLoginId, String newLoginId) {

    Users user = getUserOne(currentLoginId);

    if (user == null) {
        throw new IllegalArgumentException(
                "ユーザーが存在しません"
        );
    }

    user.setLoginId(newLoginId);

    userRepository.save(user);
}
```

のようにDB上の`loginId`を書き換えることで実現できる。

しかし、ログイン中のユーザーについてはSpring Securityも認証情報を保持している。

そのため、

```text
DB
    → 新しいloginId

Spring Security
    → 変更前のloginId
```

という状態が残る可能性がある。

そこで変更成功後に、

```java
SecurityContextHolder.clearContext();

session.removeAttribute(
    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
);
```

として認証情報を削除し、

```java
return "redirect:/login";
```

でログイン画面へ戻すようにした。

これによって、DB上のユーザーIDを変更した後は、新しいユーザーIDで改めて認証するという分かりやすい流れにした。


## 1-2. Session全体ではなくSpring Securityの認証情報だけを削除した

ユーザーID・パスワード変更後にログアウト状態にする方法として、

```java
session.invalidate();
```

でSession全体を破棄する方法も考えられる。

しかし、このアプリケーションでは`SessionLocaleResolver`を使用して表示言語をSessionで管理している。

そのため、Session全体を破棄すると、認証情報だけでなくLocaleなどの情報まで失われる。

そこで、

```java
SecurityContextHolder.clearContext();

session.removeAttribute(
    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
);
```

として、Session自体は残し、Spring Securityの認証情報だけを削除するようにした。

つまり、

```text
Session
├─ Spring Securityの認証情報 → 削除
├─ Locale                  → 維持
└─ その他のSession情報      → 維持
```

という設計にした。

ユーザーIDやパスワードを変更したため再ログインは必要だが、それとは関係のない表示言語まで初期化する必要はないためである。


## 1-3. 変更完了メッセージはFlash Attributeでログイン画面へ渡した

ユーザーIDやパスワードを変更すると再ログインが必要になる。

しかし、単純に、

```java
return "redirect:/login";
```

とするだけでは、ユーザーから見ると「なぜ突然ログイン画面へ戻ったのか」が分かりにくい。

そこで、

```java
redirectAttributes.addFlashAttribute(
        "messageKey",
        "user.password.changed"
);
```

のようにFlash Attributeを使用して変更完了メッセージを渡すようにした。

ログイン画面では、

```html
<div class="alert alert-success"
     th:if="${messageKey != null}"
     th:text="#{__${messageKey}__}">
</div>
```

として表示する。

また、Controllerから直接日本語の文章を渡すのではなく、

```text
user.password.changed
```

のようなメッセージキーを渡すことで、多言語化にも対応できるようにした。


## 1-4. パスワード変更では入力チェックと業務上のチェックを分けた

パスワード変更では複数のチェックが必要になる。

まず`EditPasswordForm`では、

```java
@PasswordMatch(
    passwordFieldName = "newPassword",
    passwordConfirmFieldName = "newPasswordConfirm"
)
@Data
public class EditPasswordForm {

    @NotBlank
    private String currentPassword;

    @NotBlank(message = "{signup.password.notBlank}")
    @Length(
        min = 8,
        max = 20,
        message = "{signup.password.length}"
    )
    @Pattern(
        regexp = "^[\\x21-\\x7E]+$",
        message = "{signup.password.pattern}"
    )
    private String newPassword;

    @NotBlank
    private String newPasswordConfirm;
}
```

として、

- 未入力ではないか
- 8～20文字か
- 使用可能な文字だけで構成されているか
- 新しいパスワードと確認用パスワードが一致しているか

をBean Validationで確認する。

一方、

- 現在のパスワードが本当に正しいか
- 新しいパスワードが現在のパスワードと同じではないか

については、DBに保存されている現在のパスワードとの比較が必要になる。

そのため、これらについてはServiceで、

```java
if (!passwordEncoder.matches(
        currentPassword,
        user.getPassword())) {

    throw new CurrentPasswordMismatchException(...);
}
```

```java
if (passwordEncoder.matches(
        newPassword,
        user.getPassword())) {

    throw new PasswordSameException(...);
}
```

として確認するようにした。

これによって、

```text
Bean Validation
    → 入力された値自体のチェック

Service
    → DB上の情報を必要とする業務上のチェック
```

と役割を分けた。


## 1-5. パスワード変更のエラーを独自例外で区別した

パスワード変更では、

```text
現在のパスワードが違う

新しいパスワードが現在のパスワードと同じ
```

という異なるエラーが発生する。

そこで、

```java
public class CurrentPasswordMismatchException
        extends RuntimeException {

    public CurrentPasswordMismatchException(String message) {
        super(message);
    }
}
```

```java
public class PasswordSameException
        extends RuntimeException {

    public PasswordSameException(String message) {
        super(message);
    }
}
```

という独自例外を用意した。

Serviceではそれぞれの原因に応じた例外を発生させる。

Controllerでは、

```java
catch (CurrentPasswordMismatchException e) {

    bindingResult.rejectValue(
            "currentPassword",
            "invalid",
            e.getMessage()
    );

    model.addAttribute("editPasswordForm", form);

    return getEditPassword(model, form);
}
```

```java
catch (PasswordSameException e) {

    bindingResult.rejectValue(
            "newPassword",
            "same",
            e.getMessage()
    );

    model.addAttribute("editPasswordForm", form);

    return getEditPassword(model, form);
}
```

として、それぞれ適切な入力欄へエラーを返すようにした。

すべてを`IllegalArgumentException`として扱うよりも、

```text
CurrentPasswordMismatchException
    → 現在のパスワードに関するエラー

PasswordSameException
    → 新しいパスワードに関するエラー
```

と例外の型自体に意味を持たせた方が、Controller側でも処理を分けやすい。


## 1-6. 退会時には関連データから先に削除するようにした

退会処理では、最初は`users`から対象ユーザーを削除すればよいと考えた。

しかし、このアプリケーションにはユーザーに紐づく、

```text
favorite
study_history
```

というデータも存在する。

特に`favorite`では、

```text
favorite.user_id
        ↓
users.id
```

という外部キーが設定されており、`favorite.user_id`は`users.id`を参照している。

例えば、

```text
users
----------------
id
10
 ↑
 │ 外部キーで参照
 │
favorite
----------------
user_id
10
```

という状態である。

DBでこの外部キーの設定を確認したところ、ユーザー削除時の動作は、

```text
NO ACTION
```

となっていた。

### `NO ACTION`とは

`NO ACTION`とは、**参照先のレコードが削除されたときに、DBが参照元のレコードを自動的に削除・変更しない設定**である。

今回の場合、

```text
favorite.user_id = 10
```

が、

```text
users.id = 10
```

を参照している。

この状態で先に、

```text
users.id = 10
```

を削除すると、

```text
favorite.user_id = 10
        ↓
users.id = 10 を参照している
        ↓
しかし users.id = 10 が存在しなくなる
```

という状態になってしまう。

これは外部キー制約に違反する。

`NO ACTION`では、`users`を削除したからといってDBが関連する`favorite`を自動的に削除してくれるわけではない。

そのため、

```text
usersを削除しようとする
        ↓
favoriteがそのusersをまだ参照している
        ↓
DBはfavoriteに対して何もしない
        ↓
外部キー制約に違反する
        ↓
usersの削除が拒否される
```

となる。

一方、外部キーに、

```text
ON DELETE CASCADE
```

が設定されていれば、

```text
usersを削除
        ↓
関連するfavoriteもDBが自動削除
        ↓
usersの削除完了
```

となる。

今回は`NO ACTION`なので、アプリケーション側で関連データを先に削除する必要がある。

そこで退会処理を、

```text
① favoriteを削除
        ↓
② study_historyを削除
        ↓
③ usersを削除
```

という順番にした。

Repositoryには、

```java
void deleteByFavoriteKeyUserId(Long userId);
```

```java
void deleteByStudyHistoryKeyUserId(Long userId);
```

を追加した。

Serviceでは、

```java
@Transactional
public void cancelMembership(String loginId) {

    // loginIdからユーザーを取得
    Users user = getUserOne(loginId);

    if (user == null) {
        throw new IllegalArgumentException(
                "ユーザーが存在しません"
        );
    }

    // DB上のユーザーIDを取得
    Long userId = user.getId();

    // ① お気に入りを削除
    favoriteRepository.deleteByFavoriteKeyUserId(userId);

    // ② 学習履歴を削除
    studyHistoryRepository.deleteByStudyHistoryKeyUserId(userId);

    // ③ ユーザーを削除
    userRepository.delete(user);

    log.info("退会完了 loginId={}", loginId);
}
```

とした。

また、退会処理では複数のテーブルに対してDELETEを実行するため、`@Transactional`を付けて一連の削除処理を1つのトランザクションとして扱うようにした。

これにより、途中で処理に失敗した場合に、一部のデータだけが削除された状態になることを防いでいる。


## 1-7. 退会後に認証状態も終了させるようにした

退会処理では、ServiceでDBからユーザーと関連データを削除するだけでなく、現在ログインしているユーザーの認証状態も終了させるようにした。

Controllerは以下のようにした。

```java
@PostMapping("/user/delete")
public String cancelMembership(
        @AuthenticationPrincipal UserDetails loginUser,
        HttpServletRequest request)
        throws ServletException {

    userAccountService.cancelMembership(
            loginUser.getUsername()
    );

    request.logout();

    return "redirect:/user/canceled";
}
```

### `HttpServletRequest`とは

```java
HttpServletRequest request
```

の`HttpServletRequest`は、**ブラウザなどのクライアントからサーバーへ送られてきたHTTPリクエストを、Javaから扱うためのオブジェクト**である。

今回であれば、ユーザーが退会ボタンを押すと、

```html
<form th:action="@{/user/delete}" method="post">
```

によって、ブラウザからサーバーへ、

```text
POST /user/delete
```

というHTTPリクエストが送られる。

Spring MVCはこのリクエストを受け取ると、そのHTTPリクエストを表す`HttpServletRequest`オブジェクトをControllerの引数として渡してくれる。

つまり、

```java
@PostMapping("/user/delete")
public String cancelMembership(
        ...
        HttpServletRequest request)
```

の`request`は、簡単に考えると、

```text
ユーザーが退会ボタンを押す
        ↓
ブラウザ
        ↓
POST /user/delete
        ↓
サーバー
        ↓
HttpServletRequest request
```

という、**今回の`/user/delete`へのHTTPリクエストをJava側から扱うためのもの**である。

`HttpServletRequest`からはリクエストに関するさまざまな情報や機能を利用できる。

今回利用したのが、

```java
request.logout();
```

である。


### `request.logout()`とは

```java
request.logout();
```

は、**現在のHTTPリクエストに関連付けられているログインユーザーをログアウトさせるためのServlet APIのメソッド**である。

今回、Serviceでは、

```java
userAccountService.cancelMembership(
        loginUser.getUsername()
);
```

によって、

```text
favorite
    ↓
study_history
    ↓
users
```

の順番でDB上のデータを削除している。

しかし、

```text
DBからusersのレコードを削除する
```

ことと、

```text
現在ログインしているユーザーをログアウトさせる
```

ことは別の処理である。

DBからユーザーを削除したからといって、それだけで現在の認証状態を終了させる処理を行ったことにはならない。

そこで、DB上の退会処理が完了した後に、

```java
request.logout();
```

を実行する。

処理の流れは、

```text
退会ボタンを押す
        ↓
POST /user/delete
        ↓
UserAccountService.cancelMembership()
        ↓
favoriteを削除
        ↓
study_historyを削除
        ↓
usersを削除
        ↓
request.logout()
        ↓
現在の認証状態を終了
        ↓
/user/canceledへリダイレクト
```

となる。

これによって、DB上からアカウントを削除するだけでなく、退会したユーザーをログアウト状態にしたうえで退会完了画面へ遷移させるようにした。


### `throws ServletException`とは

Controllerのメソッドには、

```java
throws ServletException
```

も付いている。

これは、

```java
request.logout();
```

が`ServletException`を発生させる可能性があるためである。

`ServletException`は、**ServletによるHTTPリクエストの処理中に問題が発生したことを表す例外**である。

`request.logout()`のメソッドは、呼び出し側から見ると概念的に、

```java
logout() throws ServletException
```

となっている。

`ServletException`は`RuntimeException`とは異なる検査例外なので、Javaではその例外を、

```text
try-catchで処理する
```

または、

```text
throwsで呼び出し元へ伝える
```

必要がある。

今回はController内で`try-catch`するのではなく、

```java
public String cancelMembership(...)
        throws ServletException {
```

として、メソッド自身では処理せず上位へ伝える形にしている。

したがって、

```java
HttpServletRequest request
```

と、

```java
throws ServletException
```

が追加されているのは、今回の退会処理で、

```java
request.logout();
```

を利用するためである。


### この実装にした理由

退会処理では、

```text
DB上のアカウントを削除する
```

だけではなく、

```text
現在の認証状態も終了させる
```

ところまでを一連の退会処理として扱う必要があると考えた。

そのため、

```java
userAccountService.cancelMembership(
        loginUser.getUsername()
);

request.logout();
```

として、

```text
DB上の退会処理
        +
認証上のログアウト処理
```

の両方を実行するようにした。

今回の実装を通して、**DBに保存されているユーザー情報と、Webアプリケーション上で現在ログインしているという認証状態は別のものであり、退会時には両方を適切に処理する必要がある**ことを確認できた。


## 1-8. 現在のユーザーIDと新しいユーザーIDの役割を分けた

ユーザーID変更フォームでは当初、

```java
if (form.getLoginId() == null) {
    Users user = getLoginUser(loginUser);
    form.setLoginId(user.getLoginId());
}
```

として、現在のユーザーIDをフォームへ設定していた。

しかしHTMLでは、

```html
<input type="text"
       id="loginId"
       th:field="*{loginId}">
```

としているため、`EditLoginIdForm.loginId`へ現在のユーザーIDを設定すると、新しいユーザーIDの入力欄にもその値が表示されてしまった。

そこで、

```text
currentLoginId
    → 現在のユーザーIDを表示するための値

editLoginIdForm.loginId
    → 新しいユーザーIDを入力するための値
```

と役割を分けた。

Controllerでは、

```java
model.addAttribute("editLoginIdForm", form);
model.addAttribute("currentLoginId", loginUser.getUsername());
```

とする。

これによって、

```html
<div class="form-control bg-light"
     th:text="${currentLoginId}">
</div>
```

には現在のユーザーIDを表示し、

```html
<input type="text"
       th:field="*{loginId}">
```

は空欄の状態から入力できるようにした。


## 1-9. 入力ルールをプレースホルダで分かるようにした

ユーザーID変更・パスワード変更フォームでは、入力欄だけを表示すると、

```text
ユーザーIDは何文字までだったか

パスワードは何文字必要だったか

どのような文字が使用できるか
```

をユーザーが覚えている必要がある。

そこで新規登録画面ですでに使用している、

```html
th:placeholder="#{signup.loginId.placeholder}"
```

```html
th:placeholder="#{signup.password.placeholder}"
```

```html
th:placeholder="#{signup.passwordConfirm.placeholder}"
```

を変更画面でも再利用した。

同じ入力ルールを使用しているため、変更画面専用のメッセージを新しく作るのではなく、既存の`messages.properties`の値を流用した。


# 2. 気づいた点・勉強になった点

## 2-1. DB上のユーザー情報とSpring Securityの認証情報は別物である

今回特に重要だったのは、

```text
DBのusers
```

と、

```text
Spring Securityが保持している認証情報
```

は同じものではないという点である。

DBの`loginId`をUPDATEしたからといって、現在ログイン中のSpring Securityの認証情報まで自動的に変更されるわけではない。

そのため、ユーザーIDのような認証に使われる情報を変更するときは、

```text
DBの更新
        +
現在の認証状態
```

の両方を考える必要がある。


## 2-2. `SecurityContext`は現在の認証情報を保持している

Spring Securityでは、現在ログインしているユーザーの情報を`SecurityContext`で管理している。

概念的には、

```text
SecurityContext
    ↓
Authentication
    ├─ Principal
    │    └─ ユーザー情報
    ├─ Authorities
    │    └─ ROLE_USERなど
    └─ 認証済みかどうか
```

という関係になっている。

そのため、

```java
SecurityContextHolder.clearContext();
```

は、現在の実行コンテキストが保持している認証情報をクリアする処理になる。


## 2-3. `SPRING_SECURITY_CONTEXT_KEY`はSession内の認証情報を示している

今回使用した、

```java
HttpSessionSecurityContextRepository
    .SPRING_SECURITY_CONTEXT_KEY
```

は、Spring SecurityがSessionへ`SecurityContext`を保存するときに使用する属性名を表す定数である。

そのため、

```java
session.removeAttribute(
    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
);
```

とすることで、Session全体を削除せずにSpring Securityの認証情報だけを削除できる。

今回の実装によって、

```text
HTTP Session
    ↓
その中の一部としてSpring Securityの認証情報が存在する
```

という関係を具体的に理解できた。


## 2-4. `rejectValue()`と`reject()`ではエラーの所属先が違う

Controllerでは、

```java
bindingResult.rejectValue(
        "currentPassword",
        "invalid",
        e.getMessage()
);
```

と、

```java
bindingResult.reject(
        "userNotFound",
        e.getMessage()
);
```

を使い分けた。

`rejectValue()`は、

```text
currentPassword
newPassword
loginId
```

など、特定のフィールドに対するエラーを登録する。

そのため、

```html
<div th:errors="*{currentPassword}">
</div>
```

のように、その入力欄の近くへエラーを表示できる。

一方、`reject()`は特定のフィールドに属さないフォーム全体のエラーとして登録する。

これがThymeleafでいうグローバルエラーになる。


## 2-5. Java側に`GlobalError`という独自クラスが必要なわけではない

HTMLでは、

```html
<div class="alert alert-danger"
     th:if="${#fields.hasGlobalErrors()}">

    <div th:each="error : ${#fields.globalErrors()}"
         th:text="${error}">
    </div>

</div>
```

としている。

最初はJava側に`GlobalError`のようなクラスが存在するのかと思ったが、そうではない。

例えば、

```java
bindingResult.reject(
        "userNotFound",
        e.getMessage()
);
```

とすると、特定フィールドに紐付かないエラーとして`BindingResult`へ登録される。

Thymeleafはそれを、

```html
#fields.globalErrors()
```

で取得している。

つまり「グローバルエラー」は自分で作ったJavaクラスの名前ではなく、**フォーム全体に対するエラーという分類**である。


## 2-6. バリデーションの実行順序によって表示されるエラーが変わる

パスワード変更Controllerでは、

```java
if (bindingResult.hasErrors()) {
    return getEditPassword(model, form);
}
```

をServiceより前に実行している。

そのため、

```text
現在のパスワードが間違っている
        +
新しいパスワードと確認用パスワードも違う
```

という状態では、まず`@PasswordMatch`などのBean Validationが実行される。

そこでエラーになれば、

```java
return getEditPassword(model, form);
```

によってServiceまで到達しない。

つまり、現在のパスワードが正しいかを確認する、

```java
passwordEncoder.matches(...)
```

はまだ実行されない。

今回、エラー処理では「何をチェックしているか」だけでなく、**どの順番でチェックしているかも画面の動作に影響する**ことに気づいた。


## 2-7. DBからユーザーを削除することとログアウトは別の処理である

退会時には、

```java
userAccountService.cancelMembership(
        loginUser.getUsername()
);
```

によってDB上のユーザーを削除した後、

```java
request.logout();
```

を実行している。

ここで使用している、

```java
HttpServletRequest request
```

は、今回でいえばブラウザから送られてきた、

```text
POST /user/delete
```

というHTTPリクエストをJava側から扱うためのオブジェクトである。

そして、

```java
request.logout();
```

によって、そのリクエストに関連する現在の認証状態をログアウトさせる。

つまり退会処理には、

```text
DB上のユーザーを削除する
        +
現在ログインしている状態を終了させる
```

という2つの処理が必要になる。


## 2-8. ログアウト後の遷移先は`permitAll()`にする必要がある

退会後は、

```java
request.logout();

return "redirect:/user/canceled";
```

としている。

この時点ではすでに未認証状態になっている。

そのため、`/user/canceled`がログイン必須ページのままだと、

```text
退会
    ↓
ログアウト
    ↓
/user/canceledへアクセス
    ↓
未認証なのでアクセスできない
```

となる。

そこで、

```java
.requestMatchers("/user/canceled").permitAll()
```

を追加した。

今回、SecurityConfigでは「そのURLを誰が見るのか」だけではなく、**そのURLへ遷移するときユーザーがどの認証状態になっているのかまで考える必要がある**ことに気づいた。


# 3. 今回の実装を振り返って

今回の実装では、

```text
プロフィール確認
        ↓
ユーザーID変更
        ↓
パスワード変更
        ↓
アカウント削除
```

という一連のアカウント管理機能を実装した。

実装前は、

```text
ユーザーID
    → UPDATE

パスワード
    → UPDATE

退会
    → DELETE
```

という比較的単純なCRUDになると思っていた。

しかし実際には、ユーザーIDを変更するとSpring Securityの認証情報との整合性を考える必要があり、パスワードを変更するとBean ValidationだけでなくDBに保存されたハッシュ化済みパスワードとの照合も必要になった。

さらに退会処理では、`users`だけを見るのではなく、`favorite`や`study_history`などの関連データと外部キー制約まで確認する必要があった。

特に今回、

```text
DB上のユーザー情報
Spring Securityの認証情報
HTTP Session
```

はそれぞれ関連しているものの、同じものではないという点を実際の実装を通して理解できたことは大きかった。

また、パスワード変更では、

```text
@Validated
    ↓
Bean Validation
    ↓
BindingResult
    ↓
Service
    ↓
業務チェック
    ↓
独自例外
    ↓
Controllerでcatch
    ↓
rejectValue() / reject()
    ↓
Thymeleafでエラー表示
```

という一連の流れを実装した。

これによって、これまで個別に使用してきたValidation、例外処理、`BindingResult`、Thymeleafのエラー表示がどのようにつながっているのかを整理できた。

退会処理についても、

```text
Spring SecurityからloginIdを取得
        ↓
Usersを取得
        ↓
DB上のusers.idを取得
        ↓
favoriteを削除
        ↓
study_historyを削除
        ↓
usersを削除
        ↓
ログアウト
        ↓
退会完了画面
```

という流れを実装したことで、認証に使用する`loginId`とDB上の主キー`id`の違い、Entityの関連、外部キー制約、Transaction、認証状態までを一連の処理として考えることができた。

今回の実装を通して、アカウント管理機能は単純なCRUDではなく、

- Spring Security
- SecurityContext
- HTTP Session
- Locale
- Bean Validation
- 独自バリデーション
- 独自例外
- BindingResult
- PasswordEncoder
- JPA
- 外部キー制約
- Transaction
- Thymeleaf

など、これまで学習してきた複数の技術を組み合わせる機能であることを実感した。


# 4. 次にやること

ユーザーメニューその3として、各種設定画面を実装する。