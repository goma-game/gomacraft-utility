package gomacraft.utility.settings.client;

import gomacraft.utility.Features;
import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.feature.FeatureState;
import gomacraft.utility.feature.FeatureStatus;
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
import gomacraft.utility.settings.SettingsLayout;
import gomacraft.utility.settings.TextWrap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 設定の画面(Mod の一覧の「設定」から開く —— goma-org/gomabase#2295 の 14、形は 4 回目の確かめの 4)。
 *
 * <ul>
 *   <li>機能と設定の項目を、タブと木で並べる(並びの正は {@link SettingsLayout})。子は親の下に字下げして出し、親が切
 *       なら薄くして触れなくする。上の検索欄は、全部のタブの名前とツールチップから絞る。</li>
 *   <li>変えた値は、この画面の中だけに置き、「完了」(か Esc)で書く。「元に戻す」は開いたときの値へ、
 *       「既定に戻す」は見えている項目を既定の値へ戻す(どちらも「完了」まで書かない)。</li>
 *   <li>書くとき: 手元の設定は {@code gomacraft_utility-client.toml}、機能の入り切りは
 *       {@code gomacraft_utility-features.toml}(次の起動の状態)へ書き、起動のときに有効だった機能はその場で止める・
 *       動かす({@link FeatureRegistry#setActive})。切っていた機能を入にしたときだけ起動し直しが要るので、そのときだけ
 *       知らせの画面に並べる。</li>
 *   <li>ツールチップは自分で折り返す({@link TextWrap}。Minecraft の折り返しは日本語の文を空白で折る)。</li>
 * </ul>
 */
public final class SettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("gomacraft_utility");
    private static final int ROW_HEIGHT = 22;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFF808080;
    private static final int COLOR_FAILED = 0xFFFF5555;
    private static final int COLOR_HEADER = 0xFFFFE080;
    private static final int COLOR_GUIDE = 0xFF606060;

    private final Screen parent;
    private final ClientSettings settings = Settings.client();
    private final FeatureRegistry registry = Features.registry();
    private final Map<String, Feature> catalog = new LinkedHashMap<>();
    private final List<SettingsLayout.Tab> tabs;
    /** 機能 → 木の中の名前の道筋(知らせの画面に出す —— 「英語名で検索 / AE2 ターミナル」)。 */
    private final Map<String, String> paths = new HashMap<>();

    private final Map<String, Boolean> openedFeatures = new LinkedHashMap<>();
    private final Map<String, Boolean> features = new LinkedHashMap<>();
    private final Map<ClientSettings.Setting<?>, Object> openedSettings = new LinkedHashMap<>();
    private final Map<ClientSettings.Setting<?>, Object> values = new LinkedHashMap<>();

    private String currentTab;
    private String query = "";
    private boolean committed;

    private RowList list;
    private EditBox search;
    private Row hovered;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gomacraft_utility.settings.title"));
        this.parent = parent;
        FeatureConfig config = readFeatureConfig();
        List<String> shown = new ArrayList<>();
        for (Feature f : Features.catalog(false)) {
            catalog.put(f.id(), f);
            if (!f.shownInClientSettings()) {
                continue;
            }
            shown.add(f.id());
            boolean on = config.value(f.id()).map(v -> v == FeatureConfig.Value.TRUE).orElse(f.enabledByDefault());
            openedFeatures.put(f.id(), on);
        }
        features.putAll(openedFeatures);
        for (ClientSettings.Setting<?> s : settings.all()) {
            openedSettings.put(s, settings.get(s));
        }
        values.putAll(openedSettings);
        tabs = SettingsLayout.build(shown, settings.all());
        currentTab = tabs.isEmpty() ? SettingsLayout.DEV : tabs.get(0).id();
        for (SettingsLayout.Tab tab : tabs) {
            collectPaths(tab.nodes(), "");
        }
    }

    private void collectPaths(List<SettingsLayout.Node> nodes, String prefix) {
        for (SettingsLayout.Node node : nodes) {
            String label = I18n.get(node.labelKey());
            String path = prefix.isEmpty() ? label : prefix + " / " + label;
            if (node instanceof SettingsLayout.FeatureNode f) {
                paths.put(f.featureId(), path);
            }
            collectPaths(node.children(), path);
        }
    }

    @Override
    protected void init() {
        TabManager manager = new TabManager(this::addRenderableWidget, this::removeWidget) {
            @Override
            public void setCurrentTab(Tab tab, boolean playSound) {
                super.setCurrentTab(tab, playSound);
                if (tab instanceof NamedTab named && !named.id.equals(currentTab)) {
                    currentTab = named.id;
                    rebuild(true);
                }
            }
        };
        List<NamedTab> named = new ArrayList<>();
        for (SettingsLayout.Tab tab : tabs) {
            named.add(new NamedTab(tab.id(), Component.translatable(tab.labelKey())));
        }
        TabNavigationBar bar = TabNavigationBar.builder(manager, this.width)
                .addTabs(named.toArray(Tab[]::new)).build();
        addRenderableWidget(bar);
        bar.arrangeElements();
        int selected = 0;
        for (int i = 0; i < named.size(); i++) {
            if (named.get(i).id.equals(currentTab)) {
                selected = i;
            }
        }
        if (!named.isEmpty()) {
            bar.selectTab(selected, false);
        }
        int top = bar.getRectangle().bottom() + 4;

        search = new EditBox(this.font, this.width / 2 - 100, top, 200, 18,
                Component.translatable("gomacraft_utility.settings.search"));
        search.setHint(Component.translatable("gomacraft_utility.settings.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            if (!text.equals(query)) {
                query = text;
                rebuild(true);
            }
        });
        addRenderableWidget(search);

        int listTop = top + 22;
        int bottom = this.height - 28;
        list = new RowList(this.minecraft, this.width, bottom - listTop, listTop);
        addRenderableWidget(list);

        int buttonWidth = 100;
        int x = this.width / 2 - (buttonWidth * 3 + 8) / 2;
        int y = this.height - 24;
        addRenderableWidget(Button.builder(Component.translatable("gomacraft_utility.settings.undo"), b -> {
            features.putAll(openedFeatures);
            values.putAll(openedSettings);
            rebuild(false);
        }).bounds(x, y, buttonWidth, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gomacraft_utility.settings.undo.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gomacraft_utility.settings.reset"), b -> {
            resetVisible();
            rebuild(false);
        }).bounds(x + buttonWidth + 4, y, buttonWidth, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gomacraft_utility.settings.reset.tooltip"))).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> commit())
                .bounds(x + (buttonWidth + 4) * 2, y, buttonWidth, 20).build());
        rebuild(false);
    }

    /** 見えている項目の木(タブか、検索の結果)。 */
    private List<SettingsLayout.Node> visibleNodes() {
        List<SettingsLayout.Node> out = new ArrayList<>();
        for (SettingsLayout.Tab tab : tabs) {
            if (searching() || tab.id().equals(currentTab)) {
                out.addAll(tab.nodes());
            }
        }
        return out;
    }

    private boolean searching() {
        return !query.isBlank();
    }

    private void resetVisible() {
        SettingsLayout.walk(visibleNodes(), 0, (node, depth) -> {
            if (searching() && !matchesOrHasMatch(node)) {
                return;
            }
            switch (node) {
                case SettingsLayout.FeatureNode f -> features.put(f.featureId(), catalog.get(f.featureId()).enabledByDefault());
                case SettingsLayout.SettingNode s -> values.put(s.setting(), s.setting().defaultValue());
                case SettingsLayout.GroupNode g -> {
                }
            }
        });
    }

    // ---- 並べる ----

    private void rebuild(boolean resetScroll) {
        if (list == null) {
            return;
        }
        double scroll = list.getScrollAmount();
        List<Row> rows = new ArrayList<>();
        for (SettingsLayout.Tab tab : tabs) {
            if (searching()) {
                List<Row> found = new ArrayList<>();
                add(found, tab.nodes(), 0, () -> true, false);
                if (tab.id().equals(SettingsLayout.DEV)) {
                    addStatus(found);
                }
                if (!found.isEmpty()) {
                    rows.add(new HeaderRow(Component.translatable(tab.labelKey()), 0));
                    rows.addAll(found);
                }
            } else if (tab.id().equals(currentTab)) {
                add(rows, tab.nodes(), 0, () -> true, false);
                if (tab.id().equals(SettingsLayout.DEV)) {
                    addStatus(rows);
                }
            }
        }
        list.setRows(rows);
        list.setScrollAmount(resetScroll ? 0 : scroll);
    }

    /**
     * @param parent   親が効いているか(描くたびに今の値で問う)
     * @param inMatch  検索で、上の段が当たっている(中身を全部出す)
     */
    private void add(List<Row> rows, List<SettingsLayout.Node> nodes, int depth, BooleanSupplier parent,
            boolean inMatch) {
        for (SettingsLayout.Node node : nodes) {
            boolean self = inMatch || !searching() || matches(node);
            if (!self && !hasMatch(node.children())) {
                continue;
            }
            BooleanSupplier childEnabled;
            switch (node) {
                case SettingsLayout.FeatureNode f -> {
                    String id = f.featureId();
                    rows.add(new ControlRow(node, depth, parent, featureButton(id), usable(id), failed(id)));
                    childEnabled = () -> parent.getAsBoolean() && usable(id) && Boolean.TRUE.equals(features.get(id));
                }
                case SettingsLayout.SettingNode s -> {
                    rows.add(new ControlRow(node, depth, parent, settingControl(s.setting()), true, false));
                    ClientSettings.Setting<?> setting = s.setting();
                    childEnabled = () -> parent.getAsBoolean()
                            && (!(setting instanceof ClientSettings.Bool) || Boolean.TRUE.equals(values.get(setting)));
                }
                case SettingsLayout.GroupNode g -> {
                    rows.add(new HeaderRow(Component.translatable(g.labelKey()), depth));
                    List<String> anyOf = g.anyOf();
                    childEnabled = () -> parent.getAsBoolean() && (anyOf.isEmpty()
                            || anyOf.stream().anyMatch(id -> usable(id) && Boolean.TRUE.equals(features.get(id))));
                }
            }
            add(rows, node.children(), depth + 1, childEnabled, self && searching());
        }
    }

    /** 開発者のタブの終わり: 起動時の状態(機能ごとの 1 語。起動の後に止めたものはそう添える)。 */
    private void addStatus(List<Row> rows) {
        List<Row> found = new ArrayList<>();
        for (FeatureStatus status : registry.statuses()) {
            String text = status.token();
            if (registry.isSuspended(status.id())) {
                text += " " + I18n.get("gomacraft_utility.settings.suspended");
            }
            if (searching() && !text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).strip())) {
                continue;
            }
            int color = status.state() == FeatureState.FAILED ? COLOR_FAILED
                    : status.state() == FeatureState.ON && !registry.isSuspended(status.id()) ? COLOR_TEXT : COLOR_DIM;
            found.add(new TextRow(Component.literal(text), 1, color));
        }
        if (!found.isEmpty()) {
            rows.add(new HeaderRow(Component.translatable("gomacraft_utility.settings.group.status"), 0));
            rows.addAll(found);
        }
    }

    private boolean matches(SettingsLayout.Node node) {
        String q = query.toLowerCase(Locale.ROOT).strip();
        if (q.isEmpty()) {
            return true;
        }
        String text = I18n.get(node.labelKey());
        if (node.tooltipKey() != null) {
            text += "\n" + I18n.get(node.tooltipKey());
        }
        if (node instanceof SettingsLayout.FeatureNode f) {
            text += "\n" + f.featureId();
        }
        return text.toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean hasMatch(List<SettingsLayout.Node> nodes) {
        for (SettingsLayout.Node n : nodes) {
            if (matchesOrHasMatch(n)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOrHasMatch(SettingsLayout.Node node) {
        return matches(node) || hasMatch(node.children());
    }

    /** 入り切りを変えられるか(起動のときに有効だったか、設定で切ってあっただけ)。要る Mod が無い・壊れたものは触れない。 */
    private boolean usable(String id) {
        return registry.isLoaded(id) || registry.enableableByRestart(id);
    }

    private boolean failed(String id) {
        return registry.status(id).map(s -> s.state() == FeatureState.FAILED).orElse(false);
    }

    // ---- 操作 ----

    private AbstractWidget featureButton(String id) {
        return Button.builder(CommonComponents.optionStatus(Boolean.TRUE.equals(features.get(id))), b -> {
            boolean next = !Boolean.TRUE.equals(features.get(id));
            features.put(id, next);
            b.setMessage(CommonComponents.optionStatus(next));
        }).size(SettingsLayout.CONTROL_WIDTH, 20).build();
    }

    @SuppressWarnings("unchecked")
    private AbstractWidget settingControl(ClientSettings.Setting<?> setting) {
        if (setting instanceof ClientSettings.Bool b) {
            return Button.builder(CommonComponents.optionStatus(Boolean.TRUE.equals(values.get(b))), button -> {
                boolean next = !Boolean.TRUE.equals(values.get(b));
                values.put(b, next);
                button.setMessage(CommonComponents.optionStatus(next));
            }).size(SettingsLayout.CONTROL_WIDTH, 20).build();
        }
        if (setting instanceof ClientSettings.Int i) {
            return new IntSlider(i);
        }
        ClientSettings.Choice c = (ClientSettings.Choice) setting;
        return Button.builder(choiceLabel(c, (String) values.get(c)), button -> {
            List<String> choices = c.choices();
            int at = choices.indexOf((String) values.get(c));
            String next = choices.get((at + 1) % choices.size());
            values.put(c, next);
            button.setMessage(choiceLabel(c, next));
        }).size(SettingsLayout.CONTROL_WIDTH, 20).build();
    }

    private static Component choiceLabel(ClientSettings.Choice c, String value) {
        return Component.translatable(c.translationKey() + "." + value);
    }

    private final class IntSlider extends AbstractSliderButton {
        private final ClientSettings.Int setting;

        IntSlider(ClientSettings.Int setting) {
            super(0, 0, SettingsLayout.CONTROL_WIDTH, 20, Component.empty(), 0);
            this.setting = setting;
            this.value = toSlider((Integer) values.get(setting));
            updateMessage();
        }

        private double toSlider(int v) {
            int span = setting.max() - setting.min();
            return span <= 0 ? 0 : (double) (v - setting.min()) / span;
        }

        private int current() {
            return setting.min() + (int) Math.round(value * (setting.max() - setting.min()));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Integer.toString(current())));
        }

        @Override
        protected void applyValue() {
            values.put(setting, current());
        }
    }

    // ---- 書く ----

    @Override
    public void onClose() {
        commit();
    }

    private void commit() {
        if (committed) {
            return;
        }
        committed = true;
        Map<String, Boolean> featureChanges = new LinkedHashMap<>();
        features.forEach((id, on) -> {
            if (!Objects.equals(openedFeatures.get(id), on)) {
                featureChanges.put(id, on);
            }
        });
        if (!featureChanges.isEmpty()) {
            try {
                FeatureConfig.write(FMLPaths.CONFIGDIR.get(), List.copyOf(catalog.values()), featureChanges);
            } catch (IOException e) {
                LOGGER.warn("gomacraft_utility: 機能の入り切りを書けない: {}", e.toString());
            }
            // 起動のときに有効だった機能は、その場で止める・動かす(起動の状態の 1 行は変えない)
            featureChanges.forEach(registry::setActive);
        }
        boolean settingsChanged = false;
        for (Map.Entry<ClientSettings.Setting<?>, Object> e : values.entrySet()) {
            if (!Objects.equals(openedSettings.get(e.getKey()), e.getValue())) {
                set(e.getKey(), e.getValue());
                settingsChanged = true;
            }
        }
        if (settingsChanged) {
            // 書くと各機能が値を写す(止めていた機能を動かし直した後に書く —— 止めている間の機能は写さない)
            try {
                settings.save();
            } catch (IOException e) {
                LOGGER.warn("gomacraft_utility: 手元の設定を書けない: {}", e.toString());
            }
        }
        List<String> restart = new ArrayList<>();
        if (!featureChanges.isEmpty()) {
            features.forEach((id, on) -> {
                if (on && !registry.isLoaded(id) && registry.enableableByRestart(id)) {
                    restart.add("・" + paths.getOrDefault(id, I18n.get("gomacraft_utility.feature." + id)));
                }
            });
        }
        if (restart.isEmpty()) {
            this.minecraft.setScreen(parent);
            return;
        }
        this.minecraft.setScreen(new AlertScreen(() -> this.minecraft.setScreen(parent),
                Component.translatable("gomacraft_utility.settings.restart"),
                // Esc で閉じない(閉じると親の画面へ戻らずゲームへ戻る)。OK で親の画面へ
                Component.literal(String.join("\n", restart)), CommonComponents.GUI_OK, false));
    }

    @SuppressWarnings("unchecked")
    private <T> void set(ClientSettings.Setting<T> setting, Object value) {
        settings.set(setting, (T) value);
    }

    private static FeatureConfig readFeatureConfig() {
        try {
            return FeatureConfig.load(FMLPaths.CONFIGDIR.get(), Features.catalog(false));
        } catch (IOException e) {
            LOGGER.warn("gomacraft_utility: 機能の入り切りの設定を読めない(既定の値で見せる): {}", e.toString());
            return FeatureConfig.empty();
        }
    }

    // ---- 描く ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovered = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            List<FormattedCharSequence> lines = hovered.tooltip();
            if (!lines.isEmpty()) {
                setTooltipForNextRenderPass(lines);
            }
        }
    }

    private List<FormattedCharSequence> wrap(String text) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String line : TextWrap.wrap(text, c -> this.font.width(Character.toString(c)), TOOLTIP_WIDTH)) {
            out.add(Component.literal(line).getVisualOrderText());
        }
        return out;
    }

    /** タブの見出しだけを持つ(中身は画面の 1 つの一覧が、選んだタブで並べ替える)。 */
    private static final class NamedTab implements Tab {
        private final String id;
        private final Component title;

        NamedTab(String id, Component title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
        }
    }

    private final class RowList extends ContainerObjectSelectionList<Row> {
        RowList(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, ROW_HEIGHT);
        }

        void setRows(List<Row> rows) {
            replaceEntries(rows);
        }

        @Override
        public int getRowWidth() {
            return SettingsLayout.ROW_WIDTH;
        }
    }

    private abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
        final int depth;

        Row(int depth) {
            this.depth = depth;
        }

        List<FormattedCharSequence> tooltip() {
            return List.of();
        }

        /** 木の線(親から子への ┗ の形)と、字の左の端。 */
        int drawGuide(GuiGraphics graphics, int top, int left, int height) {
            int x = left + SettingsLayout.GAP + depth * SettingsLayout.INDENT;
            if (depth > 0) {
                int gx = x - SettingsLayout.INDENT + 3;
                int mid = top + height / 2;
                graphics.fill(gx, top - 2, gx + 1, mid + 1, COLOR_GUIDE);
                graphics.fill(gx, mid, x - 2, mid + 1, COLOR_GUIDE);
            }
            return x;
        }
    }

    /** 見出し(まとまり・検索の結果のタブの名前)。 */
    private final class HeaderRow extends Row {
        private final Component text;

        HeaderRow(Component text, int depth) {
            super(depth);
            this.text = text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                int mouseY, boolean hovering, float partialTick) {
            int x = drawGuide(graphics, top, left, height);
            graphics.drawString(font, text, x, top + (height - 8) / 2, COLOR_HEADER);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** 字だけの行(開発者のタブの起動時の状態)。 */
    private final class TextRow extends Row {
        private final Component text;
        private final int color;

        TextRow(Component text, int depth, int color) {
            super(depth);
            this.text = text;
            this.color = color;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                int mouseY, boolean hovering, float partialTick) {
            int x = drawGuide(graphics, top, left, height);
            String shown = font.plainSubstrByWidth(text.getString(), left + width - x - 2);
            graphics.drawString(font, shown, x, top + (height - 8) / 2, color);
            if (hovering && shown.length() < text.getString().length()) {
                hovered = this;
            }
        }

        @Override
        List<FormattedCharSequence> tooltip() {
            return wrap(text.getString());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** 名前と、右の操作(入り切り・数・選ぶ語)。 */
    private final class ControlRow extends Row {
        private final SettingsLayout.Node node;
        private final BooleanSupplier parent;
        private final AbstractWidget control;
        private final boolean usable;
        private final boolean failed;
        private final Component label;

        ControlRow(SettingsLayout.Node node, int depth, BooleanSupplier parent, AbstractWidget control,
                boolean usable, boolean failed) {
            super(depth);
            this.node = node;
            this.parent = parent;
            this.control = control;
            this.usable = usable;
            this.failed = failed;
            this.label = Component.translatable(node.labelKey());
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                int mouseY, boolean hovering, float partialTick) {
            boolean enabled = usable && parent.getAsBoolean();
            int x = drawGuide(graphics, top, left, height);
            int color = failed ? COLOR_FAILED : enabled ? COLOR_TEXT : COLOR_DIM;
            graphics.drawString(font, label, x, top + (height - 8) / 2, color);
            control.active = enabled;
            control.setX(left + width - SettingsLayout.CONTROL_WIDTH - SettingsLayout.GAP);
            control.setY(top);
            control.render(graphics, mouseX, mouseY, partialTick);
            if (hovering) {
                hovered = this;
            }
        }

        @Override
        List<FormattedCharSequence> tooltip() {
            StringBuilder text = new StringBuilder();
            if (node.tooltipKey() != null && I18n.exists(node.tooltipKey())) {
                text.append(I18n.get(node.tooltipKey()));
            }
            if (node instanceof SettingsLayout.FeatureNode && failed) {
                append(text, I18n.get("gomacraft_utility.settings.failed"));
            } else if (node instanceof SettingsLayout.FeatureNode && !usable) {
                append(text, I18n.get("gomacraft_utility.settings.unavailable"));
            }
            return text.isEmpty() ? List.of() : wrap(text.toString());
        }

        private void append(StringBuilder text, String line) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(line);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(control);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(control);
        }
    }
}
