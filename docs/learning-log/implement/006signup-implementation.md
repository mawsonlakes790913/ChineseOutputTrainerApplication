# 006 サインアップ機能の実装

## 今回の学習内容

ユーザーがログインIDとパスワードを入力してアカウントを作成する、サインアップ機能を実装した。

サインアップ自体は一般的なWebアプリケーションでよく使われる機能であり、

```text
Form
↓
Controller
↓
Service
↓
Repository
↓
DB
```

という基本的な処理の流れや、ログインIDの重複チェック、Roleの設定、パスワードのハッシュ化などは、これまで学習した内容をそのまま応用できた。

一方、今回特に学習要素が大きかったのは、サインアップフォームのValidationだった。

通常の、

```java
@NotBlank
@Length
@Pattern
```

に加えて、パスワードと確認用パスワードという**複数のフィールドを比較する独自Validation**を実装した。

そのため、

```text
@Constraint
ConstraintValidator
initialize()
isValid()
BeanWrapper
```

など、普段のControllerやServiceの実装ではあまり使用しない仕組みを改めて確認することになった。

また、実装後に画面を確認することで、Validationメッセージ、placeholder、使用可能文字についても追加で改善した。

今回の学習ログでは、特に以下について整理する。

- `Role` をEnumで管理する理由
- `userId` ではなく `loginId` とした理由
- `@Validated` と `BindingResult` の関係
- 独自Validationアノテーションの仕組み
- `PasswordMatchValidator` で `null` の場合に `true` を返す理由
- `SignupForm` に直接依存せず `BeanWrapper` を使用した理由
- クラスレベルValidationとグローバルエラーの関係
- 実装後に気づいたValidation・UIの改善点

---

## 1. `Role`をEnumで管理する理由

今回、ユーザーの権限は以下のEnumで管理した。

```java
public enum Role {
    USER,
    ADMIN
}
```

`Users` Entityでは、

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private Role role;
```

としている。

Roleを単なる `String` として、

```java
user.setRole("USER");
```

とすることもできるが、文字列の場合、

```java
user.setRole("UESR");
```

のような、本来存在しないRoleも記述できてしまう。

Enumであれば、

```java
user.setRole(Role.USER);
```

のように定義済みの値だけを使用できる。

今回のように、

```text
USER
ADMIN
```

と**取り得る値があらかじめ限定されているデータはEnumで表現すると扱いやすい**。

また、

```java
@Enumerated(EnumType.STRING)
```

を指定することで、DBには `USER`、`ADMIN` というEnum名が文字列として保存される。

---

## 2. `userId`ではなく`loginId`とした理由

ブラウザ上ではユーザーに分かりやすいように「ユーザーID」と表示しているが、バックエンドでは、

```java
private String loginId;
```

という名前を使用している。

これは `Users` Entityに、すでにDB上の主キーが存在するためである。

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

つまり、

```text
id
→ DB内部でユーザーを識別する主キー

loginId
→ ユーザーがログインするときに入力するID
```

という役割の違いがある。

ログイン用のIDまで `userId` とすると、コード上で「DB上のID」と「ユーザーがログインに使用するID」のどちらを指しているのか分かりにくくなる。

そのため、画面上では「ユーザーID」と表示しつつ、バックエンドでは役割が明確になる `loginId` という名前を使用した。

**画面上の表示名とJava側の変数名は必ずしも一致させる必要はなく、バックエンドではそのデータの役割が分かる名前を付ける。**

---

## 3. `@Validated`と`BindingResult`の関係

最初の `SignupForm` には、

```java
@NotBlank
@Length(min = 8, max = 20)
@Pattern(regexp = "^[a-zA-Z0-9]+$")
```

などのValidationをすでに設定していた。

しかし、Controller側では、

```java
@ModelAttribute SignupForm form
```

となっており、Validationを実行していなかった。

また、

```java
BindingResult bindingResult
```

が存在していたため、一見するとValidationが行われているようにも見えるが、`BindingResult` 自体がValidationを実行するわけではない。

そこで、

```java
@ModelAttribute @Validated SignupForm form
```

と変更した。

さらに、

```java
if (bindingResult.hasErrors()) {
    return getSignup(form);
}
```

を追加する。

それぞれの役割は、

```text
@NotBlank / @Length / @Pattern
    ↓
何を検証するか定義する

@Validated
    ↓
Validationを実行する

BindingResult
    ↓
Validation結果を受け取る

hasErrors()
    ↓
エラーが存在するか確認する
```

となる。

つまり、**FormにValidationアノテーションを書くだけでは不十分で、Controller側でValidationを実行し、その結果を確認する必要がある**。

また、`BindingResult` にはValidationによって発生したエラーだけでなく、Controllerからエラーを追加することもできる。

今回のログインID重複チェックでは、

```java
bindingResult.rejectValue(
        "loginId",
        "duplicate",
        e.getMessage());
```

として、Serviceで発生した重複エラーを `loginId` のフィールドエラーとして追加している。

---

## 4. 複数フィールドを検証する独自Validation

通常の、

```java
@NotBlank
@Length
@Pattern
```

は、基本的に1つのフィールドを対象とする。

例えば、

```text
passwordが空か
passwordが8～20文字か
passwordに使用できない文字がないか
```

は、`password` だけを見れば判定できる。

しかし、

```text
password
passwordConfirm
```

が一致しているかどうかは、2つのフィールドを比較しなければ判定できない。

そこで、

```text
validator
├── PasswordMatch.java
└── PasswordMatchValidator.java
```

を作成して独自Validationを実装した。

### `PasswordMatch.java`

```java
@Documented
@Constraint(validatedBy = { PasswordMatchValidator.class })
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordMatch {

    String message() default "{password.match.message}";
	
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
    
    String passwordFieldName() default "";
    String passwordConfirmFieldName() default "";
}
```

### `@Constraint`

```java
@Constraint(
    validatedBy = { PasswordMatchValidator.class }
)
```

によって、`@PasswordMatch` の実際のValidation処理を `PasswordMatchValidator` が担当することを指定する。

```text
@PasswordMatch
    ↓
PasswordMatchValidator
```

という関係になる。

### `@Target(ElementType.TYPE)`

今回は1つのフィールドではなく、

```text
password
passwordConfirm
```

という2つのフィールドをまたいで検証する。

そのため、

```java
@Target(ElementType.TYPE)
```

として、`SignupForm` クラス全体をValidationの対象とする。

1つのフィールドを対象にする独自Validationなら、基本的には、

```java
@Target(ElementType.FIELD)
```

として対象フィールドにアノテーションを付ける。

### `@Retention(RetentionPolicy.RUNTIME)`

```java
@Retention(RetentionPolicy.RUNTIME)
```

によって、実行時までアノテーション情報を保持する。

Validation実行時にアノテーションを参照する必要があるため、独自Validationアノテーションでは基本的に `RUNTIME` を使用する。

### `@Documented`

```java
@Documented
```

はJavaDocなどのドキュメントに、このアノテーションの情報を含めるためのもの。

なくても `@PasswordMatch` のValidation機能自体には影響しない。

### `@interface`

```java
public @interface PasswordMatch
```

はJavaで独自アノテーション型を定義するための専用構文。

通常のインターフェースを作っているというより、

```text
@interface
→ アノテーション型を定義するJavaの構文
```

と考える。

### `message() default`

```java
String message()
        default "{password.match.message}";
```

はValidationエラー時に使用するデフォルトメッセージ。

defaultを設定しているため、使用側では、

```java
@PasswordMatch(
    passwordFieldName = "password",
    passwordConfirmFieldName = "passwordConfirm"
)
```

とするだけでよい。

使用側で別の `message` を指定した場合は、defaultではなくそちらが使用される。

### `groups()`

```java
Class<?>[] groups() default {};
```

は、同じFormでも処理によって実行するValidationを変えたい場合に使用する。

例えば、

```text
社員新規登録
→ nameとemployeeIdをチェック

社員情報更新
→ nameだけチェック
```

のような場合。

今回の `SignupForm` はサインアップ専用なので使用しない。

### `payload()`

```java
Class<? extends Payload>[] payload() default {};
```

は、Validationエラーに追加のメタ情報を持たせるための設定。

例えば、

```text
警告レベル
重大レベル
```

などの情報を付与する使い方がある。

今回のように入力エラーを画面へ表示するだけであれば使用しない。

---

## 5. `PasswordMatchValidator`の仕組み

実際の検証処理は `PasswordMatchValidator` に実装した。

```java
public class PasswordMatchValidator
        implements ConstraintValidator<PasswordMatch, Object> {
	
    private String passwordFieldName;
    private String passwordConfirmFieldName;
	
    @Override
    public void initialize(PasswordMatch passwordMatch) {
        this.passwordFieldName =
                passwordMatch.passwordFieldName();

        this.passwordConfirmFieldName =
                passwordMatch.passwordConfirmFieldName();
    }
	
    @Override
    public boolean isValid(
            Object value,
            ConstraintValidatorContext context) {

        BeanWrapper beanWrapper =
                new BeanWrapperImpl(value);

        String password =
                (String) beanWrapper.getPropertyValue(
                        this.passwordFieldName);
		
        String passwordConfirm =
                (String) beanWrapper.getPropertyValue(
                        this.passwordConfirmFieldName);
		
        if (password == null || passwordConfirm == null) {
            return true;
        }
        
        if (!passwordConfirm.equals(password)) {
            return false;
        }
        
        return true;
    }
}
```

### `ConstraintValidator`

```java
implements ConstraintValidator<PasswordMatch, Object>
```

は独自Validationの実際のチェック処理を実装するためのインターフェース。

```text
PasswordMatch
→ どのアノテーションのValidationを行うか

Object
→ 何を検証対象として受け取るか
```

を表している。

今回は `@PasswordMatch` をクラスに付けているため、Form全体を `Object` として受け取る。

### `initialize()`

```java
@Override
public void initialize(PasswordMatch passwordMatch)
```

では、`@PasswordMatch` に設定した、

```java
passwordFieldName = "password"
passwordConfirmFieldName = "passwordConfirm"
```

を取得する。

つまり、

```java
@PasswordMatch(
    passwordFieldName = "password",
    passwordConfirmFieldName = "passwordConfirm"
)
```

で指定した情報をValidator側で受け取っている。

### `isValid()`

```java
@Override
public boolean isValid(
        Object value,
        ConstraintValidatorContext context)
```

が実際のValidation処理。

```text
true
→ Validation成功

false
→ Validationエラー
```

となる。

`value` には検証対象となるFormオブジェクトが渡される。

### `ConstraintValidatorContext`

```java
ConstraintValidatorContext context
```

はValidation実行時の情報を扱うためのオブジェクト。

エラーメッセージの変更や独自のエラー設定などに使用できるが、今回は使用していない。

---

## 6. `null`の場合に`true`を返す理由

`PasswordMatchValidator` には、

```java
if (password == null || passwordConfirm == null) {
    return true;
}
```

という処理がある。

一見すると、

```text
パスワードがnullなのにValidation成功でいいのか？
```

と思える。

しかし、`@PasswordMatch` が担当するのは、

```text
パスワードが入力されているか
```

ではなく、

```text
passwordとpasswordConfirmが一致しているか
```

というValidationである。

未入力については、

```java
@NotBlank
private String password;

@NotBlank
private String passwordConfirm;
```

が担当している。

そのため、

```text
@NotBlank
→ 入力されているか確認する

@PasswordMatch
→ 2つの値が一致しているか確認する
```

と責務を分ける。

もし `PasswordMatchValidator` でもnullをエラーにすると、未入力時に、

```text
パスワードを入力してください
パスワードと確認用パスワードが一致しません
```

のように、同じ入力に対して意味の異なるエラーが発生する可能性がある。

そのため、どちらかが `null` の場合は `PasswordMatchValidator` では `true` を返し、未入力チェックは `@NotBlank` に任せている。

---

## 7. `BeanWrapper`を使ってValidatorを汎用化する

今回の `PasswordMatchValidator` では、

```java
BeanWrapper beanWrapper =
        new BeanWrapperImpl(value);
```

を使用している。

そして、

```java
String password =
        (String) beanWrapper.getPropertyValue(
                this.passwordFieldName);
```

のように、フィールド名から値を取得している。

今回だけを考えるなら、`BeanWrapper` を使わず `SignupForm` に直接アクセスすることもできる。

例えば、

```java
public class PasswordMatchValidator
        implements ConstraintValidator<PasswordMatch, SignupForm> {

    @Override
    public boolean isValid(
            SignupForm form,
            ConstraintValidatorContext context) {

        return form.getPassword()
                   .equals(form.getPasswordConfirm());
    }
}
```

と書ける。

こちらの方が実装自体は簡単。

しかし、

```java
ConstraintValidator<PasswordMatch, SignupForm>
```

としているため、このValidatorは `SignupForm` に完全に依存する。

例えば将来、

```java
public class PasswordChangeForm {

    private String newPassword;
    private String newPasswordConfirm;
}
```

を作成し、

```text
newPassword
newPasswordConfirm
```

を比較したくなった場合、このValidatorはそのままでは使用できない。

一方、現在の実装では、

```java
@PasswordMatch(
    passwordFieldName = "password",
    passwordConfirmFieldName = "passwordConfirm"
)
```

のように、比較するフィールド名を外から指定できる。

Validator側は、

```java
Object value
```

としてどのFormでも受け取り、

```java
beanWrapper.getPropertyValue(...)
```

によって指定されたプロパティを取得する。

そのため、別のFormでも、

```java
@PasswordMatch(
    passwordFieldName = "newPassword",
    passwordConfirmFieldName = "newPasswordConfirm"
)
public class PasswordChangeForm {
    ...
}
```

とすれば、同じ `PasswordMatchValidator` を再利用できる。

整理すると、

```text
SignupFormに直接アクセス
    ↓
実装は簡単
    ↓
SignupForm専用になる
```

のに対して、

```text
BeanWrapperを使う
    ↓
フィールド名を文字列で指定できる
    ↓
特定のFormに依存しない
    ↓
他のFormでも再利用できる
```

という違いがある。

今回 `BeanWrapper` を使用した一番の理由は、**汎用的な「2つのパスワードフィールドを比較するValidator」にするため**である。

---

## 8. クラスレベルValidationとグローバルエラー

`@PasswordMatch` は、

```java
@PasswordMatch(
    passwordFieldName = "password",
    passwordConfirmFieldName = "passwordConfirm"
)
public class SignupForm {
```

とクラスに付けている。

そのため、`@PasswordMatch` のValidationエラーは特定の1フィールドに対するエラーではなく、クラス全体に対するグローバルエラーになる。

通常の、

```java
@NotBlank
@Length
@Pattern
```

はフィールドに付いているため、

```html
th:errors="*{password}"
```

のようにフィールドエラーとして取得できる。

一方、`@PasswordMatch` は、

```html
<div th:if="${#fields.hasGlobalErrors()}"
     class="alert alert-danger">

    <p th:each="error : ${#fields.globalErrors()}"
       th:text="${error}"
       class="mb-0">
    </p>

</div>
```

としてグローバルエラーを取得する。

整理すると、

```text
FIELDレベルのValidation
    ↓
フィールドエラー
    ↓
th:errors="*{フィールド名}"

TYPEレベルのValidation
    ↓
グローバルエラー
    ↓
#fields.globalErrors()
```

となる。

複数フィールドをまたぐValidationでは、Java側のValidation方法だけでなく、画面側でのエラー表示方法も変わることが分かった。

---

## 9. Validationメッセージとplaceholderを改善する

Validationを実装して実際に画面を確認すると、デフォルトのValidationメッセージはユーザーにとって分かりにくかった。

そこで、`SignupForm` では、

```java
@NotBlank(
    message = "{signup.loginId.notBlank}"
)
```

のようにメッセージキーを指定し、

```properties
signup.loginId.notBlank=ユーザーIDを入力してください
```

として `messages.properties` で管理することにした。

これによって、

```text
Validationルール
→ SignupForm

表示する文章
→ messages.properties
```

と分離できる。

また、このアプリでは、

```text
messages.properties
messages_ja.properties
messages_zh_CN.properties
messages_zh_TW.properties
```

による多言語対応を行っているため、Validationエラーについても同じ仕組みで多言語化できる。

### エラーになる前に入力条件を伝える

エラーメッセージを改善したあと、

```text
入力
↓
登録
↓
エラー
↓
初めて入力条件が分かる
```

というUIも改善できることに気づいた。

そこで、

```html
th:placeholder="#{signup.loginId.placeholder}"
```

などを追加した。

```properties
signup.loginId.placeholder=半角英数字8～20文字
signup.password.placeholder=半角英数字8～20文字
signup.passwordConfirm.placeholder=パスワードをもう一度入力
```

これによって、ユーザーは登録ボタンを押す前から入力条件を確認できる。

今回、

```text
Validation
→ 不正な値を受け付けない

エラーメッセージ
→ 何が間違っているか知らせる

placeholder
→ 入力する前に条件を知らせる
```

という、それぞれの役割の違いも確認できた。

---

## 10. Validationルール自体も実装後に見直す

placeholderを追加して入力条件を画面上に表示したことで、

```text
ユーザーID
→ 半角英数字のみ

パスワード
→ 半角英数字のみ
```

という条件自体が現実的ではないことに気づいた。

そこで、それぞれの用途に合わせてValidationルールを変更した。

### ユーザーID

ユーザーIDでは半角英数字に加えて `_` を使用可能にした。

```java
@Pattern(
    regexp = "^[a-zA-Z0-9_]+$",
    message = "{signup.loginId.pattern}"
)
```

### パスワード

パスワードでは半角英数字に加えて半角記号も使用可能にした。

```java
@Pattern(
    regexp = "^[\\x21-\\x7E]+$",
    message = "{signup.password.pattern}"
)
```

`[\x21-\x7E]` はASCIIコードの16進数 `21` ～ `7E` の文字を対象とする。

```text
! " # $ % & ' ( ) * + , - . /
0～9
: ; < = > ? @
A～Z
[ \ ] ^ _ `
a～z
{ | } ~
```

が含まれる。

この変更で特に重要だったのは、**Validationが正しく動作していることと、Validationルール自体が適切であることは別問題**だという点。

最初の、

```java
^[a-zA-Z0-9]+$
```

もコードとしては正しく動いていた。

しかし、実際のサインアップ画面として使用してみると、パスワードまで半角英数字だけに制限する必要はないと気づいた。

また、Validationルールを変更した場合はJava側だけを変更するのではなく、

```properties
signup.loginId.pattern=...
signup.loginId.placeholder=...

signup.password.pattern=...
signup.password.placeholder=...
```

も同時に変更する必要がある。

**実際のValidationと、ユーザーに表示している入力条件が食い違わないようにすることも必要である。**

---

## 11. 実装を終えて

新規登録機能は多くのWebアプリケーションで同じような仕組みを持っているため、これまで学んだことをそのまま流用できる部分が多かった。

そのため、サインアップ機能全体としてはそこまで難しくなかった。

一方で、独自Validationのためのアノテーション定義では、

```text
@Constraint
@Target
@Retention
@interface
ConstraintValidator
ConstraintValidatorContext
BeanWrapper
BeanWrapperImpl
```

など、普段あまり使わないクラス、インターフェース、アノテーションが多く登場した。

一度学習していても、こうした普段使わない仕組みは久しぶりに実装するとやはり忘れている部分が多かった。

特に今回は、単にコードを書くだけでなく、

```text
なぜnullならtrueなのか？

なぜSignupFormへ直接アクセスしないのか？

なぜBeanWrapperを使っているのか？

なぜ@PasswordMatchはクラスに付けるのか？
```

といった点まで確認したことで、独自Validationのコードを単なる定型文としてではなく、それぞれの処理の役割まで整理できた。

### 追加修正が段階的に発生した

今回の実装では、

```text
1. サインアップ機能の実装
2. サインアップ時のバリデーション実装
```

までは一気に実装できた。

一方、

```text
3. バリデーションエラーメッセージの追加
4. signup.htmlのUI改善
5. ユーザーIDとパスワードのValidation変更
```

については、1つの実装が終わるたびに、

```text
「あ、これも直さなきゃ」
```

と気づいて追加したものである。

実際には、

```text
Validationを実装
    ↓
デフォルトのエラーメッセージが分かりにくい
    ↓
カスタムメッセージを追加
    ↓
入力後ではなく入力前にも条件を伝えたい
    ↓
placeholderを追加
    ↓
入力条件を画面上で確認
    ↓
そもそも半角英数字だけという条件が適切ではない
    ↓
Validationルールを変更
```

という流れになった。

最初からこれらすべてに気づいて実装できるのが理想ではある。

ただし今回は、実装に致命的な問題があって作り直したというより、**一度実装したものを実際に確認したことで改善点に気づき、追加修正によってより使いやすくなった**という面が強かった。

そのため、3～5の追加修正については大きな手戻りというより、実際の画面を確認しながら段階的に完成度を上げていった修正だったと考えている。

コードが正常に動作した時点で終わりにするのではなく、

```text
機能として動くか
+
ユーザーにとって分かりやすいか
+
入力ルール自体が適切か
```

まで実際に確認することも重要だと感じた。

---

## 次にやること

ログイン機能を実装する。