package gomacraft.utility.chatimage.client;

import com.mojang.blaze3d.platform.NativeImage;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.chatimage.DiscordImageUrl;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 取りにいった画像の作り置き(少しだけ、古いものから捨てる)。主スレッドだけが触る。
 *
 * <p>1 つの URL につき取りにいくのは 1 回(マウスを乗せている間は毎フレーム問われる)。失敗・失効も覚える
 * (失敗だけは {@link #RETRY_MS} の後にもう一度取りにいく)。捨てるとき・機能を止めたときは、GPU の絵を放す
 * ({@code TextureManager.release} が DynamicTexture を閉じ、点の記憶も放す)。
 */
final class ChatImageCache {
    /** 覚えておく数(絵は長い辺 2048 まで。多くて約 16 MB × この数)。 */
    private static final int MAX_ENTRIES = 8;
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
    }

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
        Entry created = new Entry();
        ENTRIES.put(key, created);
        trim();
        int started = generation;
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> {
            try {
                return ImageFetcher.fetch(url);
            } catch (ImageFetcher.FetchException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, IO).whenComplete((image, error) -> mc.execute(() -> finish(key, created, started, image, error)));
        return created;
    }

    private static void finish(String key, Entry entry, int started, NativeImage image, Throwable error) {
        // 止めた・捨てた後に届いた絵は使わずに閉じる
        if (started != generation || ENTRIES.get(key) != entry) {
            if (image != null) {
                image.close();
            }
            return;
        }
        if (error != null || image == null) {
            Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            if (cause instanceof ImageFetcher.FetchException fetch) {
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
        try {
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(GomacraftUtility.MOD_ID,
                    "chat_image/" + (textures++));
            Minecraft.getInstance().getTextureManager().register(location, texture);
            entry.texture = location;
            entry.width = image.getWidth();
            entry.height = image.getHeight();
            entry.state = State.READY;
        } catch (RuntimeException e) {
            image.close();
            entry.state = State.FAILED;
            entry.failedAt = System.currentTimeMillis();
        }
    }

    private static void trim() {
        Iterator<Map.Entry<String, Entry>> it = ENTRIES.entrySet().iterator();
        while (ENTRIES.size() > MAX_ENTRIES && it.hasNext()) {
            Entry eldest = it.next().getValue();
            it.remove();
            release(eldest);
        }
    }

    private static void release(Entry entry) {
        if (entry.texture != null) {
            Minecraft.getInstance().getTextureManager().release(entry.texture);
            entry.texture = null;
        }
    }

    /** 全部を捨てる(機能を止めたとき)。途中の取りにいきの結果も捨てる。 */
    static void clear() {
        generation++;
        for (Entry entry : ENTRIES.values()) {
            release(entry);
        }
        ENTRIES.clear();
    }
}
