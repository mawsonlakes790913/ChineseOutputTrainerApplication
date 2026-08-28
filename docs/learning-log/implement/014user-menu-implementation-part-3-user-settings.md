# 014 ユーザーメニューの実装その3 学習ログ

今回はユーザーメニューその3として、主に以下の機能を実装した。

* 学習対象言語の設定
* 表示する発音記号の設定
* ユーザー設定のDBへの永続化
* ログイン時のユーザー設定のSessionへの復元
* 設定画面
* 学習対象言語変更後の遷移先の調整

今回の実装では、これまでSessionだけで管理していた`LanguageVariant`と`PronunciationType`を、ログインユーザーについては`users`テーブルにも保存するようにした。

特に重要だったのは、DBとSessionのどちらか一方だけを使うのではなく、

```text
DB
    → ユーザー設定を永続的に保存する

Session
    → 現在の画面表示や学習処理で使用する設定を保持する
```

と役割を分けた点である。

# 1. 各実装において工夫した点

## 1-1. ユーザー設定をSessionだけでなくDBにも保存するようにした

これまでは学習対象言語と表示発音記号をSessionで管理していた。

例えば、学習対象言語を國語へ変更すると、

```java
session.setAttribute(
        "languageVariant",
        LanguageVariant.TAIWAN
);
```

によって、そのSessionが有効な間は國語を利用できる。

しかしSessionは永続的な保存場所ではない。

ログアウトすると、

```text
Session
    ↓
破棄
    ↓
languageVariant = TAIWAN
も失われる
```

ため、再ログインするとデフォルトの`MAINLAND`へ戻ってしまう。

そこで`Users`に、

```java
@Enumerated(EnumType.STRING)
@Column(
    name = "language_variant",
    nullable = false,
    length = 20
)
private LanguageVariant languageVariant =
        LanguageVariant.MAINLAND;

@Enumerated(EnumType.STRING)
@Column(
    name = "pronunciation_type",
    nullable = false,
    length = 20
)
private PronunciationType pronunciationType =
        PronunciationType.PINYIN;
```

を追加した。

これによって、

```text
users
├─ language_variant
└─ pronunciation_type
```

としてユーザーごとの設定をDBへ保存できる。

設定変更時には、

```java
user.setLanguageVariant(languageVariant);
userRepository.save(user);
```

または、

```java
user.setPronunciationType(pronunciationType);
userRepository.save(user);
```

としてDBを更新する。

一方、Sessionへの保存もそのまま残した。

```java
session.setAttribute(
        "languageVariant",
        languageVariant
);

session.setAttribute(
        "pronunciationType",
        pronunciationType
);
```

その結果、

```text
DB
    → ログアウトしても残る設定

Session
    → 現在のログイン中に利用する設定
```

という役割分担にした。

既存の画面ではすでに、

```html
${session.languageVariant}
```

などを利用しているため、DBへ保存するようになったからといってSessionを廃止するのではなく、それぞれ別の役割を持たせる方が既存実装も活用できると考えた。

## 1-2. 既存ユーザーがいるため、DBカラムを段階的に追加した

`Users`へ最初から、

```java
@Column(
    name = "language_variant",
    nullable = false
)
```

のようなフィールドを追加すると、すでに存在しているユーザーにはその値がない。

そこでDBでは最初に、

```sql
ALTER TABLE users
ADD COLUMN language_variant VARCHAR(20);

ALTER TABLE users
ADD COLUMN pronunciation_type VARCHAR(20);
```

として、`NOT NULL`を付けずにカラムを追加した。

その後、

```sql
UPDATE users
SET language_variant = 'MAINLAND',
    pronunciation_type = 'PINYIN';
```

として既存ユーザーへ初期値を設定した。

値が入ったことを確認してから、

```sql
ALTER TABLE users
ALTER COLUMN language_variant SET NOT NULL;

ALTER TABLE users
ALTER COLUMN pronunciation_type SET NOT NULL;
```

として`NOT NULL`制約を追加した。

つまり、

```text
① nullableなカラムを追加
        ↓
② 既存データへ値を設定
        ↓
③ NOT NULLを追加
```

という順番にした。

一方、Java側では、

```java
private LanguageVariant languageVariant =
        LanguageVariant.MAINLAND;

private PronunciationType pronunciationType =
        PronunciationType.PINYIN;
```

としている。

これは既存ユーザーを修正するためではなく、これから新しく作成される`Users`の初期値として利用するためである。

## 1-3. ログインユーザーとゲストで設定の保存方法を分けた

`LanguageVariantController`では、

```java
if (loginUser != null) {
    userAccountService.updateLanguageVariant(
            loginUser.getUsername(),
            languageVariant,
            locale
    );
}
```

としている。

`@AuthenticationPrincipal`で取得した`loginUser`が存在する場合だけDBを更新する。

その後、

```java
session.setAttribute(
        "languageVariant",
        languageVariant
);
```

はログイン状態に関係なく実行する。

これによって、

```text
ログインユーザー
    ↓
DBへ保存
+
Sessionへ保存
```

```text
ゲスト
    ↓
Sessionだけに保存
```

という使い分けができる。

ゲストについてはユーザーを識別する`users`レコードが存在しないため永続化する必要はなく、そのSessionが有効な間だけ設定を利用できればよい。

一方、ログインユーザーについてはユーザーごとの設定としてDBへ保存することで、再ログイン後にも同じ設定を利用できる。

## 1-4. 学習対象言語を変更した場合だけ中断中の問題を破棄するようにした

学習対象言語を変更した場合は、

```java
session.removeAttribute("practiceQuestions");
session.removeAttribute("practiceCurrentPage");
```

として、中断中の通常学習データを削除している。

例えば、

```text
普通話の問題を50問取得
        ↓
practiceQuestionsへ保存
        ↓
途中で國語へ変更
```

したにもかかわらず`practiceQuestions`を残してしまうと、設定上は國語なのに、Sessionには普通話用に取得した問題が残る可能性がある。

そのため`LanguageVariant`の変更では、中断中の問題を破棄するようにした。

一方、`PronunciationTypeController`では削除していない。

```java
session.setAttribute(
        "pronunciationType",
        pronunciationType
);
```

だけを行っている。

拼音から注音へ変更しても、

```text
問題そのもの
    → 同じ

発音記号の表示方法
    → 変わる
```

だけだからである。

このように、設定値を変更したときにSessionをすべて初期化するのではなく、**その設定変更によって影響を受けるデータだけを破棄する**ようにした。

## 1-5. 設定変更後の戻り先を`redirect`パラメータで一般化した

学習対象言語の変更では、以前は、

```java
if ("/practice/menu".equals(redirect)) {
    return "redirect:/practice/menu";
}
```

としていた。

これは、

```text
/practice/menu
```

だけを特別扱いする実装である。

しかし設定画面や復習メニューからも学習対象言語を変更するようになると、

```text
/practice/menu
/user/settings
/review/menu
```

など、戻り先が増える。

そこで、

```java
if (redirect != null) {
    return "redirect:" + redirect;
}

return "redirect:/";
```

へ変更した。

呼び出し側では、

```html
th:href="@{/language-variant(
    languageVariant='TAIWAN',
    redirect='/user/settings'
)}"
```

のように戻り先を指定できる。

ヘッダーでは、

```html
redirect=${languageVariantRedirect}
```

としているため、Controller側から、

```java
model.addAttribute(
        "languageVariantRedirect",
        "/review/menu"
);
```

のように設定すればよい。

これによって`LanguageVariantController`自身が、

```text
どの画面から呼ばれたか
```

を一つずつ判定する必要がなくなった。

呼び出し側が戻り先を指定し、`LanguageVariantController`は指定されたURLへ戻すという形にした。

## 1-6. ログイン成功時にDBの設定をSessionへ復元するようにした

DBへ設定を保存するだけでは、再ログイン時のSessionには設定が存在しない。

例えば、DBに、

```text
language_variant = TAIWAN
pronunciation_type = ZHUYIN
```

が保存されていても、ログイン後のSessionが、

```text
languageVariant = null
pronunciationType = null
```

のままでは、画面側でその設定を利用できない。

そこでログイン成功時に、

```java
Users user =
        userAccountService.getUserOne(
                authentication.getName()
        );

LanguageVariant languageVariant =
        user.getLanguageVariant();

PronunciationType pronunciationType =
        user.getPronunciationType();

HttpSession session = request.getSession();

session.setAttribute(
        "languageVariant",
        languageVariant
);

session.setAttribute(
        "pronunciationType",
        pronunciationType
);
```

を実行する`LoginSuccessHandler`を作成した。

これによって、

```text
ログイン成功
    ↓
authentication.getName()
    ↓
loginIdを取得
    ↓
usersを取得
    ↓
DBのlanguageVariantを取得
DBのpronunciationTypeを取得
    ↓
Sessionへ保存
```

という流れになる。

そのため、ログアウトによってSessionが破棄されても、

```text
DB
    → 設定は残る
```

ので、次回ログイン時に再びSessionへ復元できる。

## 1-7. `SavedRequestAwareAuthenticationSuccessHandler`を継承して既存のログイン後遷移を維持した

最初は`AuthenticationSuccessHandler`を使って、

```java
@Override
public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication)
        throws IOException, ServletException {

    // Sessionへ設定を保存

    response.sendRedirect("/");
}
```

とすることを考えた。

`AuthenticationSuccessHandler`は、ログイン成功後の処理を定義するためのインターフェースである。

しかし、

```java
response.sendRedirect("/");
```

とすると、ログイン後は常に`/`へ移動してしまう。

このアプリケーションではこれまで、

```java
.defaultSuccessUrl("/", false)
```

としていた。

第2引数が`false`なので、ログイン前にアクセスしようとしていたページが存在する場合は、そちらへの遷移が優先される。

例えば、

```text
未ログイン
    ↓
/user/settingsへアクセス
    ↓
ログイン画面へ移動
    ↓
ログイン成功
    ↓
/user/settingsへ戻る
```

という動作になる。

今回変更したいのは、

```text
ログイン後の遷移方法
```

ではなく、

```text
従来のログイン後処理
        +
DBのユーザー設定をSessionへ保存
```

である。

そこで、

```java
public class LoginSuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler
```

として、Spring Securityが用意している`SavedRequestAwareAuthenticationSuccessHandler`を継承した。

独自処理を実行した後に、

```java
super.onAuthenticationSuccess(
        request,
        response,
        authentication
);
```

を呼び出す。

これによって、

```text
LoginSuccessHandler
    ↓
DBからユーザー設定を取得
    ↓
Sessionへ保存
    ↓
super.onAuthenticationSuccess(...)
    ↓
Spring Security既存のログイン成功後処理
```

という流れにした。

既存のログイン後遷移を自分で再実装せず、必要な処理だけを追加できた。

## 1-8. `defaultTargetUrl`はSavedRequestがない場合の遷移先として設定した

`LoginSuccessHandler`のコンストラクタでは、

```java
public LoginSuccessHandler(
        UserAccountService userAccountService) {

    this.userAccountService =
            userAccountService;

    super.setDefaultTargetUrl("/");
}
```

としている。

最初は、

```java
super.setDefaultTargetUrl("/");
```

によってログイン後は必ず`/`へ移動するようにも見えた。

しかし、`SavedRequestAwareAuthenticationSuccessHandler`ではSavedRequestが存在する場合はそちらが優先される。

例えば、

```text
/user/settingsへアクセス
        ↓
未ログイン
        ↓
ログイン画面
        ↓
ログイン成功
        ↓
SavedRequestあり
        ↓
/user/settingsへ戻る
```

となる。

一方、

```text
ログイン画面を直接開く
        ↓
ログイン成功
        ↓
SavedRequestなし
        ↓
defaultTargetUrl
        ↓
/
```

となる。

したがって、

```java
super.setDefaultTargetUrl("/");
```

は、**ログイン前に戻るべきページが存在しなかった場合のデフォルト遷移先を設定している**。

## 1-9. ログイン成功時にUsersを1回だけ取得するようにした

当初は、

```java
public LanguageVariant getLanguageVariant(
        String loginId) {

    Users user = getUserOne(loginId);

    return user.getLanguageVariant();
}
```

のような専用Serviceメソッドを追加することも考えた。

しかしログイン成功時には、

```text
languageVariant
pronunciationType
```

の両方が必要になる。

それぞれ専用メソッドを作るより、

```java
Users user =
        userAccountService.getUserOne(
                authentication.getName()
        );

LanguageVariant languageVariant =
        user.getLanguageVariant();

PronunciationType pronunciationType =
        user.getPronunciationType();
```

として、`Users`を一度取得して必要な設定を取り出せば十分である。

今回の処理ではユーザー情報そのものが必要なので、設定項目ごとにServiceメソッドを増やさないようにした。

## 1-10. 現在選択中の設定は設定画面から再選択できないようにした

設定画面では、

```html
th:classappend="${session.languageVariant != null
    && session.languageVariant.name() == 'MAINLAND'}
    ? ' disabled'"
```

のようにしている。

現在`MAINLAND`が選択されている場合は、

```html
disabled
```

クラスが追加される。

発音記号についても、

```html
th:classappend="${session.pronunciationType != null
    && session.pronunciationType.name() == 'PINYIN'}
    ? ' disabled'"
```

としている。

これによって、現在選択されている値が画面上でも分かりやすくなり、同じ設定をもう一度選択する操作も防いでいる。

さらにController側でも、

```java
if (languageVariant == current) {
    return redirect != null
            ? "redirect:" + redirect
            : "redirect:/";
}
```

```java
if (pronunciationType == current) {
    return "redirect:/user/settings";
}
```

としている。

そのため、

```text
画面
    → 現在値を選択できない

Controller
    → 同じ値が送られても更新しない
```

という二段階の処理になっている。

## 1-11. 循環依存は`PasswordEncoder`のBean定義を分離して解消した

`LoginSuccessHandler`を`SecurityConfig`へ追加したところ、Spring Boot起動時に循環依存が発生した。

依存関係は、

```text
SecurityConfig
    ↓
LoginSuccessHandler
    ↓
UserAccountService
    ↓
PasswordEncoder
    ↓
SecurityConfig
```

となっていた。

`SecurityConfig`は、

```java
private final LoginSuccessHandler loginSuccessHandler;
```

として`LoginSuccessHandler`を必要とする。

`LoginSuccessHandler`は、

```java
private final UserAccountService userAccountService;
```

を必要とする。

さらに`UserAccountService`は`PasswordEncoder`を必要としていたが、その`PasswordEncoder`のBean定義が`SecurityConfig`自身に存在していた。

そこで、

```java
@Configuration
public class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

という別のConfigurationクラスへBean定義を移動した。

その結果、

```text
SecurityConfig
    ↓
LoginSuccessHandler
    ↓
UserAccountService
    ↓
PasswordEncoder

PasswordEncoderConfig
    ↓
PasswordEncoder
```

となり、`UserAccountService`から`SecurityConfig`へ戻る依存がなくなった。

循環依存を許可する設定で回避するのではなく、Beanの配置を見直して依存関係そのものを単純化した。

# 2. 気づいた点・勉強になった点

## 2-1. DBとSessionは同じ「データを保存する場所」でも役割が異なる

今回最も重要だったのは、

```text
DB
```

と、

```text
Session
```

の役割の違いである。

Sessionへ、

```java
session.setAttribute(
        "languageVariant",
        LanguageVariant.TAIWAN
);
```

と保存すれば、そのSessionが存在する間は値を利用できる。

しかし、ログアウトなどでSessionが破棄されれば値も失われる。

一方、DBへ、

```text
language_variant = TAIWAN
```

として保存した値は、ログアウトしても残る。

そのため今回のような「ユーザーが選択した設定」については、

```text
DB
    → 永続的な設定

Session
    → 現在利用中の設定
```

と分けるのが適していることが分かった。

## 2-2. `HttpSession`は`onAuthenticationSuccess()`の引数へ自由に追加できない

Controllerでは、

```java
public String method(
        HttpSession session)
```

のように`HttpSession`を引数として受け取れるため、最初は`onAuthenticationSuccess()`にも、

```java
public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication,
        HttpSession session)
```

のように追加できると考えた。

しかし、

```java
@Override
```

するメソッドは、親クラスまたはインターフェースで定義されているメソッドのシグネチャに合わせる必要がある。

`onAuthenticationSuccess()`は、

```java
onAuthenticationSuccess(
    HttpServletRequest request,
    HttpServletResponse response,
    Authentication authentication
)
```

として定義されているため、勝手に4つ目の引数を追加すると別のメソッドになり、`@Override`できない。

そこで、

```java
HttpSession session =
        request.getSession();
```

としてSessionを取得した。

`HttpServletRequest`から現在のHTTPリクエストに関連するSessionを取得できることを確認できた。

## 2-3. `super.onAuthenticationSuccess()`によって親クラスの処理を利用できる

`LoginSuccessHandler`では最後に、

```java
super.onAuthenticationSuccess(
        request,
        response,
        authentication
);
```

を実行している。

ここでの`super`は親クラスである、

```text
SavedRequestAwareAuthenticationSuccessHandler
```

を指す。

したがって、このコードは親クラスが持っている`onAuthenticationSuccess()`を実行している。

処理の流れは、

```text
LoginSuccessHandler独自の処理
    ↓
Sessionへユーザー設定を保存
    ↓
super.onAuthenticationSuccess(...)
    ↓
親クラスのログイン成功後処理
    ↓
SavedRequestがあれば元のページ
なければdefaultTargetUrl
```

となる。

継承を使うことで、親クラスの既存処理を残しながら、その前に自分の処理を追加できることを具体的に確認できた。

## 2-4. `AuthenticationSuccessHandler`は使えないのではなく、今回は既存実装を持つクラスを継承した方が適していた

最初に検討した`AuthenticationSuccessHandler`でも、今回の処理自体は実装できる。

つまり、

```text
AuthenticationSuccessHandlerでは実現不可能
```

だから使わなかったわけではない。

`AuthenticationSuccessHandler`はインターフェースなので、ログイン成功後に、

```text
どこへ遷移するか
```

も含めて自分で実装できる。

しかし今回は、Spring Securityがすでに、

```text
SavedRequestがある
    → 元のページへ戻る

SavedRequestがない
    → デフォルトページへ移動
```

という必要な処理を提供している。

その処理を実装しているのが、

```text
SavedRequestAwareAuthenticationSuccessHandler
```

である。

そのため、

```text
既存機能を自分で再実装する
```

のではなく、

```text
既存機能を持つクラスを継承
        +
必要な処理だけ追加
```

という方法を選んだ。

今回、インターフェースを直接実装することだけがカスタマイズではなく、**目的に合った既存の実装クラスを継承する方法もある**ことを学んだ。

## 2-5. `.successHandler()`はログイン成功時に使用する処理をSpring Securityへ登録する設定である

`LoginSuccessHandler`を作成しただけでは、Spring Securityが自動的にそのクラスをログイン成功時に使用するわけではない。

SecurityConfigで、

```java
.successHandler(loginSuccessHandler)
```

と設定する必要がある。

また、

```java
private final LoginSuccessHandler loginSuccessHandler;
```

としてDIしている。

これによって、

```text
ログイン成功
    ↓
Spring Security
    ↓
登録されているsuccessHandlerを確認
    ↓
LoginSuccessHandler
    ↓
onAuthenticationSuccess()
```

という流れになる。

今回、クラスを作ることと、そのクラスをフレームワークの処理へ登録することは別であることを確認できた。

## 2-6. 循環依存は実行時の無限ループではなくBean生成時の依存関係の問題である

今回発生した、

```text
SecurityConfig
    ↓
LoginSuccessHandler
    ↓
UserAccountService
    ↓
PasswordEncoder
    ↓
SecurityConfig
```

という循環依存は、メソッドが実行され続ける「無限ループ」とは異なる。

Spring Bootは起動時に必要なBeanを生成する。

しかし、

```text
SecurityConfigを作りたい
    ↓
LoginSuccessHandlerが必要

LoginSuccessHandlerを作りたい
    ↓
UserAccountServiceが必要

UserAccountServiceを作りたい
    ↓
PasswordEncoderが必要

PasswordEncoderを作りたい
    ↓
SecurityConfigが必要
```

となると、どのBeanから完成させればよいか決められない。

つまり問題が起きているのは、

```text
アプリケーション実行中
```

ではなく、

```text
SpringがBeanを生成している起動時
```

である。

今回`PasswordEncoderConfig`を分離したことで、Beanの配置も依存関係に影響することを具体的に確認できた。

## 2-7. `redirect`パラメータを使うことで遷移先を呼び出し側から指定できる

今回の`LanguageVariantController`では、

```java
@RequestParam(required = false)
String redirect
```

を受け取っている。

例えば、

```text
/language-variant
?languageVariant=TAIWAN
&redirect=/review/menu
```

というリクエストであれば、

```java
redirect
```

には、

```text
/review/menu
```

が入る。

そして、

```java
if (redirect != null) {
    return "redirect:" + redirect;
}
```

によって、

```text
redirect:/review/menu
```

を返す。

この仕組みによって、設定変更を行うController自身が戻り先を固定するのではなく、呼び出し側が戻り先を指定できる。

今回、同じ処理を複数の画面から利用するときには、

```text
処理を行う側
    → 共通処理を担当

呼び出す側
    → その画面固有の情報を渡す
```

と役割を分けることで、特定の画面に依存しにくい実装にできることを確認できた。

# 3. 今回の実装を通して

今回の設定機能では、単純に設定画面を追加するだけではなく、

```text
設定変更
    ↓
DBへ永続化
    ↓
Sessionへ反映
    ↓
ログアウト
    ↓
Session破棄
    ↓
再ログイン
    ↓
DBから設定を取得
    ↓
Sessionへ復元
```

という一連の流れを実装した。

特に、

```text
DB
    → 永続的なユーザー設定

Session
    → 現在利用中の設定

Authentication
    → 現在認証されているユーザー

SavedRequestAwareAuthenticationSuccessHandler
    → ログイン成功後の既存の遷移処理
```

のように、それぞれが別の役割を持っていることを整理できた。

また、`SavedRequestAwareAuthenticationSuccessHandler`を継承して既存のログイン処理を活用したことや、循環依存をBean構成の見直しによって解消したことで、Spring SecurityやDIについてもこれまでより具体的に理解できた。
