package gomacraft.utility.settings.client;

import com.mojang.serialization.Codec;
import gomacraft.utility.Features;
import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureState;
import gomacraft.utility.feature.FeatureStatus;
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 設定の画面(Mod の一覧の「設定」から開く —— goma-org/gomabase#2295 の 14)。
 *
 * <ul>
 *   <li><b>機能の入り切り</b>: 手元の設定の画面に出す機能({@link Feature#shownInClientSettings})を全部並べる。
 *       値は {@code gomacraft_utility-features.toml} に書き、<b>次の起動から効く</b>(mixin の当否は起動で決まる)。
 *       サーバの宣言(gomabase が配る設定)で決める機能は出さない。</li>
 *   <li>ボタンの名前は短い語だけにし、説明は {@code <訳の鍵>.tooltip} のツールチップに置く(長い名前はボタンからはみ出して
 *       読めない —— goma-org/gomabase#2295 の 2 回目の確かめの 5)。起動し直すと効くことも、機能のツールチップが言う。</li>
 *   <li><b>各機能の設定</b>: 機能が {@link ClientSettings#register} で宣言した項目を全部並べる(機能を足せば並ぶ)。
 *       値は {@code gomacraft_utility-client.toml} に書き、すぐ効く。</li>
 * </ul>
 */
public final class SettingsScreen extends OptionsSubScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger("gomacraft_utility");

    /** 入り切りを変えた機能(閉じるときに書く)。 */
    private final Map<String, Boolean> featureChanges = new LinkedHashMap<>();
    private boolean settingsChanged;

    public SettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("gomacraft_utility.settings.title"));
    }

    @Override
    protected void addOptions() {
        ClientSettings settings = Settings.client();
        // 各機能の設定(すぐ効く。効き方はツールチップが言う)
        List<ClientSettings.Setting<?>> all = settings.all();
        if (!all.isEmpty()) {
            header("gomacraft_utility.settings.section.settings");
            List<OptionInstance<?>> options = new ArrayList<>();
            for (ClientSettings.Setting<?> s : all) {
                options.add(option(settings, s));
            }
            this.list.addSmall(options.toArray(OptionInstance<?>[]::new));
        }
        // 機能の入り切り(次の起動から。ツールチップが言う)
        header("gomacraft_utility.settings.section.features");
        FeatureConfig config = readFeatureConfig();
        List<OptionInstance<?>> toggles = new ArrayList<>();
        for (Feature f : Features.catalog(false)) {
            if (!f.shownInClientSettings()) {
                continue;
            }
            boolean on = config.value(f.id()).map(v -> v == FeatureConfig.Value.TRUE).orElse(f.enabledByDefault());
            String id = f.id();
            toggles.add(OptionInstance.createBoolean(
                    "gomacraft_utility.feature." + id,
                    value -> net.minecraft.client.gui.components.Tooltip.create(featureTooltip(id)),
                    on,
                    value -> featureChanges.put(id, value)));
        }
        this.list.addSmall(toggles.toArray(OptionInstance<?>[]::new));
    }

    private void header(String key) {
        this.list.addSmall(new StringWidget(Component.translatable(key), this.font), null);
    }

    /** 機能の説明: 何をするか、今の状態(起動のときに決まったもの)、変えたら起動し直すこと。 */
    private static Component featureTooltip(String id) {
        Optional<FeatureStatus> status = Features.registry().status(id);
        String state = status.map(FeatureStatus::token).orElse(id);
        Component now = status.isPresent() && status.get().state() == FeatureState.FAILED
                ? Component.translatable("gomacraft_utility.settings.feature.failed", state)
                : Component.translatable("gomacraft_utility.settings.feature.state", state);
        return Component.translatable("gomacraft_utility.feature." + id + ".tooltip").copy()
                .append("\n").append(now)
                .append("\n").append(Component.translatable("gomacraft_utility.settings.feature.restart"));
    }

    @SuppressWarnings("unchecked")
    private <T> OptionInstance<T> option(ClientSettings settings, ClientSettings.Setting<T> setting) {
        String caption = setting.translationKey();
        Component help = Component.translatable(caption + ".tooltip");
        if (setting instanceof ClientSettings.Bool b) {
            return (OptionInstance<T>) OptionInstance.createBoolean(caption, OptionInstance.cachedConstantTooltip(help),
                    settings.get(b), value -> {
                settings.set(b, value);
                settingsChanged = true;
            });
        }
        if (setting instanceof ClientSettings.Int i) {
            return (OptionInstance<T>) new OptionInstance<>(caption, OptionInstance.cachedConstantTooltip(help),
                    (label, value) -> Options.genericValueLabel(label, Component.literal(Integer.toString(value))),
                    new OptionInstance.IntRange(i.min(), i.max()), settings.get(i), value -> {
                        settings.set(i, value);
                        settingsChanged = true;
                    });
        }
        ClientSettings.Choice c = (ClientSettings.Choice) setting;
        return (OptionInstance<T>) new OptionInstance<>(caption, OptionInstance.cachedConstantTooltip(help),
                (label, value) -> Options.genericValueLabel(label, Component.translatable(caption + "." + value)),
                new OptionInstance.Enum<>(c.choices(), Codec.STRING), settings.get(c), value -> {
                    settings.set(c, value);
                    settingsChanged = true;
                });
    }

    private static FeatureConfig readFeatureConfig() {
        try {
            return FeatureConfig.load(FMLPaths.CONFIGDIR.get(), Features.catalog(false));
        } catch (IOException e) {
            LOGGER.warn("gomacraft_utility: 機能の入り切りの設定を読めない(既定の値で見せる): {}", e.toString());
            return FeatureConfig.empty();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (settingsChanged) {
            try {
                Settings.client().save();
            } catch (IOException e) {
                LOGGER.warn("gomacraft_utility: 手元の設定を書けない: {}", e.toString());
            }
        }
        if (!featureChanges.isEmpty()) {
            try {
                FeatureConfig.write(FMLPaths.CONFIGDIR.get(), Features.catalog(false), featureChanges);
            } catch (IOException e) {
                LOGGER.warn("gomacraft_utility: 機能の入り切りを書けない: {}", e.toString());
            }
        }
    }
}
