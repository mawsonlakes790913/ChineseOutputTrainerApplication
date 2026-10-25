# 027 メールアドレス登録 学習ログ

## 1. 既存データがあるテーブルへのNOT NULLカラム追加

既存レコードが存在するテーブルへ新しい必須カラムを追加する場合、最初から`NOT NULL`を設定すると既存レコードが制約を満たせない。

今回は、

```text
emailカラムをNULL許可で追加
    ↓
既存レコードへ値を設定
    ↓
NOT NULLを設定
    ↓
UNIQUE制約を設定
```

という順番で変更した。

既存データを考慮してDBの変更手順を決める必要があることを確認できた。また、`UNIQUE`制約を設定する前に既存データの重複がないことも重要となる。

---

## 2. HTMLのtype="email"とSpringのバリデーション

メールアドレス入力欄では、

```html
<input type="email">
```

を使用し、`SignupForm`には次の制約を設定した。

```java
@NotBlank(message = "{signup.email.notBlank}")
@Email(message = "{signup.email.invalid}")
private String email;
```

`type="email"`はブラウザ側の入力補助・簡易チェックであり、サーバー側ではBean Validationによって改めて入力値を検証する。

また、`@NotBlank`は未入力や空白のみの入力を禁止し、`@Email`はメールアドレスの形式を検証する。**必須入力の検証と形式の検証は別の役割**である。

ブラウザ側のチェックだけに依存せず、サーバー側でも入力値を検証する構成を確認できた。

---

## 3. 独自例外にフィールド情報を持たせる

SignupではログインIDとメールアドレスの両方で重複エラーが発生するようになったため、エラーメッセージだけではどの入力欄へエラーを表示すればよいか判断できなくなった。

そこで`DuplicateSignupException`に、

```java
private final String field;
```

を持たせ、Controllerでは、

```java
bindingResult.rejectValue(
        e.getField(),
        "duplicate",
        e.getMessage());
```

として重複したフィールドへエラーを登録するようにした。

この実装では、**Serviceが重複した項目を判断し、例外がその情報をControllerへ伝え、Controllerが入力欄にエラーを表示する**という役割分担になっている。

例外にはエラーメッセージだけでなく、呼び出し元のエラー処理に必要な追加情報を持たせることもできると分かった。

---

## 4. LIKEによる部分一致検索

Adminのメールアドレス検索では、次の条件を使用した。

```sql
LOWER(u.email)
LIKE LOWER(CONCAT('%', :email, '%'))
```

`%`を検索値の前後に付けることで部分一致検索となり、`LOWER`をDB側と入力値の両方に適用することで、大文字・小文字を区別せず比較できる。

また、今回は完全なメールアドレスを入力するフォームではなくキーワード検索なので、HTML側では`type="email"`ではなく`type="text"`を使用した。

---

## まとめ

今回の実装は、これまで作成したSignup、ユーザー情報変更、Admin検索などの仕組みをメールアドレスにも適用する部分が中心だった。

新しく確認した内容としては、

- 既存データがある場合の`NOT NULL`・`UNIQUE`制約の追加手順
- `type="email"`、`@NotBlank`、`@Email`の役割の違い
- 独自例外にフィールド情報を保持し、ServiceとControllerで役割を分ける方法
- `LIKE`、`CONCAT`、`LOWER`を使った部分一致検索

が主なポイントとなった。
