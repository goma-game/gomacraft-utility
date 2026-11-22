# gomacraft-utility

gomacraft(ATM10 のサーバ)のための、自作のユーティリティ Mod。身内で遊ぶための便利さと不具合の直しに絞り、新しい要素(ブロック・アイテム・ワールドのデータ)は足さない。

## 方針

- jar は 1 つで、サーバとクライアントの両方に入れる。この Mod の無いクライアントでも接続できる(通信は optional)。
- 機能ごとに、対象の Mod が入っているか・版が合うかで有効を決め、設定で個別に切れる。当て先が無い・壊れた機能はそれだけが止まり、ゲームの起動は止めない。
- 起動時に、機能ごとの状態を 1 行ログに出す(例: `gomacraft_utility: features ae2_search=on compass=off(mod missing: compass)`)。
- Minecraft と NeoForge の版は gomacraft に合わせる(版の正は配布する側の宣言)。

## 配布

jar と sha256 は tag ごとの GitHub Release に置き、配布する側がその release を宣言して取り込む。

## ライセンス

MIT(`LICENSE`)。他の Mod のコードは含めない。
