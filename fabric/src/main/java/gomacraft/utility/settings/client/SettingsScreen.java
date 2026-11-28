package gomacraft.utility.settings.client;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 小さな設定の画面(fabric 版だけ。forge 版の SettingsScreen はタブと木と検索を持つが、fabric 版は移した機能が
 * 少ないので、縦に並べるだけにした)。
 *
 * <ul>
 *   <li>機能の入り切り: 起動のときに有効になった機能は、「完了」でその場で止める・動かし直す
 *       ({@link FeatureRegistry#setActive})。どれも設定のファイル(次の起動)にも書く。起動のときに切ってあった
 *       機能を入にしたら、起動し直すと効くと知らせる。</li>
 *   <li>手元の設定の項目({@link ClientSettings} に機能が宣言したもの): 「完了」で書き戻す。</li>
 * </ul>
 */
public final class SettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(GomacraftUtility.MOD_ID);
    private static final int WIDTH = 240;
    private static final int HEIGHT = 20;
    private static final int GAP = 4;

    private final Screen parent;
    private final Map<String, Boolean> featureChanges = new LinkedHashMap<>();
    private final Map<ClientSettings.Setting<?>, Object> settingChanges = new LinkedHashMap<>();

    public SettingsScreen(Screen parent) {
        super(Component.translatableWithFallback("gomacraft_utility.settings.title", "gomacraft utility settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FeatureRegistry registry = Features.registry();
        int x = (width - WIDTH) / 2;
        int y = 40;
        for (Feature f : Features.catalog(false)) {
            if (!f.shownInClientSettings()) {
                continue;
            }
            String id = f.id();
            Component label = Component.translatableWithFallback("gomacraft_utility.feature." + id, id);
            boolean usable = registry.isLoaded(id) || registry.enableableByRestart(id);
            boolean current = featureChanges.getOrDefault(id, registry.isOn(id));
            CycleButton<Boolean> button = CycleButton.onOffBuilder(current)
                    .create(x, y, WIDTH, HEIGHT, label, (b, value) -> featureChanges.put(id, value));
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
        ClientSettings client = Settings.client();
        for (ClientSettings.Setting<?> setting : client.all()) {
            if (setting instanceof ClientSettings.Choice choice) {
                addRenderableWidget(choiceButton(client, choice, x, y));
                y += HEIGHT + GAP;
            } else if (setting instanceof ClientSettings.Bool bool) {
                boolean current = (Boolean) settingChanges.getOrDefault(bool, client.get(bool));
                addRenderableWidget(CycleButton.onOffBuilder(current)
                        .create(x, y, WIDTH, HEIGHT, label(bool), (b, value) -> settingChanges.put(bool, value)));
                y += HEIGHT + GAP;
            }
            // 整数の項目は fabric 版の機能にまだ無い(足すときにここへ並べる)
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, Math.max(y + GAP, height - 28), WIDTH, HEIGHT)
                .build());
    }

    private CycleButton<String> choiceButton(ClientSettings client, ClientSettings.Choice choice, int x, int y) {
        String current = (String) settingChanges.getOrDefault(choice, client.get(choice));
        CycleButton<String> button = CycleButton.<String>builder(
                        value -> Component.translatableWithFallback(choice.translationKey() + "." + value, value), current)
                .withValues(choice.choices())
                .create(x, y, WIDTH, HEIGHT, label(choice), (b, value) -> settingChanges.put(choice, value));
        button.setTooltip(Tooltip.create(Component.translatableWithFallback(choice.translationKey() + ".tooltip", "")));
        return button;
    }

    private static Component label(ClientSettings.Setting<?> setting) {
        return Component.translatableWithFallback(setting.translationKey(), setting.key());
    }

    /** 閉じるときに書き戻す(「完了」・Esc のどちらでも)。 */
    @Override
    public void onClose() {
        apply();
        minecraft.gui.setScreen(parent);
    }

    private void apply() {
        FeatureRegistry registry = Features.registry();
        boolean restart = false;
        for (Map.Entry<String, Boolean> change : featureChanges.entrySet()) {
            String id = change.getKey();
            if (registry.isLoaded(id)) {
                registry.setActive(id, change.getValue());
            } else if (change.getValue() && registry.enableableByRestart(id)) {
                restart = true;
            }
        }
        try {
            if (!featureChanges.isEmpty()) {
                FeatureConfig.write(FabricLoader.getInstance().getConfigDir(), Features.catalog(false), featureChanges);
            }
            Settings.client().updateAndSave(settingChanges);
        } catch (Exception e) {
            LOGGER.warn("gomacraft_utility: 設定を書けない: {}", FeatureRegistry.describe(e));
            say(Component.literal("gomacraft_utility: " + FeatureRegistry.describe(e)).withStyle(ChatFormatting.RED));
        }
        featureChanges.clear();
        settingChanges.clear();
        if (restart) {
            say(Component.translatableWithFallback("gomacraft_utility.settings.restart",
                    "These take effect after restarting the game").withStyle(ChatFormatting.YELLOW));
        }
    }

    private void say(Component message) {
        if (minecraft.player != null) {
            minecraft.gui.chatListener().handleSystemMessage(message, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
    }
}
