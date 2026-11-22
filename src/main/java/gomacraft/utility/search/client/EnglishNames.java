package gomacraft.utility.search.client;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.search.EnglishFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * 英語名の索引(段 1 の中心)。リソースの読み込みのたびに en_us の訳を 1 回だけ読み、検索の差し込みはここを引くだけにする
 * (端末を重くしない)。表示の言語が en_us なら作らない(英語名で引く意味が無い)。
 *
 * <p>索引を使う機能は {@link #install} で名乗る。索引の読み込みが例外を投げたら、名乗った機能を全部 FAILED にする
 * (リソースの読み込みは止めない)。
 */
public final class EnglishNames {
    private static final String ENGLISH = "en_us";
    private static final int MAX_DEPTH = 16;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Set<String> DEPENDENTS = new CopyOnWriteArraySet<>();

    /** 今の索引(無ければ null)。読み込みのたびに丸ごと差し替える。 */
    private static volatile ClientLanguage english;
    /** 索引を差し替えた回数。名前の作り置き(cache)は、この値が変わったら捨てる。 */
    private static volatile int generation;

    private EnglishNames() {
    }

    /** 索引を使う機能が、Mod の組み立ての中で呼ぶ(何度呼んでもよい)。 */
    public static void install(FeatureContext context, String feature) {
        DEPENDENTS.add(feature);
        if (INSTALLED.compareAndSet(false, true)) {
            context.modBus().addListener(RegisterClientReloadListenersEvent.class, event -> {
                try {
                    event.registerReloadListener(new Listener());
                } catch (Throwable t) {
                    Listener.fail(t);
                }
            });
        }
    }

    public static int generation() {
        return generation;
    }

    /** 訳のキーの英語(無い・索引が無いなら空)。 */
    public static Optional<String> lookup(String key) {
        ClientLanguage en = english;
        if (en == null || key == null || !en.has(key)) {
            return Optional.empty();
        }
        return Optional.of(en.getOrDefault(key));
    }

    /** 表示名の英語を小文字で(英語にできない・索引が無いなら null)。 */
    public static String lowerCase(Component component) {
        String text = render(component);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    /** 表示名を英語で書き直す(訳のキーを en_us で引いて引数を埋める)。頭が英語にできなければ null。 */
    public static String render(Component component) {
        ClientLanguage en = english;
        if (en == null || component == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        return append(component, en, out, 0) ? out.toString() : null;
    }

    private static boolean append(Component component, ClientLanguage en, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (component.getContents() instanceof TranslatableContents translatable) {
            String template = en.has(translatable.getKey())
                    ? en.getOrDefault(translatable.getKey())
                    : translatable.getFallback();
            if (template == null) {
                return false;
            }
            Object[] args = translatable.getArgs();
            out.append(EnglishFormat.format(template, i -> argument(args[i], en, depth), args.length));
        } else {
            component.getContents().visit(text -> {
                out.append(text);
                return Optional.empty();
            });
        }
        for (Component sibling : component.getSiblings()) {
            if (!append(sibling, en, out, depth + 1)) {
                // 付け足しの部分が英語にできなければ、今の言語の文字のまま付ける
                out.append(sibling.getString());
            }
        }
        return true;
    }

    private static String argument(Object arg, ClientLanguage en, int depth) {
        if (arg instanceof Component component) {
            StringBuilder sb = new StringBuilder();
            return append(component, en, sb, depth + 1) ? sb.toString() : component.getString();
        }
        return String.valueOf(arg);
    }

    private static final class Listener extends SimplePreparableReloadListener<ClientLanguage> {
        @Override
        protected ClientLanguage prepare(ResourceManager manager, ProfilerFiller profiler) {
            try {
                return ClientLanguage.loadFrom(manager, List.of(ENGLISH), false);
            } catch (Throwable t) {
                fail(t);
                return null;
            }
        }

        @Override
        protected void apply(ClientLanguage prepared, ResourceManager manager, ProfilerFiller profiler) {
            try {
                String selected = Minecraft.getInstance().getLanguageManager().getSelected();
                english = ENGLISH.equals(selected) ? null : prepared;
                generation++;
            } catch (Throwable t) {
                fail(t);
            }
        }

        private static void fail(Throwable t) {
            english = null;
            generation++;
            for (String feature : DEPENDENTS) {
                Features.registry().markFailed(feature, "english index: " + FeatureRegistry.describe(t));
            }
        }
    }
}
