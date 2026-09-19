document.querySelectorAll(".display-language-link").forEach(link => {

    link.addEventListener("click", function(event) {

        event.preventDefault();

        // 現在のURLを取得
        const url = new URL(window.location.href);

        // langを追加・変更
        url.searchParams.set("lang", this.dataset.lang);

        // 変更後のURLへ遷移
        window.location.href = url.toString();
    });
});