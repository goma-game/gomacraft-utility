# gomacraft-utility

gomacraft サーバー向けのユーティリティ Mod です。NeoForge 版（ATM10 環境向け、オリジナル）と、バニラベースのサーバー向けに一部機能を移植した Fabric 版を提供しています。新しいブロック・アイテム・ワールドデータなどの追加は行いません。

## 主な機能

- **検索・AE2 / EMI 連携（NeoForge 版）**: 英語名検索、検索履歴、EMI 上での在庫数表示、クリックによる取り出し・クラフトなど
- **Discord 連携・チャット機能（NeoForge / Fabric 共通）**: スクリーンショットの Discord 投稿、チャット内の Discord 画像プレビュー・ゲーム内拡大表示、チャット内 URL（http / https）のリンク化
- **軽量化・不具合修正（NeoForge 版）**: AE2 ターミナルの並べ替え最適化、TTF フォント読み込み保護など

## Fabric 版（`fabric/`）

Minecraft 26.2 のバニラベース環境向けに、NeoForge 版から Discord 連携・チャット関連機能（スクリーンショット投稿・Discord 画像表示・URL リンク化）のみを移植した版です。

- 配布ファイルは `gomacraft_utility-fabric-<バージョン>.jar` として GitHub Release に公開します（NeoForge 版の `gomacraft_utility-<バージョン>.jar` とは別タグで管理します）。

## 方針

- ローダー（NeoForge / Fabric）ごとにサーバー・クライアント共通の単一 jar として配布します。通信処理はオプショナルなため、本 Mod を導入していないクライアントからも接続できます。
- 各機能は対象 Mod の有無やバージョン適合性に応じて動作し、設定画面から個別に切り替えられます（既定で無効な機能もあります）。対象 Mod がない場合やエラーが発生した場合でも該当機能のみが停止し、ゲームの起動自体は妨げません。
- 起動時に各機能の状態をログへ 1 行で出力します（例: `gomacraft_utility: features ae2_search=on compass=off(mod missing: compass)`）。
- Minecraft およびローダー（NeoForge / Fabric）のバージョンは各サーバーの構成に合わせます。

## 配布

ビルド済みの jar と sha256 チェックサムはタグごとの GitHub Release に公開します。

## ライセンス

MIT ライセンス（[LICENSE](LICENSE)）の下で公開しています。一部に他 Mod から移植した処理を含んでおり、該当箇所のソースコードに出典・バージョン・ライセンスを明記しています。
