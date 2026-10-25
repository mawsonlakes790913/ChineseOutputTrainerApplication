# 033-06 メール送信時の例外処理とSpring MVCのエラー表示（学習ログ）

## はじめに

033-01〜033-05では、Amazon SESによるメール認証・配送、AWS SDKとIAMを利用した送信、さらにElastic BeanstalkでのHTTPS設定の管理を学んだ。本節では、**メール送信そのものが失敗した場合に、Spring Bootアプリケーションがその失敗をどのように扱い、ユーザーへ伝えるべきか**を整理する。

ここでは、メール送信失敗時の例外処理と、Spring MVCからユーザーへエラーを伝える方法を整理する。コード変更と確認結果は[033-06実装ログ](./033-06-amazon-ses-mail-error-message-development-log.md)に記録する。

## 1. メール送信失敗をどこで検知するのか

### AWS SDKの例外をアプリケーション固有の例外へ変換する

Amazon SES APIを呼び出す`MailService`では、SDKによる送信処理が失敗した場合に`SdkException`を捕捉し、独自の`MailSendException`をスローする構成としていた。

```java
} catch (SdkException e) {
    throw new MailSendException(
            "Amazon SESでのメール送信に失敗しました",
            e);
}
```

ここでの役割分担は次のとおりである。

- `SdkException`は、AWS SDKを利用する処理で発生した例外である。
- `MailSendException`は、アプリケーション側で「メール送信に失敗した」という事象を表す独自例外である。
- 例外の原因となった`e`をコンストラクターへ渡すことで、元の例外を原因として保持する。

この変換により、呼び出し元のControllerは、SDKの具体的な例外型を直接扱わずに、メール送信という業務処理の失敗を捕捉できる。

ただし、**`MailSendException`をスローするだけでは、画面にエラーメッセージは表示されない**。例外を受け取ったControllerで、画面遷移と表示内容を決める必要がある。

### 開発者向けメッセージとユーザー向けメッセージを分ける

例外に付ける「Amazon SESでのメール送信に失敗しました」という文言は、内部で原因を調査するための情報である。一方、ユーザーが知りたいのは、どのAWSサービスで問題が起きたかではなく、メールが送信できたかどうかと、次にどうすればよいかである。

そこで、ユーザー向けには「メールの送信に失敗しました。時間をおいて再度お試しください。」という、内部のサービス名を含まないメッセージを用意した。

**ログ・例外は原因調査のため、画面のメッセージは利用者への状況説明のため**という目的の違いを理解した。

## 2. 同じメール送信失敗でも、会員登録とパスワード再設定で対応が異なる

### 会員登録：登録成功を取り消さず、メール送信失敗を通知する

会員登録処理では、`signupService.signup()`によるユーザー登録が成功した後に、登録完了メールを送信する。

元の`SignupController`の`postSignup()`には、すでに`MailSendException`を捕捉し、ログを記録する処理があった。しかし、メール送信に失敗しても、画面上では登録完了メールを送ったように見える点が問題だった。

ここで重要なのは、**ユーザー登録の成功と登録完了メールの送信成功は別の結果**であることだ。メール送信の失敗を理由に、完了済みの登録まで失敗したと表示するのは実際の状態と一致しない。

そのため、会員登録自体は成功扱いのままにして、ログイン画面へ遷移した際に、登録完了メールの送信に失敗したことを併せて表示する方針とした。

```java
} catch (MailSendException e) {
    log.error(
            "会員登録完了メールの送信に失敗 loginId={}",
            form.getLoginId(),
            e);

    redirectAttributes.addFlashAttribute(
            "mailError",
            messageSource.getMessage(
                    "signup.error.mailSend",
                    null,
                    locale));
}
```

`signupSuccess`も設定されるため、遷移先では**登録成功とメール送信失敗の両方**を伝える構成になる。

### パスワード再設定：送信完了画面へ進ませない

パスワード再設定では、メールの送信が処理の目的に直結している。確認した呼び出し関係は次のとおりである。

```text
postForgotPassword()
    ↓
PasswordResetService.createPasswordResetToken()
    ↓
MailService.sendPasswordResetEmail()
    ↓
Amazon SES
```

`MailService`からスローされた`MailSendException`は、`PasswordResetService`を経由してControllerまで伝わる。一方、当初の`postForgotPassword()`には、その例外を捕捉する処理がなかった。

送信に失敗したにもかかわらず送信完了画面へ遷移すると、ユーザーは届かないメールを待つことになる。そこで、Controllerで例外を捕捉し、メールアドレス入力画面を再表示して失敗を通知する構成とした。

```java
try {
    passwordResetService.createPasswordResetToken(
            form.getEmail(),
            locale);

} catch (MailSendException e) {
    log.error("パスワード再設定メールの送信に失敗", e);

    model.addAttribute(
            "mailError",
            messageSource.getMessage(
                    "password.reset.error.mailSend",
                    null,
                    locale));

    return getForgotPassword(form, model);
}
```

この違いから、**例外の種類だけで画面遷移を決めるのではなく、直前の業務処理がどこまで成功しているかを考える必要がある**と学んだ。

## 3. Flash AttributeとModel属性の使い分け

会員登録では`redirectAttributes.addFlashAttribute()`を使用し、パスワード再設定では`model.addAttribute()`を使用している。

会員登録では、処理後にログイン画面へリダイレクトするため、リダイレクト先へ一時的に表示情報を引き継ぐFlash Attributeを利用する。一方、パスワード再設定では、エラー発生時に入力画面を再表示するため、その画面を描画するModelへ`mailError`を設定する。

両者は同じ`mailError`という属性名を使っていても、**画面に値を届ける経路が異なる**。表示の仕組みを考える際は、Controllerがリダイレクトするのか、テンプレートを再描画するのかを確認する必要がある。

## 4. Thymeleafでエラーがある場合だけ表示する

ログイン画面の`login.html`と、パスワード再設定の`forgot-password.html`には、次の表示要素を追加した。

```html
<div th:if="${mailError}"
     class="alert alert-danger"
     role="alert"
     th:text="${mailError}">
</div>
```

`th:if`は`mailError`が存在する場合に表示要素を出力するための条件、`th:text`はその属性値を画面上のテキストとして表示するための指定である。`alert alert-danger`はBootstrapによるエラー表示のスタイルである。

ここで、Controllerで`mailError`を設定するだけでは不十分であり、**実際に表示するテンプレートにも対応する要素が必要**だと確認した。

## 5. MessageSourceと多言語メッセージの役割

今回のアプリケーションでは、日本語・簡体字中国語・繁体字中国語に対応しているため、ユーザー向けメッセージをController内に直接固定するのではなく、`messageSource.getMessage()`で取得する構成にした。

```java
messageSource.getMessage(
        "password.reset.error.mailSend",
        null,
        locale)
```

ここで、第1引数はメッセージキー、`locale`は表示言語の選択に使われる。追加したメッセージは以下のとおりである。

**日本語**

```properties
signup.error.mailSend=会員登録は完了しましたが、登録完了メールの送信に失敗しました。
password.reset.error.mailSend=メールの送信に失敗しました。時間をおいて再度お試しください。
```

**簡体字中国語**

```properties
signup.error.mailSend=注册已完成，但注册成功通知邮件发送失败。
password.reset.error.mailSend=邮件发送失败，请稍后重试。
```

**繁体字中国語**

```properties
signup.error.mailSend=註冊已完成，但註冊成功通知郵件發送失敗。
password.reset.error.mailSend=電子郵件發送失敗，請稍後再試。
```

同じキーを各言語のメッセージファイルに定義しておくことで、Controller側の処理を言語ごとに分岐させずに表示文言を切り替えられる。

また、会員登録用のメッセージには「登録は完了した」という事実を含め、パスワード再設定用には再試行を促す内容を使用している。**多言語化は単に翻訳する作業ではなく、各画面で伝えるべき処理結果を明確にする作業でもある**。

## 6. 確認できたことと、未検証のことを区別する

今回の修正では、Controllerの例外処理、`mailError`の設定、Thymeleafの表示条件、各言語のメッセージ定義の整合性を確認し、Mavenビルドも実施した。

一方、**Amazon SESの送信エラーを意図的に発生させ、エラーメッセージが画面に表示されるところまで確認するテストは実施していない**。

したがって、今回の成果は「送信失敗時の表示に必要なコードと設定を整備し、その構成を確認した」ことであり、「実際の送信失敗を再現して画面表示まで検証済み」とは区別して記録する。

## まとめ

メール送信の例外処理を通じて、次の点を理解した。

- AWS SDKの例外をアプリケーション独自の`MailSendException`として扱うことで、Controllerがメール送信の失敗を認識できる。
- 内部ログに残す情報と、ユーザーに表示する文言は目的が異なる。
- 会員登録では登録成功を維持しつつ送信失敗を通知し、パスワード再設定では送信完了画面へ進まず入力画面を再表示する。
- リダイレクトを伴う場合のFlash Attributeと、画面を再描画する場合のModel属性は使い分ける。
- Controller、Thymeleaf、メッセージ定義の三者がそろって初めて、利用者向けのエラー表示が成立する。
- コードと設定の確認と、例外発生時の画面テストは別の検証である。

これで、第33章のメール送信機能について、**送信の仕組みだけでなく、送信できなかった場合にアプリケーションがどう振る舞うべきか**まで整理できた。
