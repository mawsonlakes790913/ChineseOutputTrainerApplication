# 024 内部処理のリファクタリング — 学習ログ

## このチャプターで行ったこと

このチャプターでは、新しい機能を追加するのではなく、これまで実装してきたControllerやServiceを改めて確認し、内部処理のリファクタリングを行った。

最初にControllerとServiceの責務を確認し、Controllerに入り込んでいたデータ変換や検索条件の判断、Repositoryへの直接アクセスなどをServiceへ移動した。

その後、Service内部について、

* 複数メソッドに重複している処理
* 不要な条件分岐
* マジックナンバー
* 長くなったメソッド
* 不要になったpublicメソッド
* 不要な一時変数

などを確認して整理した。

最後にControllerをもう一度確認し、GET・POSTメソッドの命名、`Page`型の変数名、ログインユーザー取得処理、セッションからの値取得などを整理した。

また、セッション切れ後の再ログイン時に不定期で発生するstatus 999について、対策と再発時の調査準備を行った。原因は未特定であり、解消を確認したわけではない。

リファクタリングでは、すべての重複を共通化せず、処理の意味が明確になる変更を優先した。

## 1. ControllerとServiceの責務は、コードの長さではなく「何を判断しているか」で考える

今回特に勉強になったのは、Controllerの処理が長いからServiceへ移すのではなく、その処理が何を担当しているのかを考える必要があるという点だった。

例えば通常学習では、Controller側で、

```text
ログインしている
    → ログインユーザー用の問題を取得

ログインしていない
    → 非ログインユーザー用の問題を取得

sourceConditionがnull
    → ALLとして扱う
```

という判断をしていた。

これはHTTPリクエストを受け取ったりViewを決定したりする処理ではなく、「どの問題を取得するか」というアプリケーション側のルールである。

そのため、Controllerでは、

```java
List<Question> questions =
        practiceService.getPracticeQuestions(
                userId,
                languageVariant,
                difficulty,
                sourceCondition,
                start,
                random);
```

と必要な情報だけをServiceへ渡し、実際の判断はServiceに任せる形に変更した。

これまでは、

```text
Controllerが長い
    → Serviceへ移す
```

という見方をしがちだったが、

```text
この判断は画面・HTTPに関するものか
        ↓
それともアプリケーションの処理ルールか
```

で考える方が、ControllerとServiceの責務を判断しやすいと分かった。

## 2. ControllerからRepositoryを直接呼ばない理由が具体的に分かった

`AiPracticeController`では、AI生成問題がすでに保存されているか確認するために`QuestionRepository`を直接使用していた。

今回これを、

```text
AiPracticeController
        ↓
AiPracticeService
        ↓
QuestionRepository
```

という構成へ変更した。

この修正によって、Controller・Service・Repositoryという役割分担を採用する理由が、具体的な処理と結びついた。

「保存済みかどうか確認する」という処理は単なるSQL実行ではなく、

```text
このユーザーが
このAI生成問題を
すでに自分の問題として保存しているか
```

というアプリケーション上の意味を持っている。

そのため、ControllerがRepositoryの検索方法まで知る必要はなく、ControllerはServiceに「保存済みの問題IDを取得してほしい」と依頼するだけでよい。

Repositoryを直接呼んでいるかどうかだけではなく、**DBアクセスにアプリケーション上の意味があるなら、その意味をServiceのメソッドとして表現する**という考え方が分かった。

## 3. Serviceを作っただけでは責務を分離したことにならない

AI生成履歴では、`AiGenerationHistoryService`が既に存在していたにもかかわらず、

```text
履歴検索
    → AiPracticeServiceからRepositoryを直接呼ぶ

履歴削除
    → AiPracticeServiceからRepositoryを直接呼ぶ

履歴保存
    → AiGenerationHistoryServiceを呼ぶ
```

という状態になっていた。

つまり、Serviceクラスを分けていても、その機能に関する処理が複数のServiceへ中途半端に分散していた。

これを、

```text
AiPracticeService
        ↓
AiGenerationHistoryService
        ↓
AiGenerationHistoryRepository
```

に整理した。

ここから、クラスを分けること自体ではなく、**AI生成履歴を管理する責務の所在を一つに定めること**が重要だと分かった。

特に今回のようにServiceから別のServiceを利用する場合でも、単に依存関係を増やすのではなく、担当する機能が明確なら自然な構成になることに気づいた。

## 4. 重複コードは「同じコードだから」ではなく「同じ意味だから」まとめる

今回、多くの重複処理を確認したが、すべてをprivateメソッドへ切り出したわけではなかった。

例えば`UserProfileController`では、ログインID変更後とパスワード変更後の両方で、

```java
SecurityContextHolder.clearContext();

session.removeAttribute(
        HttpSessionSecurityContextRepository
                .SPRING_SECURITY_CONTEXT_KEY
);
```

を実行していた。

これは単に同じ2行が存在しているだけではなく、どちらも、

```text
現在の認証状態を破棄する
```

という同じ意味を持つ処理だった。

そのため、

```java
private void clearAuthentication(
        HttpSession session)
```

としてまとめることで、呼び出し側も、

```java
clearAuthentication(session);
```

だけで処理の目的が分かるようになった。

一方、LanguageVariantControllerなどにあった短いredirect処理については、同じ記述が複数存在していてもprivateメソッドにはしなかった。

ここから、

```text
コードが重複している
    ↓
必ず共通化する
```

ではなく、

```text
同じ意味を持つ一つの処理として名前を付けられるか
        ↓
名前を付けることで呼び出し側が分かりやすくなるか
```

を基準にした方がよいと分かった。

## 5. privateメソッドに分ければ必ず読みやすくなるわけではない

これまでは長いメソッドを見ると、privateメソッドへ細かく分割した方がよいと思うことがあった。

しかし今回のリファクタリングでは、長いControllerメソッドでも、処理が上から下へ一本道で進んでおり、それぞれの処理がそのControllerの責務である場合は、無理に分割しなかった。

例えば、

```text
リクエスト値を受け取る
    ↓
Serviceを呼ぶ
    ↓
ページング情報を作る
    ↓
Modelへ値を設定する
    ↓
Viewを返す
```

という処理であれば、ある程度長くても一つのメソッド内で順番に読める。

これを細かくprivateメソッドへ分けすぎると、

```text
メインメソッドを見る
    ↓
privateメソッドへ移動
    ↓
戻る
    ↓
別のprivateメソッドへ移動
```

となり、かえって全体の流れを追いにくくなる場合がある。

メソッドの行数そのものよりも、**一つのメソッドの中に異なる責務が混在しているかどうか**を見る方が重要だと分かった。

## 6. 変数名は型だけでなく、実際に保持しているものを表した方がよい

複数のControllerで、

```java
Page<UserQuestionListDto> questionList
```

のような変数名を使用していた。

しかし実際に保持しているのは`List`ではなく`Page`なので、

```java
Page<UserQuestionListDto> questionPage
```

へ変更した。

同様に、

```text
structureList → structurePage
userList      → userPage
```

なども変更した。

機能上はどちらでも動作するが、`Page`には、

```text
getContent()
getTotalElements()
getNumber()
getSize()
```

など、一覧そのものだけではなくページング情報も含まれている。

そのため、変数名を見ただけで、

```text
これは画面に表示するListそのものなのか
それともページング情報を含むPageなのか
```

を区別できるようになった。

変数名は単なる好みではなく、コードを読むときに型や役割を判断するための情報になることに改めて気づいた。

## 7. マジックナンバーは「数字だから」ではなく、意味を持つ値なら定数化する

AI生成履歴では、履歴を最大10件まで保持するため、

```java
if (histories.size() >= 10)
```

のように直接`10`を使用していた。

これを、

```java
private static final int MAX_HISTORY_SIZE = 10;
```

として、

```java
if (histories.size() >= MAX_HISTORY_SIZE)
```

のように変更した。

これによって、コードを読んだときに「10という数字で比較している」のではなく、「最大履歴件数を超えているか確認している」ことが分かる。

また、将来最大件数を変更する場合も、定数の値だけを変更すればよい。

ただし、すべての数字を定数にする必要があるわけではなく、**アプリケーション上のルールや意味を持っている数字を定数として名前で表現する**ことが重要だと分かった。

## 8. booleanよりenumの方が状態の意味を明確にできる場合がある

AI生成処理では、使用するAIを、

```java
boolean useChatGPT = false;
```

で切り替えていた。

この場合、

```text
true  = ChatGPT
false = Gemini
```

という対応関係をコードを読む側が覚えておく必要がある。

そこで、

```java
public enum AiProvider {
    CHAT_GPT,
    GEMINI
}
```

を作成し、

```java
private static final AiProvider AI_PROVIDER =
        AiProvider.GEMINI;
```

として使用するAIを明示する形に変更した。

これなら、

```java
AI_PROVIDER == AiProvider.GEMINI
```

のように値そのものが意味を表す。

また、将来的に別のAIを追加する場合も、

```text
CHAT_GPT
GEMINI
OTHER_AI
```

のように選択肢を増やせる。

booleanは二択を表現するには簡単だが、**その二択自体に名前が必要な場合や、将来選択肢が増える可能性がある場合はenumの方が自然**だと分かった。

## 9. 存在確認だけならEntityを取得する必要はない

FavoriteServiceでは、お気に入りが存在するか確認するために、

```java
Optional<Favorite> optionalFavorite =
        favoriteRepository.findById(key);
```

としてEntityを取得していた。

しかし必要なのはFavoriteの内容ではなく、

```text
存在するか
存在しないか
```

だけだった。

そのため、

```java
favoriteRepository.existsById(key)
```

を使用する形へ変更した。

Repositoryのメソッドを選ぶときも、

```text
データが必要
    → find...

存在だけ確認したい
    → exists...
```

と、後続処理で何が必要なのかによって使い分けることができると分かった。

## 10. 再現性の低い不具合では、仮説と確認済みの事実を区別する

長時間操作せずにセッションが切れた後、再ログインを試みると、まれに`status: 999`と表示される現象があった。再読み込みするとWhitelabel Error Pageが表示され、ログイン画面へ戻って再度送信すると正常にログインできた。

通常のログイン失敗であれば`AuthenticationFailureHandler`が処理するため、この現象は単純なパスワード間違いとは異なる可能性があった。しかし、発生頻度が低く、最初にどの処理でエラーが起きたのかは特定できなかった。

調査では、Spring Securityの設定にある`anyRequest().authenticated()`に対し、エラー処理用の`/error`が公開されていないことに着目した。エラー処理そのものが認証の対象となり、問題が複雑化している可能性を考え、次の許可設定を追加した。

```java
.requestMatchers("/error").permitAll()
```

また、再発時に認証処理の流れを追えるよう、`application.yml`でSpring SecurityのDEBUGログを有効にした。

```yaml
logging:
  level:
    org.springframework.security: DEBUG
```

ここで重要なのは、**`/error`へのアクセス許可は対策であって、最初のエラーの原因を突き止めた証拠ではない**という点である。修正時点では再現試験による解消確認もできていない。

再現性の低い問題では、確認できた現象、推測した原因、実施した対策、未確認の事項を分けて記録し、再発時に調査できる情報を残すことが重要だと学んだ。

## 11. 一度動くコードを書いた後に見直すことで初めて見える問題がある

今回見直したコードの多くは、実装時点では正常に動作していた。しかし、機能追加を優先している間には、ControllerからRepositoryへの直接アクセス、Service間の責務の分散、変数名と型の不一致、不要な条件分岐などが残っていた。

新機能を追加せず既存コードを見直す時間を設けたことで、こうした問題を整理できた。一方、短い処理や一本道で読みやすいメソッドは、無理に分割せず維持した。

今回の学びは、**リファクタリングとは単にコードを短くすることではなく、各処理の責務と意図を明確にする作業である**ということだった。動作しているコードについても「なぜこのクラスにこの処理があるのか」を確認し、変更の効果が明確な箇所から改善していきたい。
