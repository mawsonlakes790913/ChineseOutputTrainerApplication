# 023-05 ログイン成功時の歓迎メッセージ表示 — 学習ログ

## はじめに

ログイン後の歓迎メッセージを追加する過程で、Spring Securityの認証成功処理とSpring MVCの画面表示処理が、同じリクエストを扱っていても異なる実行段階にあることを理解した。本章では表示機能の実装手順ではなく、リダイレクトをまたぐデータの受け渡しと、実行コンテキストに起因するエラーから得た知見を整理する。

## 1. リダイレクトをまたぐ一時データの扱い

通常のControllerで`Model`に追加した属性は、そのリクエストの画面表示に利用されるものであり、リダイレクト後の別リクエストへ自動的には引き継がれない。そこでSpring MVCには、次のリクエストへ一時的に値を渡す`FlashMap`という仕組みがある。Controllerで使う`RedirectAttributes.addFlashAttribute()`も、これを利用する方法の一つである。

今回必要だったのは、ログイン成功時に生成した情報を、リダイレクト後の画面へ渡すことだった。ただし、`LoginSuccessHandler`の`onAuthenticationSuccess()`はControllerのメソッドではなく、`RedirectAttributes`を引数として受け取れない。この違いから、**同じFlashMapを利用する場合でも、呼び出し元によってアクセス方法が異なる**ことを学んだ。

また、歓迎文そのものを渡すのではなく、`login.success`というメッセージキーと`loginId`を渡す設計にした。表示時に`messages.properties`を参照することで、日本語・簡体字・繁体字それぞれの文言を同じ画面構造から表示できる。画面間で受け渡す情報と、実際の表示文言を分離する考え方も重要だった。

## 2. Spring SecurityとSpring MVCの実行位置の違い

最初は`RequestContextUtils.getFlashMapManager(request)`を使って`FlashMapManager`を取得しようとしたが、戻り値が`null`となり、`saveOutputFlashMap()`の呼び出しで`NullPointerException`が発生した。

原因は、`RequestContextUtils.getFlashMapManager()`が新しいマネージャーを生成する処理ではなく、Spring MVCの`DispatcherServlet`によってリクエストへ設定されたものを取得する処理だったことにある。

認証成功時の`LoginSuccessHandler`はSpring SecurityのFilter処理の中で実行される。一方、参考にしたInterceptorは`DispatcherServlet`側の処理で動作する。そのため、Interceptorで利用できたリクエスト属性が、認証成功ハンドラでも利用できるとは限らなかった。

この経験から、**参考コードのAPIや記述方法だけでなく、そのコードがどの処理段階で実行されるかを確認する必要がある**と分かった。特に`null`が返る原因を考える際には、値の設定漏れだけでなく、そもそも設定される前のタイミングで参照していないかを調べることが重要である。

## 3. SessionFlashMapManagerの利用と画面遷移の前提

修正では、`RequestContextUtils`経由でマネージャーを取得する方法をやめ、`SessionFlashMapManager`を利用してFlashMapをセッションへ保存した。これにより、`DispatcherServlet`によるリクエスト属性の設定を前提とせず、認証成功ハンドラから一時的な情報を保存できた。

既存の`LoginSuccessHandler`は、学習対象言語と発音表記をセッションへ設定した後、`SavedRequestAwareAuthenticationSuccessHandler`の処理へ制御を戻していた。歓迎メッセージの追加後も、このログイン時の設定処理と親クラスによる遷移処理を維持した。

ここで注意が必要なのは、`SavedRequestAwareAuthenticationSuccessHandler`によるログイン後の遷移先が常にHome画面とは限らないことである。ログイン前にアクセスしようとしていたページへ戻る場合もあるため、FlashMapへ値を保存できたことと、Home画面の歓迎メッセージが必ず表示されることは同義ではない。

今回の実装では遷移先を固定していない。したがって、**データの受け渡しが成功する条件と、そのデータを使う画面が表示される条件は分けて考える**必要があると理解した。

## まとめ

今回最も大きかった学びは、Spring SecurityとSpring MVCが連携していても、両者の実行コンテキストは同一ではないという点である。`NullPointerException`の原因を追うことで、`RequestContextUtils`の前提、Filterと`DispatcherServlet`の実行順序、FlashMapの保存方法を具体的に理解できた。

また、歓迎メッセージという小さなUI機能でも、リダイレクト後のデータ受け渡し、多言語表示、ログイン後の遷移先という複数の仕組みが関係する。個々のAPIだけでなく、処理全体の流れを確認する重要性を学んだ。
