package gomacraft.utility.screenshot.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gomacraft.utility.Features;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import gomacraft.utility.screenshot.ScreenshotRules;
import gomacraft.utility.screenshot.ScreenshotRules.Result;
import gomacraft.utility.screenshot.ScreenshotRules.Target;
import gomacraft.utility.screenshot.ShareSettings;
import gomacraft.utility.screenshot.net.ScreenshotPayloads;
import gomacraft.utility.mixin.screenshot_share.ConnectionAccessor;
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Util;

/**
 * クライアントの側: 撮ったときのチャット欄の行に「投稿」の文字を足し、押された 1 枚だけをサーバへ送る。
 *
 * <p>⚠ 同意は 1 枚ごとのクリック。押さなければ何も送らない。送れるのはこの起動の中で撮ったものだけ
 * (押した文字は乱数の札を持ち、札 → ファイルの表は手元のメモリだけ。1 枚 1 回)。
 * 文字を出すのは、つないだサーバが受ける口を開いているときだけ(シングルや Mod の無いサーバでは出ない)。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写した。違うのは口だけ: 撮ったときの行は、forge 版の
 * {@code ScreenshotEvent} の代わりに、バニラの {@code Screenshot.grab} の知らせの受け手を包んで足す
 * ({@code mixin.screenshot_share.ScreenshotMixin} —— Fabric API には撮ったときの event が無い)。手元のコマンドと
 * tick・通信は Fabric API の口。
 */
public final class ShareClient {
    /**
     * 押した文字が打つ、手元だけのコマンド(Fabric API がチャットのクリックの {@code sendUnattendedCommand} を手元の
     * コマンドへ回す —— fabric-command-api-v2 の ClientPacketListenerMixin)。
     */
    static final String COMMAND = "gomacraft_utility_screenshot";
    /** 札の数の上限(古いものから忘れる)。 */
    private static final int MAX_TOKENS = 32;
    /**
     * 1 tick に送る切れ端の上限。⚠ 実際の絞りは送りの列の空き: 各 tick の頭の
     * {@code channel().bytesBeforeUnwritable()}(既定の高水位 64 KiB)に収まる分だけを積む。だから速い回線でも
     * 1 tick に約 2 個(約 1.2 MB/s)、遅い回線では列が空くまで積まない(keepalive などが並ぶのは多くて
     * 約 64 KiB の後ろ)。⚠ 固定の 300 KB/s に絞っていたときは、7.8 MB の PNG に 26 秒かかった
     * (mcdev の実測 —— goma-org/gomabase#2295 の 12)。
     */
    private static final int MAX_CHUNKS_PER_TICK = 8;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 札 → ファイル。⚠ 札を作るのは画像を書いた thread(バニラの io の pool)なので、触るときは TOKENS で鍵をかける。 */
    private static final Map<String, Path> TOKENS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
            return size() > MAX_TOKENS;
        }
    };

    /** 送っている途中の 1 枚(主スレッドだけが触る)。 */
    private static Sending sending;
    /** 画像を読んでいる途中か(主スレッドだけが触る)。 */
    private static boolean loading;
    /** 送り終えて返事を待っている送り → 札(主スレッドだけが触る)。 */
    private static final Map<Integer, String> AWAITING = new LinkedHashMap<>();
    /** 止めた・動かし直した回数(その前に始めた読み込みの終わりを捨てる。主スレッドだけが触る)。 */
    private static int generation;

    /** 各自の手元の設定(普通に押したときの行き先・選ぶ画面を出す修飾キー)。撮った thread からも読む。 */
    private static volatile ShareSettings settings = ShareSettings.defaults();
    /** つないだサーバが受けるか(主スレッドが tick ごとに書き、撮った thread が読む)。 */
    private static volatile boolean accepting;

    private record Sending(int upload, byte[] data, int next, String token, Target target) {
    }

    private ShareClient() {
    }

    public static void init() {
        // 設定は手元の設定(gomacraft_utility-client.toml の [screenshot_share])に置く。設定の画面にも並ぶ
        ClientSettings client = Settings.client();
        client.register(ShareSettings.DEFAULT_TARGET);
        client.register(ShareSettings.CHOOSER_MODIFIER);
        client.onChange(() -> Features.guard(ScreenshotFeatures.SHARE, () -> {
            settings = ShareSettings.from(client);
        }));
        // 返事の受け手。受け手を登録した channel だけがサーバに名乗られる(サーバの canSend がこれを見る)
        ClientPlayNetworking.registerGlobalReceiver(ScreenshotPayloads.Outcome.TYPE,
                (outcome, context) -> Features.guard(ScreenshotFeatures.SHARE, () -> onOutcome(outcome)));
        // コマンドはワールドに入るたびに登録し直される。止めている間に入っても、動かし直したら効くように、止めているかは
        // 見ない(コマンドの中身は guard で分ける)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> Features.registry()
                .guardLoaded(ScreenshotFeatures.SHARE, () -> dispatcher.register(command())));
        ClientTickEvents.END_CLIENT_TICK.register(
                mc -> Features.guard(ScreenshotFeatures.SHARE, ShareClient::tick));
        // 設定の画面で止めた・動かし直した: 送っている途中・返事の待ちを捨てる(止めている間に終わった読み込みや返事は
        // guard の中で受けられず、残すと「送っている途中」のまま次を断り続ける)。動かし直したら設定を読み直す
        // (止めている間の設定の変更は、上の onChange が受けていない)
        Features.registry().onActiveChange(ScreenshotFeatures.SHARE, active -> {
            generation++;
            sending = null;
            loading = false;
            AWAITING.clear();
            if (active) {
                settings = ShareSettings.from(client);
            }
        });
    }

    /** つないだサーバが受けるか(専用サーバで、口を開いているときだけ)。 */
    static boolean serverAccepts() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        return connection != null && !mc.hasSingleplayerServer()
                && ClientPlayNetworking.canSend(ScreenshotPayloads.Chunk.TYPE);
    }

    /**
     * バニラの {@code Screenshot.grab} の知らせの受け手を包む(mixin から呼ぶ)。保存できた知らせ
     * ({@code screenshot.success})にだけ「投稿」の文字を足す。⚠ 受け手を呼ぶのは画像を書いた thread(io の pool)。
     * 包んだ受け手そのものの例外は飲まない(guard の外で呼ぶ)。
     */
    public static Consumer<Component> wrap(Consumer<Component> delegate) {
        return message -> delegate.accept(Features.guard(ScreenshotFeatures.SHARE, () -> decorate(message), message));
    }

    private static Component decorate(Component message) {
        Optional<Path> file = savedFile(message);
        if (file.isEmpty() || !accepting) {
            return message;
        }
        String token = HexFormat.of().toHexDigits(RANDOM.nextInt());
        synchronized (TOKENS) {
            TOKENS.put(token, file.get());
        }
        return message.copy().append(" ").append(button(token));
    }

    /** バニラの「スクリーンショットを … として保存しました」の、名前の部品が開くファイル。ほかの知らせは空。 */
    static Optional<Path> savedFile(Component message) {
        if (message == null || !(message.getContents() instanceof TranslatableContents t)
                || !"screenshot.success".equals(t.getKey())) {
            return Optional.empty();
        }
        for (Object arg : t.getArgs()) {
            if (arg instanceof Component part && part.getStyle().getClickEvent() instanceof ClickEvent.OpenFile open) {
                return Optional.of(open.file().toPath());
            }
        }
        return Optional.empty();
    }

    private static Component button(String token) {
        // 説明の修飾キーは、各自の設定のもの(固定の「Ctrl」にしない)
        Component hover = Component.translatableWithFallback("gomacraft_utility.screenshot.button.hover",
                "Post this screenshot to Discord under your name. Hold %s to choose the channel.",
                settings.chooserModifier().label);
        return Component.translatableWithFallback("gomacraft_utility.screenshot.button", "[Post to Discord]")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/" + COMMAND + " " + token))
                        .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal(COMMAND)
                .then(ClientCommands.argument("token", StringArgumentType.word())
                        .executes(context -> {
                            String token = StringArgumentType.getString(context, "token");
                            if (Features.registry().isFailed(ScreenshotFeatures.SHARE)) {
                                say("gomacraft_utility.settings.failed", "Broken and stopped.");
                                return 1;
                            }
                            if (Features.registry().isSuspended(ScreenshotFeatures.SHARE)) {
                                // 止める前に出たボタンを押した: 黙って何もしないと、壊れたように見える
                                say("gomacraft_utility.screenshot.stopped",
                                        "Posting screenshots is turned off in the settings.");
                                return 1;
                            }
                            Features.guard(ScreenshotFeatures.SHARE, () -> choose(token));
                            return 1;
                        }));
    }

    /** 修飾キーを押しながらなら行き先を選ぶ画面を出し、そうでなければ設定の既定の行き先へ送る。 */
    private static void choose(String token) {
        if (!modifierDown()) {
            post(token, settings.defaultTarget());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // チャット欄のクリックの処理が終わってから開く(チャット欄が閉じる順と重ねない)
        mc.execute(() -> Features.guard(ScreenshotFeatures.SHARE, () -> mc.gui.setScreen(new TargetScreen(
                target -> Features.guard(ScreenshotFeatures.SHARE, () -> post(token, target))))));
    }

    private static boolean modifierDown() {
        Minecraft mc = Minecraft.getInstance();
        return switch (settings.chooserModifier()) {
            case CTRL -> mc.hasControlDown();
            case ALT -> mc.hasAltDown();
        };
    }

    private static void post(String token, Target target) {
        // ⚠ 札は受け付けられる(ACCEPTED)まで残す(COOLDOWN・BUSY・BROKEN のあとで押し直せるように)
        Path file;
        synchronized (TOKENS) {
            file = TOKENS.get(token);
        }
        if (file == null) {
            say("gomacraft_utility.screenshot.expired", "This screenshot can no longer be posted.");
            return;
        }
        if (sending != null || loading || AWAITING.containsValue(token)) {
            say("gomacraft_utility.screenshot.busy", "Another screenshot is still being sent. Try again shortly.");
            return;
        }
        if (!serverAccepts()) {
            say("gomacraft_utility.screenshot.unavailable", "This server does not accept screenshots.");
            return;
        }
        loading = true;
        int started = generation;
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> load(file), Util.backgroundExecutor())
                .whenComplete((data, error) -> mc.execute(() -> {
                    // 止める・動かし直すより前に始めた読み込みの終わりは捨てる(印はそのときに下ろした。ここで触ると、
                    // 動かし直した後の新しい読み込みの印を下ろし、送りを上書きする)
                    if (started != generation) {
                        return;
                    }
                    // 止めている間に終わっても、読み込みの印は下ろす(guard の外。残すと次の投稿を断り続ける)
                    loading = false;
                    Features.guard(ScreenshotFeatures.SHARE, () -> {
                    if (error != null || data == null || data.isEmpty()) {
                        say("gomacraft_utility.screenshot.unreadable",
                                "Could not read the screenshot, or it is too large to send.");
                        return;
                    }
                    if (!serverAccepts()) {
                        say("gomacraft_utility.screenshot.unavailable", "This server does not accept screenshots.");
                        return;
                    }
                    sending = new Sending(RANDOM.nextInt(Integer.MAX_VALUE), data.get(), 0, token, target);
                    say("gomacraft_utility.screenshot.sending", "Sending the screenshot...");
                    });
                }));
    }

    /**
     * 送る中身。PNG のまま上限を超えるときだけ JPEG に作り直す。それでも超える・読めないなら空。
     *
     * <p>⚠ 上限の内の PNG は作り直さない: 4K の PNG(7.8 MB)を JPEG(0.92)にしても 2.3 MB にしかならず、
     * 作り直しに 3〜4 秒かかる(実測)。⚠ PNG のまま送るほうが速いのは、送りが約 1.5 MB/s 以上出る回線のとき
     * (LAN・速い上り)。遅い上りでは JPEG のほうが早く着くが、速さを測らずに選ぶ形は持たない(上限の内は PNG)。
     */
    static Optional<byte[]> load(Path file) {
        try {
            byte[] png = Files.readAllBytes(file);
            if (png.length <= ScreenshotRules.MAX_BYTES && ScreenshotRules.check(png).isEmpty()) {
                return Optional.of(png);
            }
            BufferedImage source = ImageIO.read(file.toFile());
            if (source == null) {
                return Optional.empty();
            }
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(source, 0, 0, null);
            for (float quality : new float[] {0.9f, 0.75f, 0.6f}) {
                byte[] jpeg = jpeg(rgb, quality);
                if (ScreenshotRules.check(jpeg).isEmpty()) {
                    return Optional.of(jpeg);
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static void tick() {
        accepting = serverAccepts();
        if (Minecraft.getInstance().getConnection() == null) {
            // 抜けたら返事は来ない(待ちを残すと、その札が「送っている」のまま押せなくなる)
            AWAITING.clear();
        }
        Sending current = sending;
        if (current == null) {
            return;
        }
        if (!serverAccepts()) {
            sending = null;
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        int total = current.data().length;
        int count = ScreenshotRules.chunkCount(total);
        int next = current.next();
        // ⚠ この tick に積んでよい量は、tick の頭の送りの列の空き(`bytesBeforeUnwritable`。Netty の
        // 既定の高水位は 64 KiB)だけ。主スレッドから渡した送りは、Netty の側で形になるまで水位に数えられない
        // ので、ループの中で `isWritable()` を見ても止まらない(レビューの指摘)。空きが切れ端 1 個に
        // 足りない tick は送らない —— 遅い回線では keepalive が並ぶのは多くて約 64 KiB の後ろ
        long budget = ((ConnectionAccessor) connection.getConnection()).gomacraftUtility$channel()
                .bytesBeforeUnwritable();
        for (int sent = 0; sent < MAX_CHUNKS_PER_TICK && next < count
                && budget >= ScreenshotRules.chunkLength(total, next); sent++, next++) {
            budget -= ScreenshotRules.chunkLength(total, next);
            int from = next * ScreenshotRules.CHUNK_BYTES;
            byte[] piece = java.util.Arrays.copyOfRange(current.data(), from,
                    from + ScreenshotRules.chunkLength(total, next));
            ClientPlayNetworking.send(new ScreenshotPayloads.Chunk(current.upload(), total, next,
                    current.target().ordinal(), piece));
        }
        if (next == current.next()) {
            return;
        }
        if (next < count) {
            sending = new Sending(current.upload(), current.data(), next, current.token(), current.target());
        } else {
            sending = null;
            AWAITING.put(current.upload(), current.token());
        }
    }

    /** サーバの返事。 */
    public static void onOutcome(ScreenshotPayloads.Outcome outcome) {
        Result result = Result.of(outcome.result());
        String token = AWAITING.remove(outcome.upload());
        if (sending != null && sending.upload() == outcome.upload()) {
            token = sending.token();
            if (result != Result.ACCEPTED) {
                sending = null;
            }
        }
        if (result == Result.ACCEPTED && token != null) {
            // 受け付けられた 1 枚はもう送らない(1 枚 1 回)
            synchronized (TOKENS) {
                TOKENS.remove(token);
            }
        }
        say(result.key(), fallback(result));
    }

    private static String fallback(Result result) {
        return switch (result) {
            case ACCEPTED -> "The server received the screenshot. It will appear on Discord shortly.";
            case OFF -> "This server does not accept screenshots.";
            case BUSY -> "The server is receiving other screenshots. Try again shortly.";
            case COOLDOWN -> "Wait a little before posting the next screenshot.";
            case TOO_LARGE -> "The screenshot is too large to post.";
            case BAD_IMAGE -> "The server could not read the screenshot.";
            case SPOOL_FULL -> "Too many screenshots are waiting to be posted. Try again later.";
            case BROKEN -> "The screenshot was not received completely. Wait a moment and try again.";
            case ERROR -> "The server could not save the screenshot.";
        };
    }

    private static void say(String key, String fallback) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.chatListener().handleSystemMessage(
                Component.translatableWithFallback(key, fallback).withStyle(ChatFormatting.GRAY), false);
    }
}
