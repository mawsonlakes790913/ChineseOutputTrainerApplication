# 028 メールを利用したパスワードリセット機能 — 学習ログ

## 概要

今回は、登録済みのメールアドレスを利用したパスワードリセット機能を実装した。

これまでのパスワード変更機能はログイン済みユーザー向けだったため、パスワードを忘れてログインできない場合には利用できなかった。そこで、登録メールアドレスへ一時的なURLを送信し、そのURLを利用して新しいパスワードを設定できる仕組みを追加した。

```text
登録メールアドレスを入力
    ↓
パスワードリセット用URLをメールで受信
    ↓
トークンの有効性を確認
    ↓
新しいパスワードを設定
    ↓
使用済みトークンを削除
```

今回の学習では、トークンを使った一時的な操作許可、JPAのSQL実行タイミング、SMTPによるメール送信、PostgreSQLの権限管理などが特に重要だった。

---

## 1. パスワードリセットトークンの役割

パスワードを忘れたユーザーはログインできないため、通常のログイン認証を前提としてパスワード変更を許可することはできない。

そこで、登録メールアドレスへランダムなトークンを含むURLを送り、そのトークンを提示したユーザーに限って、一時的にパスワードの再設定を許可する仕組みを採用した。

例えば、DBに次のような情報を保存する。

```text
ユーザー：user_id = 15
トークン：8f21c9a7-63c4-4b42-9e12-xxxxxxxxxxxx
有効期限：2026-09-18 03:30
```

メールに記載されたURLへアクセスすると、サーバーはURLのトークンを取得してDBを検索し、対応するユーザーを特定する。

ここで重要なのは、**URL自体にユーザー情報を持たせるのではなく、トークンとユーザーの対応関係をサーバー側で管理する**という点だった。

また、トークンは登録メールアドレスを受信できることを前提とした一時的な認可手段であり、利用者の身元を絶対的に証明するものではない。第三者にURLを知られれば悪用される可能性があるため、有効期限と使用後の無効化が必要になる。

今回は有効期限を1時間とし、トークンを「特定の操作を一時的に許可する使い捨ての鍵」として扱った。

---

## 2. トークンとUsersの関連付け

パスワードリセット要求を管理するため、`password_reset_token`テーブルと`PasswordResetToken` Entityを追加した。

今回の設計では、1人のユーザーが同時に保持できるトークンを1つに制限した。

```text
Users 1 ─ 0..1 PasswordResetToken
```

Entity側では`@OneToOne`で`Users`と関連付け、DB側でも`password_reset_token.user_id`へ`UNIQUE`制約を設定した。

**Java側の関連付けだけでなく、DB側の制約でも同じ設計を保証する**ことが重要だと分かった。

また、Repositoryには次の2種類の検索が必要になった。

```java
Optional<PasswordResetToken> findByUser(Users user);
Optional<PasswordResetToken> findByToken(String token);
```

`findByUser()`は再発行時に既存トークンがあるか確認するため、`findByToken()`はメール内のURLから受け取った文字列を検証するために使用する。

同じEntityを検索する場合でも、処理の目的によって検索の起点が異なることを理解できた。

---

## 3. トークンの生成・再発行とJPAのflush

パスワードリセット要求を受け取ったら、メールアドレスから`Users`を取得し、既存トークンがあれば削除して新しいトークンを発行する。

トークンには次のコードを使用した。

```java
String token = UUID.randomUUID().toString();
```

`UUID.randomUUID()`はランダムベースのUUID version 4を生成する。戻り値は`UUID`型なので、今回は`toString()`で文字列へ変換してDBへ保存した。UUIDには複数の生成方式があるため、「UUIDであればすべて同じ性質」というわけではない点にも注意したい。

トークンには対象ユーザーと、発行から1時間後の有効期限を設定した。

```java
passwordResetToken.setUser(user);
passwordResetToken.setToken(token);
passwordResetToken.setExpiresAt(
        LocalDateTime.now().plusHours(1));
```

### 再発行時に発生したUNIQUE制約違反

実装中、同じユーザーが再度パスワードリセットを要求すると、`user_id`のUNIQUE制約違反が発生した。

原因は、Repositoryの`delete()`を呼び出しても、トランザクション内ではDELETE文が直ちにDBへ送信されるとは限らないことだった。

```text
既存トークンをdelete()
    ↓
DB上には既存レコードが残っている
    ↓
新しいトークンのINSERT
    ↓
同じuser_idのUNIQUE制約違反
```

そこで、削除直後に`flush()`を追加した。

```java
passwordResetTokenRepository.delete(existingToken.get());
passwordResetTokenRepository.flush();
```

```text
既存トークンをdelete()
    ↓
flush()でDELETEをDBへ送信
    ↓
新しいトークンを保存
```

この経験から、**JavaでRepositoryのメソッドを呼び出す順番と、実際にSQLが実行されるタイミングは必ずしも一致しない**ことを学んだ。

なお、`flush()`は保留中の変更をDBへ送信する処理であり、トランザクションを確定する`commit`とは異なる。`flush()`後でも、トランザクションがロールバックされれば変更は取り消される。

---

## 4. JavaMailSenderによるメール送信と環境別設定

生成したトークンをユーザーへ届けるため、Spring BootのMail Starterと`JavaMailSender`を利用した。

メール作成には`MimeMessage`と`MimeMessageHelper`を使用した。

- `MimeMessage`：メール1通分のメッセージを表す。
- `MimeMessageHelper`：送信元・送信先・件名・本文などの設定を補助する。

今回はGmailのSMTPサーバーを使用し、ポート587、SMTP認証、STARTTLSを設定した。

SMTPの認証情報は設定ファイルへ直接書かず、環境変数から取得するようにした。

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

DBのパスワードやAPIキーと同じく、メール送信用の認証情報もコードや設定ファイルへ直接埋め込まないことが重要だと再確認した。

### ベースURLを設定値として管理する理由

メール本文には、トークンを含むパスワード再設定URLを記載する。

```text
/reset-password?token=...
```

ローカル環境では`http://localhost:8080`を使用するが、デプロイ後は本番環境のURLへ切り替える必要がある。

そこで、ベースURLを`app.base-url`として設定し、`@Value`で取得するようにした。

```java
@Value("${app.base-url}")
private String baseUrl;
```

**環境によって変わる値をJavaコードから分離する**ことで、同じ処理を維持したまま設定だけを変更できることを確認した。

---

## 5. トークンの検証・パスワード更新・無効化

メール内のURLを開いた時点では、トークンを無条件に信用せず、次の条件を確認する。

```text
DBにトークンが存在するか
    ↓
有効期限内か
    ↓
条件を満たす場合のみ再設定画面を表示
```

今回はこの処理を`PasswordResetService.validateToken()`にまとめた。有効な場合は`PasswordResetToken`を返し、関連付けられた`Users`からパスワード変更対象を特定する。

有効期限の判定には、次の条件を使用した。

```java
passwordResetToken.getExpiresAt()
        .isBefore(LocalDateTime.now())
```

この条件では、期限が現在時刻より前になった場合に無効とする。期限時刻と完全に一致した場合も無効としたいなら、境界条件を含めた判定を検討できる。

### GETとPOSTの両方で検証する

パスワード再設定画面を表示するGETでトークンを検証しても、実際にパスワードを更新するPOSTの時点で同じトークンが有効とは限らない。

そのため、POSTの処理でも`validateToken()`を再実行した。

```text
GET /reset-password
    ↓
トークン検証・画面表示
    ↓
POST /reset-password
    ↓
トークン再検証・パスワード更新
    ↓
使用済みトークン削除
```

新しいパスワードには既存のバリデーションと`@PasswordMatch`を使用し、保存時には`PasswordEncoder`でハッシュ化する。

```java
String encodedPassword =
        passwordEncoder.encode(newPassword);
user.setPassword(encodedPassword);
```

更新後には`PasswordResetToken`を削除し、同じURLを再利用できないようにした。

この実装を通して、**トークンには時間による制限だけでなく、使用後に無効化する仕組みも必要**だと理解した。

---

## 6. 未認証ユーザー向けの画面とアクセス制御

パスワードリセットはログインできないユーザーが利用する機能なので、次のURLをSpring Securityの`permitAll()`対象とした。

```text
/forgot-password
/forgot-password/sent
/reset-password
```

ログイン画面からメールアドレス入力画面へ進み、送信完了画面を経て、メール内のURLからパスワード再設定画面へアクセスする。

パスワード変更後はログイン画面へ戻り、新しいパスワードで通常のログインを行う。

ここで理解したのは、**URLへのアクセスを未認証ユーザーに許可することと、パスワードの変更を無条件に許可することは異なる**という点だった。

`permitAll()`は画面やエンドポイントへアクセスできるようにする設定であり、実際にパスワードを変更できるかどうかはServiceでのトークン検証によって判断する。

---

## 7. PostgreSQLのテーブル権限とシーケンス権限

新しい`password_reset_token`テーブルを追加した後、動作確認時に次のエラーが発生した。

```text
permission denied for table password_reset_token
```

原因は、アプリケーションのDB接続ユーザーに、新しく作成したテーブルへの操作権限が付与されていなかったことだった。

そこで、テーブルへの権限を追加した。

```sql
GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE password_reset_token
TO chinese_output_forge_app;
```

さらに、`token_id`は`BIGSERIAL`による自動採番を使用しているため、テーブルの権限だけでなく、内部で使用するシーケンスへの権限も必要だった。

```sql
GRANT USAGE, SELECT
ON SEQUENCE password_reset_token_token_id_seq
TO chinese_output_forge_app;
```

今回の経験から、PostgreSQLでは**テーブルの操作権限と、自動採番に利用するシーケンスの権限が別々に管理されている**ことを学んだ。

---

## まとめ

今回の実装では、ログインできないユーザーに対して、登録メールアドレスへ送信した一時的なトークンを用いてパスワード再設定を許可する仕組みを学習した。

特に重要だったのは、トークンとユーザーの関連付け、有効期限と使用後の削除、GETとPOSTの両方での検証である。

また、実際に発生したUNIQUE制約違反を通してJPAの`delete()`と`flush()`の違いを理解し、新規テーブルへのアクセス権限エラーからPostgreSQLのテーブル権限とシーケンス権限の違いも確認できた。

SMTPによるメール送信や環境別設定も含め、パスワードリセット機能が複数の技術を組み合わせて成立していることを理解できた。
