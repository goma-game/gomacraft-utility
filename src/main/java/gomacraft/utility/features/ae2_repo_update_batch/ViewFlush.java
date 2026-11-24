package gomacraft.utility.features.ae2_repo_update_batch;

/**
 * 済んでいない並べ直し(ae2_repo_update_batch)を、一覧を読む口で済ませてよいかの決まり(Minecraft に依らない ——
 * 試験はここを見る)。
 *
 * <p>⚠ 済ませてよいのは描画のスレッド(Minecraft のメインのスレッド)だけ。ほかのスレッドから一覧を読まれることがある
 * (EMI の検索の焼き付けのスレッドが、ツールチップの印付けからターミナルの欄のアイテムを読む —— goma-org/gomabase#2295 の
 * 3 回目の確かめのログ)。そこで並べ直すと、描画のスレッドの並べ直しと同じ一覧を同時に並べ替え、AE2 の通信の処理まで
 * 「Comparison method violates its general contract」で落ちる。ほかのスレッドには並べ直す前の一覧を返し、印は残して
 * 描画のスレッドの次の読みで済ませる。
 */
public final class ViewFlush {
    private ViewFlush() {
    }

    /**
     * @param pending        済んでいない並べ直しがあるか
     * @param onRenderThread 呼んだのが描画のスレッドか
     * @return 今ここで並べ直すなら true(呼ぶ側が印を消して並べ直す)。偽なら印は残す
     */
    public static boolean mayFlush(boolean pending, boolean onRenderThread) {
        return pending && onRenderThread;
    }
}
