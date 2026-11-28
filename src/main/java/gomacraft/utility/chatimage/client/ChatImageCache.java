package gomacraft.utility.chatimage.client;

import com.mojang.blaze3d.platform.NativeImage;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.chatimage.DiscordImageUrl;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 取りにいった画像の作り置き(少しだけ、古いものから捨てる)。主スレッドだけが触る。
 *
 * <p>1 つの URL につき取りにいくのは 1 回(マウスを乗せている間は毎フレーム問われる)。失敗・失効も覚える
 * (失敗だけは {@link #RETRY_MS} の後にもう一度取りにいく)。捨てるとき・機能を止めたとき・ログアウトしたときは、
 * GPU の絵を放し({@code TextureManager.release} が DynamicTexture を閉じ、点の記憶も放す)、途中の取りにいきを
 * 取り消す({@code Future.cancel})。取り消しの後に届いた絵は、{@link #finish} で閉じる。
 */
final class ChatImageCache {
    /**
     * 覚えておく数(絵は長い辺 2048 まで。1 枚で GPU に約 16 MB、DynamicTexture が点を手元にも持つので合わせて約
     * 32 MB × この数)。
     */
    private static final int MAX_ENTRIES = 8;
    /** 同時に取りにいく(待ちを含む)数の上限。超えた分は、空くまで始めない(次のフレームで問い直される)。 */
    private static final int MAX_LOADING = 4;
    private static final long RETRY_MS = 30_000;

    enum State {
        LOADING, READY, EXPIRED, TOO_LARGE, FAILED
    }

    static final class Entry {
        State state = State.LOADING;
        ResourceLocation texture;
        int width;
        int height;
        long failedAt;
        Future<?> task;
    }

    /** 上限で始められなかったときに返す、覚えない「読み込み中」。 */
    private static final Entry WAITING = new Entry();

    private static final AtomicInteger THREADS = new AtomicInteger();
    /** 取りにいく・読むためのスレッド(2 本。共有の背景のスレッドを通信の待ちで塞がない)。 */
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "gomacraft_utility chat image " + THREADS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>(16, 0.75f, true);
    private static int generation;
    private static int textures;

    private ChatImageCache() {
    }

    /** その URL の今の状態。まだなら取りにいき始める。 */
    static Entry get(DiscordImageUrl url) {
        String key = url.fetchUri().toString();
        Entry entry = ENTRIES.get(key);
        if (entry != null) {
            if (entry.state == State.FAILED && System.currentTimeMillis() - entry.failedAt > RETRY_MS) {
                ENTRIES.remove(key);
            } else {
                return entry;
            }
        }
        if (loading() >= MAX_LOADING) {
            return WAITING;
        }
        Entry created = new Entry();
        ENTRIES.put(key, created);
        trim(created);
        int started = generation;
        Minecraft mc = Minecraft.getInstance();
        created.task = IO.submit(() -> {
            NativeImage image = null;
            Throwable error = null;
            try {
                image = ImageFetcher.fetch(url);
            } catch (Throwable t) {
                error = t;
            }
            NativeImage result = image;
            Throwable failure = error;
            // ⚠ 取り消された後でも絵ができていれば、主スレッドの finish が閉じる(ここで捨てると閉じ損ねる)
            mc.execute(() -> finish(key, created, started, result, failure));
        });
        return created;
    }

    private static int loading() {
        int count = 0;
        for (Entry entry : ENTRIES.values()) {
            if (entry.state == State.LOADING) {
                count++;
            }
        }
        return count;
    }

    private static void finish(String key, Entry entry, int started, NativeImage image, Throwable error) {
        entry.task = null;
        // 止めた・捨てた後に届いた絵は使わずに閉じる
        if (started != generation || ENTRIES.get(key) != entry) {
            if (image != null) {
                image.close();
            }
            return;
        }
        if (error != null || image == null) {
            if (error instanceof ImageFetcher.FetchException fetch) {
                entry.state = switch (fetch.failure) {
                    case EXPIRED -> State.EXPIRED;
                    case TOO_LARGE -> State.TOO_LARGE;
                    case FAILED -> State.FAILED;
                };
            } else {
                entry.state = State.FAILED;
            }
            entry.failedAt = System.currentTimeMillis();
            return;
        }
        DynamicTexture texture;
        try {
            texture = new DynamicTexture(image);
        } catch (RuntimeException e) {
            image.close();
            fail(entry);
            return;
        }
        try {
            texture.setFilter(true, false);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(GomacraftUtility.MOD_ID,
                    "chat_image/" + (textures++));
            Minecraft.getInstance().getTextureManager().register(location, texture);
            entry.texture = location;
            entry.width = image.getWidth();
            entry.height = image.getHeight();
            entry.state = State.READY;
        } catch (RuntimeException e) {
            // 登録で落ちたら、絵の ID と点の記憶を閉じる(DynamicTexture#close)
            texture.close();
            fail(entry);
        }
    }

    private static void fail(Entry entry) {
        entry.state = State.FAILED;
        entry.failedAt = System.currentTimeMillis();
    }

    /** 古いものから捨てる。読み込み中のものは後回しにし、それしか残らなければ取り消して捨てる。 */
    private static void trim(Entry keep) {
        for (boolean includeLoading : new boolean[] {false, true}) {
            Iterator<Map.Entry<String, Entry>> it = ENTRIES.entrySet().iterator();
            while (ENTRIES.size() > MAX_ENTRIES && it.hasNext()) {
                Entry eldest = it.next().getValue();
                if (eldest == keep || (!includeLoading && eldest.state == State.LOADING)) {
                    continue;
                }
                it.remove();
                release(eldest);
            }
        }
    }

    private static void release(Entry entry) {
        if (entry.task != null) {
            entry.task.cancel(true);
            entry.task = null;
        }
        if (entry.texture != null) {
            Minecraft.getInstance().getTextureManager().release(entry.texture);
            entry.texture = null;
        }
    }

    /** 全部を捨てる(機能を止めたとき・ログアウトしたとき)。途中の取りにいきも取り消す。 */
    static void clear() {
        generation++;
        for (Entry entry : ENTRIES.values()) {
            release(entry);
        }
        ENTRIES.clear();
    }
}
