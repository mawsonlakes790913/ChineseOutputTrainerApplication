# 003言語設定機能の実装で学んだこと

## 言語設定機能の実装で学んだこと

## 1. サイト表示言語と学習対象言語は分けて考える

今回の実装では、「言語設定」として以下の2種類を扱った。

* **サイト表示言語**

  * 日本語
  * English
  * 简体中文
  * 繁體中文
* **学習対象言語**

  * MAINLAND（大陸普通話）
  * TAIWAN（台湾華語・國語）

どちらも「言語を切り替える」という点では同じだが、役割も実装方法も異なる。

### サイト表示言語

サイト上の説明文、ボタン、メニューなどの表示を切り替える。

```text
?lang=en
    ↓
LocaleChangeInterceptor
    ↓
Localeを変更
    ↓
SessionLocaleResolver
    ↓
LocaleをSessionに保持
    ↓
messages_en.properties
```

Spring MVCが持つLocaleの仕組みを利用できる。

### 学習対象言語

実際にどちらの中国語を学習するのかを表すアプリケーション独自の状態になる。

```text
?languageVariant=MAINLAND
    ↓
LanguageVariant.MAINLAND
    ↓
Sessionに保存
```

こちらはSpring MVCのLocaleとは関係がないため、自分で状態を管理する。

同じ「言語」という名前でも、

```text
サイトを何語で表示するか
≠
何語を学習するか
```

という違いがある。

この違いを明確に分離して扱う必要があることを学んだ。

---

# 2. 多言語対応はHTMLに直接文字列を書かない

当初のHTMLには、

```html
<h1>瞬間中国語作文</h1>
```

のように固定文言を直接記述していた。

多言語対応では、これを以下のように変更する。

```html
<h1 th:text="#{home.title}">
    瞬間中国語作文
</h1>
```

そして実際の文字列をpropertiesへ移動する。

```properties
home.title=瞬間中国語作文
```

英語では、

```properties
home.title=Instant Chinese Sentence Production
```

繁体字では、

```properties
home.title=中文快速造句
```

のように同じキーを使用する。

つまりHTML側は、

```text
home.title
```

というキーだけを参照し、実際にどの文字列を表示するかはLocaleによって決定される。

```text
HTML
  ↓
#{home.title}
  ↓
現在のLocale
  ├─ ja    → messages.properties
  ├─ en    → messages_en.properties
  ├─ zh_CN → messages_zh_CN.properties
  └─ zh_TW → messages_zh_TW.properties
```

この構成にすると、HTMLの構造と表示する言語を分離できる。

一方、`中文造句工坊` のように全言語で共通して使用するブランド名については、propertiesへ移動せず、そのままHTML側に残すことにした。

---

# 3. LocaleResolverの役割

Spring MVCで表示言語を切り替える際には、現在どのLocaleを使用しているのかを管理する必要がある。

今回使用したのが、

```java
SessionLocaleResolver
```

である。

```java
@Bean
LocaleResolver localeResolver() {

    SessionLocaleResolver resolver = new SessionLocaleResolver();

    resolver.setDefaultLocale(Locale.JAPANESE);

    return resolver;
}
```

`SessionLocaleResolver` を使用すると、選択したLocaleをHTTPセッションに保持できる。

例えば一度、

```text
?lang=en
```

によって英語へ変更すると、その後別のページへ移動してもSessionに保存されたLocaleを利用できる。

また、

```java
resolver.setDefaultLocale(Locale.JAPANESE);
```

によって、Localeがまだ選択されていない場合のデフォルトを日本語としている。

---

# 4. LocaleChangeInterceptorの役割

LocaleをSessionへ保存するだけでなく、ユーザーから送られてきたリクエストを検知してLocaleを変更する仕組みも必要になる。

そこで使用したのが、

```java
LocaleChangeInterceptor
```

である。

```java
@Bean
LocaleChangeInterceptor localeChangeInterceptor() {

    LocaleChangeInterceptor interceptor =
            new LocaleChangeInterceptor();

    interceptor.setParamName("lang");

    return interceptor;
}
```

```java
interceptor.setParamName("lang");
```

とすることで、

```text
?lang=en
```

の `lang` をLocale変更用のパラメータとして扱える。

例えば、

```text
http://localhost:8080/?lang=en
```

へアクセスすると、

```text
リクエスト
    ↓
LocaleChangeInterceptor
    ↓
lang=en を検知
    ↓
Localeを英語へ変更
```

という処理が行われる。

---

# 5. Interceptorとは何か

今回 `LocaleChangeInterceptor` を使用したことで、Spring MVCのInterceptorについて学んだ。

Interceptorは、

**HTTPリクエストがControllerに届く前後に共通処理を挟む仕組み**

である。

今回の場合は、

```text
HTTP Request
    ↓
LocaleChangeInterceptor
    ↓
Controller
```

となり、Controllerへ到達する前に `lang` パラメータを確認してLocaleを変更する。

Controllerごとに、

```java
if (lang.equals("en")) {
    ...
}
```

のような処理を書く必要はない。

表示言語変更のように、複数のリクエストに対して共通して行いたい処理をControllerの外側に置くことができる。

---

# 6. WebMvcConfigurerとInterceptorRegistry

Spring MVCの設定をJava Configでカスタマイズするため、今回の `LocaleConfig` では、

```java
@Configuration
public class LocaleConfig implements WebMvcConfigurer {
```

として `WebMvcConfigurer` を実装した。

そして、

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(localeChangeInterceptor());
}
```

によって `LocaleChangeInterceptor` を登録した。

ここで最初は、

```java
InterceptorRegistry registry
```

の意味を十分理解できていなかった。

`InterceptorRegistry` は型で、`registry` は引数名になる。

```text
InterceptorRegistry → 型
registry            → 引数名
```

そして、

```java
registry.addInterceptor(localeChangeInterceptor());
```

では、

```text
localeChangeInterceptor()
    ↓
LocaleChangeInterceptorのインスタンスを取得
    ↓
registry.addInterceptor(...)
    ↓
Spring MVCへInterceptorを登録
```

という処理を行っている。

Beanを作成しただけではInterceptorとしてHTTPリクエストに適用されず、Spring MVCへ登録する必要があることを学んだ。

---

# 7. `#locale.language` だけでは中国語を区別できない

Thymeleafでは現在のLocaleを、

```text
#locale
```

から参照できる。

日本語と英語については、

```html
<span th:if="${#locale.language == 'ja'}">日本語</span>
<span th:if="${#locale.language == 'en'}">English</span>
```

で判定できる。

しかし、中国語では、

```text
zh_CN
zh_TW
```

のどちらもlanguage部分は、

```text
zh
```

になる。

そのため、

```text
#locale.language
```

だけでは簡体字と繁体字を区別できない。

そこで、

```html
<span th:if="${#locale.toString() == 'zh_CN'}">简体中文</span>
<span th:if="${#locale.toString() == 'zh_TW'}">繁體中文</span>
```

とした。

```text
#locale.language
    ↓
zh

#locale.toString()
    ↓
zh_CN / zh_TW
```

Localeには言語だけでなく地域も含まれるため、必要な粒度によって参照方法を変える必要がある。

---

# 8. 日本語だけ正常に表示されなかった

表示言語切り替えを実装したところ、

```text
?lang=en
?lang=zh_CN
?lang=zh_TW
```

については正常に動作した。

一方、

```text
?lang=ja
```

では、日本語を指定しているにもかかわらず英語が表示された。

原因は、Springのメッセージ解決時にシステム側のLocaleへのフォールバックが発生していたことだった。

そこで `application.yml` に、

```yaml
spring:
  messages:
    fallback-to-system-locale: false
```

を追加した。

これによってシステムLocaleへのフォールバックを無効化し、指定したLocaleとデフォルトの `messages.properties` を基準にメッセージを解決するようにした。

修正後、

```text
?lang=ja
```

で正常に日本語が表示された。

今回の問題から、アプリケーション側でデフォルトLocaleを設定していても、メッセージ解決時のフォールバック設定が表示結果へ影響する場合があることを学んだ。

---

# 9. 学習対象言語にはLocaleを使用しない

サイト表示言語では、

```text
?lang=ja
    ↓
LocaleChangeInterceptor
    ↓
SessionLocaleResolver
```

というSpring MVCの仕組みを使用した。

一方、学習対象言語は、

```text
?languageVariant=MAINLAND
    ↓
LanguageVariant.MAINLAND
    ↓
Sessionに保存
```

という独自の処理にした。

`LocaleChangeInterceptor` はあくまでLocaleを変更する仕組みであり、

```text
MAINLAND
TAIWAN
```

のようなアプリケーション独自の学習モードを管理するものではない。

そこで、

```java
public enum LanguageVariant {
    MAINLAND,
    TAIWAN
}
```

として学習対象言語をEnumで表現した。

表示言語と学習対象言語を別々の仕組みで管理することで、それぞれの役割を混同せずに実装できる。

---

# 10. `@RequestParam` からEnumへ変換できる

学習対象言語のControllerでは、

```java
@GetMapping("/language-variant")
public String changeLanguageVariant(
        @RequestParam LanguageVariant languageVariant,
        HttpSession session) {
```

とした。

例えば、

```text
/language-variant?languageVariant=MAINLAND
```

というリクエストを送ると、

```java
@RequestParam LanguageVariant languageVariant
```

によって `MAINLAND` を `LanguageVariant` 型として受け取ることができる。

そのため、自分で、

```java
LanguageVariant.valueOf(...)
```

などを呼び出して文字列からEnumへ変換する処理を書く必要がない。

受け取った値は、

```java
session.setAttribute("languageVariant", languageVariant);
```

としてSessionへ保存した。

---

# 11. Sessionに保存済みの値との比較

Controllerでは現在の学習対象言語をSessionから取得する。

```java
LanguageVariant current =
        (LanguageVariant) session.getAttribute("languageVariant");
```

そして、

```java
if (languageVariant == current) {
    return "redirect:/";
}
```

として、現在と同じ言語が指定された場合は変更処理を行わない。

Enum同士なので、

```java
languageVariant == current
```

で比較できる。

Sessionから値を取得する場合は戻り値が `Object` になるため、

```java
(LanguageVariant)
```

によるキャストが必要になる点も確認できた。

---

# 12. 学習対象言語と取得済み問題セットの整合性

学習対象言語の切り替えについて考えた際、Sessionに言語だけを保存すれば終わりではないことに気づいた。

例えば、MAINLANDの問題を学習中に、

```text
問題1
 ↓
問題2
 ↓
問題3
 ↓
問題4
```

TAIWANへ変更した場合、そのまま既存の問題セットを使用すると、

```text
現在の学習対象言語
TAIWAN

Sessionに取得済みの問題
MAINLAND
```

という不整合が発生する可能性がある。

そのため、学習対象言語を変更した場合はHomeへ戻す構成とした。

さらに、今後問題セットをSessionへ保存するようになった場合には、

```java
session.removeAttribute("studyQuestions");
session.removeAttribute("studyCurrentPage");
```

のように、現在の学習状態も破棄する必要がある。

今回の設計から、Sessionで複数の状態を管理する場合、

**あるSession値を変更したことで、別のSession値が古い状態にならないか**

まで考える必要があることを学んだ。

---

# 13. `data-*` 属性を使ってJavaScriptへ値を渡す

学習対象言語を変更すると現在のトレーニングへ影響するため、確認ダイアログを表示することにした。

HTML側では、

```html
<a class="dropdown-item language-variant-link"
   th:href="@{/language-variant(languageVariant='MAINLAND')}"
   th:data-confirm-message="#{header.studyLanguage.confirmMainland}">
    🇨🇳 普通话
</a>
```

として確認メッセージを `data-confirm-message` に保存する。

JavaScriptでは、

```javascript
const message = this.dataset.confirmMessage;
```

として取得できる。

つまり、

```text
data-confirm-message
        ↓
dataset.confirmMessage
```

という対応になる。

確認メッセージ自体はpropertiesで管理しているため、表示言語によって内容を切り替えることもできる。

```properties
header.studyLanguage.confirmMainland=学習言語を普通話に切り替えますか？...
```

HTMLへJavaScriptの確認文を直接埋め込まず、

```text
properties
    ↓
Thymeleaf
    ↓
data-* 属性
    ↓
JavaScript
```

という形で渡す方法を学んだ。

---

# 14. `event.preventDefault()` でリンク遷移を止める

確認ダイアログでは、

```javascript
document.querySelectorAll('.language-variant-link').forEach(link => {
    link.addEventListener('click', function(event) {

        const message = this.dataset.confirmMessage;

        if (!confirm(message)) {
            event.preventDefault();
        }
    });
});
```

としている。

`confirm()` でキャンセルされた場合、

```javascript
event.preventDefault();
```

を実行することで、本来発生するリンク遷移を中止できる。

つまり、

```text
リンクをクリック
    ↓
clickイベント
    ↓
confirm()
    ↓
OK
    → 通常どおりリンク遷移

キャンセル
    → event.preventDefault()
    → リンク遷移を中止
```

という処理になる。

---

# 15. UIでは設定の重要度によって配置を変える

表示言語と学習対象言語を両方ヘッダーへ配置したところ、ヘッダー内の要素が増えて窮屈になった。

さらに今後、

* ログイン・ログアウト
* ユーザー関連メニュー
* ハンバーガーメニュー

などを追加する可能性もある。

そこで最終的に、

```text
中文造句工坊    学習言語 🇹🇼 國語 ▼    👤    ☰
```

という構成へ整理した。

学習対象言語は、

```text
現在何を学習しているか
```

に直接関係するため、ヘッダー上へ常時表示する。

一方、表示言語は一度設定すれば頻繁に変更するものではないため、ハンバーガーメニュー内へ移動した。

```text
学習対象言語
    ↓
学習内容に直接影響
    ↓
常時表示

表示言語
    ↓
変更頻度が低い
    ↓
ハンバーガーメニュー
```

UIを整理するときは、単純に要素を減らすだけでなく、

**ユーザーが頻繁に確認・変更する必要があるものか**

という観点で配置を考えることが重要だと分かった。

---

# 16. 将来の機能を考慮してUIの「器」だけ作る場合もある

ヘッダーにはユーザーアイコンを追加した。

```html
<button type="button"
        class="btn p-0 text-white border-0"
        aria-label="ユーザー">
    <i class="bi bi-person-circle fs-4"></i>
</button>
```

現時点ではログイン機能やユーザーDBを実装していないため、このアイコンにはまだ機能を持たせていない。

将来的には、

```text
👤
 ├─ マイページ
 ├─ アカウント設定
 └─ ログアウト
```

などを配置できる。

今回のように、後から追加することがほぼ決まっている機能については、現在のUIを整理する段階で配置場所だけ確保しておく方法もある。

---

# 17. 今回の学習内容の整理

今回の言語設定機能の実装では、単に「言語を切り替える」だけでなく、Spring MVCのLocale機能やSession管理、JavaScriptとの連携について確認できた。

特に重要だった点は以下。

### Spring MVC

```text
WebMvcConfigurer
    ↓
Spring MVCの設定をカスタマイズ

LocaleChangeInterceptor
    ↓
?lang=○○ を検知

SessionLocaleResolver
    ↓
LocaleをSessionへ保持
```

### Thymeleaf / properties

```text
#{home.title}
    ↓
現在のLocale
    ↓
messages*.properties
```

### 学習対象言語

```text
LanguageVariant
    ↓
MAINLAND / TAIWAN
    ↓
Sessionへ保存
    ↓
今後の問題取得条件として使用
```

### JavaScript

```text
properties
    ↓
data-confirm-message
    ↓
dataset.confirmMessage
    ↓
confirm()
    ↓
event.preventDefault()
```

また、今回特に重要だったのは、

**似たように見える設定でも、役割が異なれば管理方法も分ける**

という点だった。

サイト表示言語はSpring MVCのLocaleとして扱い、学習対象言語はアプリケーション独自の `LanguageVariant` として扱う。

さらに、学習対象言語のようなアプリケーション全体の状態を変更するときは、その値だけを見るのではなく、Session内に保存されている問題セットなど、関連する状態との整合性も考える必要がある。

今後通常学習や復習機能を実装する際にも、

```text
現在のLanguageVariant
    ↓
Service
    ↓
Repository
    ↓
language_variantを条件として問題取得
```

という形で、今回実装した学習対象言語の状態を利用していく。
