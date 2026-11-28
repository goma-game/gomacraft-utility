package gomacraft.utility.settings.client;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 小さな設定の画面(fabric 版だけ。forge 版の SettingsScreen はタブと木と検索を持つが、fabric 版は移した機能が
 * 少ないので、縦に並べるだけにした)。書き方と動きの約束は forge 版の SettingsScreen と同じ。
 *
 * <ul>
 *   <li>機能の入り切りは、設定のファイル(次の起動の状態)の値で見せる。閉じるときにファイルへ書き、書けたら、
 *       起動のときに有効になった機能をその場で止める・動かし直す({@link FeatureRegistry#setActive})。書けなかったら
 *       動きも変えない(次に開いた画面がファイルの値を見せるので、画面と動きを食い違わせない)。</li>
 *   <li>起動のときに切ってあった機能を入にしたら、起動し直すと効くと知らせの画面で出す(タイトル画面の Mod Menu から
 *       開いたときも見えるように、チャットでなく画面で出す)。書けなかったときも知らせの画面で出す。</li>
 *   <li>手元の設定の項目({@link ClientSettings} に機能が宣言したもの)は、閉じるときに書き戻す。</li>
 * </ul>
 */
public final class SettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(GomacraftUtility.MOD_ID);
    private static final int WIDTH = 240;
    private static final int HEIGHT = 20;
    private static final int GAP = 4;

    private final Screen parent;
    private final FeatureRegistry registry = Features.registry();
    private final ClientSettings settings = Settings.client();
    /** 開いたときのファイルの値と、今の画面の値(機能の名前 → 入か)。 */
    private final Map<String, Boolean> openedFeatures = new LinkedHashMap<>();
    private final Map<String, Boolean> features = new LinkedHashMap<>();
    private final Map<ClientSettings.Setting<?>, Object> openedSettings = new LinkedHashMap<>();
    private final Map<ClientSettings.Setting<?>, Object> values = new LinkedHashMap<>();
    /** 書いたか(閉じ方が 2 つ重なっても 2 度書かない)。 */
    private boolean committed;

    public SettingsScreen(Screen parent) {
        super(Component.translatableWithFallback("gomacraft_utility.settings.title", "gomacraft utility settings"));
        this.parent = parent;
        FeatureConfig config = readFeatureConfig();
        for (Feature f : Features.catalog(false)) {
            if (f.shownInClientSettings()) {
                boolean on = config.value(f.id()).map(v -> v == FeatureConfig.Value.TRUE).orElse(f.enabledByDefault());
                openedFeatures.put(f.id(), on);
            }
        }
        features.putAll(openedFeatures);
        for (ClientSettings.Setting<?> s : settings.all()) {
            openedSettings.put(s, settings.get(s));
        }
        values.putAll(openedSettings);
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = 40;
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            String id = entry.getKey();
            Component label = Component.translatableWithFallback("gomacraft_utility.feature." + id, id);
            boolean usable = registry.isLoaded(id) || registry.enableableByRestart(id);
            CycleButton<Boolean> button = CycleButton.onOffBuilder(entry.getValue())
                    .create(x, y, WIDTH, HEIGHT, label, (b, value) -> features.put(id, value));
            button.active = usable;
            if (registry.isFailed(id)) {
                button.setTooltip(Tooltip.create(Component.translatableWithFallback(
                        "gomacraft_utility.settings.failed", "Broken and stopped.")));
            } else if (!usable) {
                button.setTooltip(Tooltip.create(Component.translatableWithFallback(
                        "gomacraft_utility.settings.unavailable", "Unavailable.")));
            } else {
                button.setTooltip(Tooltip.create(Component.translatableWithFallback(
                        "gomacraft_utility.feature." + id + ".tooltip", "")));
            }
            addRenderableWidget(button);
            y += HEIGHT + GAP;
        }
        for (ClientSettings.Setting<?> setting : settings.all()) {
            if (setting instanceof ClientSettings.Choice choice) {
                addRenderableWidget(choiceButton(choice, x, y));
                y += HEIGHT + GAP;
            } else if (setting instanceof ClientSettings.Bool bool) {
                addRenderableWidget(CycleButton.onOffBuilder((Boolean) values.get(bool))
                        .create(x, y, WIDTH, HEIGHT, label(bool), (b, value) -> values.put(bool, value)));
                y += HEIGHT + GAP;
            }
            // 整数の項目は fabric 版の機能にまだ無い(足すときにここへ並べる)
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, Math.max(y + GAP, height - 28), WIDTH, HEIGHT)
                .build());
    }

    private CycleButton<String> choiceButton(ClientSettings.Choice choice, int x, int y) {
        CycleButton<String> button = CycleButton.<String>builder(
                        value -> Component.translatableWithFallback(choice.translationKey() + "." + value, value),
                        (String) values.get(choice))
                .withValues(choice.choices())
                .create(x, y, WIDTH, HEIGHT, label(choice), (b, value) -> values.put(choice, value));
        button.setTooltip(Tooltip.create(Component.translatableWithFallback(choice.translationKey() + ".tooltip", "")));
        return button;
    }

    private static Component label(ClientSettings.Setting<?> setting) {
        return Component.translatableWithFallback(setting.translationKey(), setting.key());
    }

    // ---- 書く(forge 版の SettingsScreen の commit と同じ形)----

    @Override
    public void onClose() {
        commit(true);
    }

    /**
     * 「完了」・Esc 以外で閉じられた(切断された・ほかの画面に替えられた): 変更は捨てずに書く。知らせの画面は出さない
     * (ほかの画面に替わったところへ割り込まない)。自分で閉じたときは書き終えている。
     */
    @Override
    public void removed() {
        super.removed();
        commit(false);
    }

    /** @param navigate 書いた後に、親の画面(か知らせの画面)へ移るか */
    private void commit(boolean navigate) {
        if (committed) {
            return;
        }
        committed = true;
        List<String> problems = new ArrayList<>();
        Map<String, Boolean> featureChanges = new LinkedHashMap<>();
        features.forEach((id, on) -> {
            if (!Objects.equals(openedFeatures.get(id), on)) {
                featureChanges.put(id, on);
            }
        });
        if (!featureChanges.isEmpty()) {
            try {
                FeatureConfig.write(FabricLoader.getInstance().getConfigDir(), Features.catalog(false), featureChanges);
                // 書けたときだけ、起動のときに有効だった機能をその場で止める・動かす(起動の状態の 1 行は変えない)
                featureChanges.forEach(registry::setActive);
            } catch (IOException e) {
                LOGGER.warn("gomacraft_utility: 機能の入り切りを書けない(その場の入り切りもしない): {}", e.toString());
                problems.add(e.toString());
                featureChanges.clear();
            }
        }
        Map<ClientSettings.Setting<?>, Object> settingChanges = new LinkedHashMap<>();
        for (Map.Entry<ClientSettings.Setting<?>, Object> e : values.entrySet()) {
            if (!Objects.equals(openedSettings.get(e.getKey()), e.getValue())) {
                settingChanges.put(e.getKey(), e.getValue());
            }
        }
        if (!settingChanges.isEmpty()) {
            // 書くと各機能が値を写す(止めていた機能を動かし直した後に書く —— 止めている間の機能は写さない)
            try {
                settings.updateAndSave(settingChanges);
            } catch (IOException e) {
                LOGGER.warn("gomacraft_utility: 手元の設定を書けない: {}", e.toString());
                problems.add(e.toString());
            }
        }
        if (!navigate) {
            return;
        }
        // 起動し直しが要るもの: 今回入にした機能のうち、起動のときに切ってあったもの
        List<String> restart = new ArrayList<>();
        featureChanges.forEach((id, on) -> {
            if (on && !registry.isLoaded(id) && registry.enableableByRestart(id)) {
                restart.add("- " + Component.translatableWithFallback("gomacraft_utility.feature." + id, id).getString());
            }
        });
        if (!problems.isEmpty()) {
            alert(Component.translatableWithFallback("gomacraft_utility.settings.save_failed",
                    "Could not save the settings"), String.join("\n", problems));
        } else if (!restart.isEmpty()) {
            alert(Component.translatableWithFallback("gomacraft_utility.settings.restart",
                    "These take effect after restarting the game"), String.join("\n", restart));
        } else {
            minecraft.gui.setScreen(parent);
        }
    }

    /** 知らせの画面(OK で親の画面へ。Esc で閉じない —— 閉じると親の画面へ戻らずゲームへ戻る)。 */
    private void alert(Component title, String body) {
        minecraft.gui.setScreen(new AlertScreen(() -> minecraft.gui.setScreen(parent), title,
                Component.literal(body), CommonComponents.GUI_OK, false));
    }

    private static FeatureConfig readFeatureConfig() {
        try {
            return FeatureConfig.load(FabricLoader.getInstance().getConfigDir(), Features.catalog(false));
        } catch (IOException e) {
            LOGGER.warn("gomacraft_utility: 機能の入り切りの設定を読めない(既定の値で見せる): {}", e.toString());
            return FeatureConfig.empty();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
    }
}
