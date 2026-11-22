package gomacraft.utility.screenshot.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import gomacraft.utility.Features;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import gomacraft.utility.screenshot.ScreenshotRules;
import gomacraft.utility.screenshot.ScreenshotRules.Result;
import gomacraft.utility.screenshot.net.ScreenshotPayloads;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenshotEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * クライアントの側: 撮ったときのチャット欄の行に「投稿」の文字を足し、押された 1 枚だけをサーバへ送る。
 *
 * <p>⚠ 同意は 1 枚ごとのクリック。押さなければ何も送らない。送れるのはこの起動の中で撮ったものだけ
 * (押した文字は乱数の札を持ち、札 → ファイルの表は手元のメモリだけ。1 枚 1 回)。
 * 文字を出すのは、つないだサーバが受ける口を開いているときだけ(シングルや Mod の無いサーバでは出ない)。
 */
public final class ShareClient {
    /** 押した文字が打つ、手元だけのコマンド(NeoForge がチャットのクリックからも手元のコマンドへ回す)。 */
    static final String COMMAND = "gomacraft_utility_screenshot";
    /** 札の数の上限(古いものから忘れる)。 */
    private static final int MAX_TOKENS = 32;
    /** 1 tick に送る切れ端の数(接続を詰まらせない)。 */
    private static final int CHUNKS_PER_TICK = 4;

    private static final SecureRandom RANDOM = new SecureRandom();
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

    private record Sending(int upload, byte[] data, int next) {
    }

    private ShareClient() {
    }

    public static void init() {
        // ほかの Mod がファイルを差し替えた後の名前を使うため、いちばん後に聞く
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ScreenshotEvent.class,
                event -> Features.guard(ScreenshotFeatures.SHARE, () -> onScreenshot(event)));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
                event -> Features.guard(ScreenshotFeatures.SHARE, () -> registerCommand(event)));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> Features.guard(ScreenshotFeatures.SHARE, ShareClient::tick));
    }

    /** つないだサーバが受けるか(専用サーバで、口を開いているときだけ)。 */
    static boolean serverAccepts() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        return connection != null && !mc.hasSingleplayerServer()
                && connection.hasChannel(ScreenshotPayloads.Chunk.TYPE);
    }

    private static void onScreenshot(ScreenshotEvent event) {
        if (event.isCanceled() || !serverAccepts()) {
            return;
        }
        File file = event.getScreenshotFile();
        String token = HexFormat.of().toHexDigits(RANDOM.nextInt());
        TOKENS.put(token, file.toPath());
        Component current = event.getResultMessage();
        MutableComponent message = current != null ? current.copy() : vanillaMessage(file);
        event.setResultMessage(message.append(" ").append(button(token)));
    }

    /** バニラの「スクリーンショットを … として保存しました」(名前を押すとファイルが開く)。 */
    private static MutableComponent vanillaMessage(File file) {
        Component name = Component.literal(file.getName())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
        return Component.translatable("screenshot.success", name);
    }

    private static Component button(String token) {
        Component hover = Component.translatableWithFallback("gomacraft_utility.screenshot.button.hover",
                "Post this screenshot to the server's Discord channel. Chat and player names in it are posted as"
                        + " they are (F1 hides the HUD).");
        return Component.translatableWithFallback("gomacraft_utility.screenshot.button", "[Post to Discord]")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " " + token))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static void registerCommand(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .then(Commands.argument("token", StringArgumentType.word())
                        .executes(context -> {
                            String token = StringArgumentType.getString(context, "token");
                            Features.guard(ScreenshotFeatures.SHARE, () -> post(token));
                            return 1;
                        })));
    }

    private static void post(String token) {
        Path file = TOKENS.remove(token);
        if (file == null) {
            say("gomacraft_utility.screenshot.expired", "This screenshot can no longer be posted.");
            return;
        }
        if (sending != null || loading) {
            TOKENS.put(token, file);
            say("gomacraft_utility.screenshot.busy", "Another screenshot is still being sent. Try again shortly.");
            return;
        }
        if (!serverAccepts()) {
            say("gomacraft_utility.screenshot.unavailable", "This server does not accept screenshots.");
            return;
        }
        loading = true;
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> load(file), Util.backgroundExecutor())
                .whenComplete((data, error) -> mc.execute(() -> Features.guard(ScreenshotFeatures.SHARE, () -> {
                    loading = false;
                    if (error != null || data == null || data.isEmpty()) {
                        say("gomacraft_utility.screenshot.unreadable",
                                "Could not read the screenshot, or it is too large to send.");
                        return;
                    }
                    if (!serverAccepts()) {
                        say("gomacraft_utility.screenshot.unavailable", "This server does not accept screenshots.");
                        return;
                    }
                    sending = new Sending(RANDOM.nextInt(Integer.MAX_VALUE), data.get(), 0);
                    say("gomacraft_utility.screenshot.sending", "Sending the screenshot...");
                })));
    }

    /** 送る中身。PNG のまま上限を超えるときだけ JPEG に作り直す。それでも超える・読めないなら空。 */
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
        Sending current = sending;
        if (current == null) {
            return;
        }
        if (!serverAccepts()) {
            sending = null;
            return;
        }
        int total = current.data().length;
        int count = ScreenshotRules.chunkCount(total);
        int next = current.next();
        for (int sent = 0; sent < CHUNKS_PER_TICK && next < count; sent++, next++) {
            int from = next * ScreenshotRules.CHUNK_BYTES;
            byte[] piece = java.util.Arrays.copyOfRange(current.data(), from,
                    from + ScreenshotRules.chunkLength(total, next));
            PacketDistributor.sendToServer(new ScreenshotPayloads.Chunk(current.upload(), total, next, piece));
        }
        sending = next < count ? new Sending(current.upload(), current.data(), next) : null;
    }

    /** サーバの返事。 */
    public static void onOutcome(ScreenshotPayloads.Outcome outcome) {
        Result result = Result.of(outcome.result());
        if (result != Result.ACCEPTED && sending != null && sending.upload() == outcome.upload()) {
            sending = null;
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
            case BROKEN -> "The screenshot was not received completely. Try again.";
            case ERROR -> "The server could not save the screenshot.";
        };
    }

    private static void say(String key, String fallback) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().addMessage(Component.translatableWithFallback(key, fallback).withStyle(ChatFormatting.GRAY));
    }
}
