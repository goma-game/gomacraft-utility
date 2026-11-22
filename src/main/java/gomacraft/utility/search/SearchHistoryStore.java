package gomacraft.utility.search;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 検索語の履歴(プレイヤーごと)。手元の設定のフォルダの 1 つのファイルに置き、サーバには送らない。
 *
 * <ul>
 *   <li>同じ語をもう一度使うと先頭へ上がる(お気に入りは位置を変えない)。</li>
 *   <li>お気に入りは先頭にまとめて出し、数の上限で消さない。お気に入りでない語は {@link #MAX_RECENT} 件まで持つ
 *       (古いものから消す)。</li>
 * </ul>
 *
 * <p>Minecraft に依らない(試験はここを見る)。thread の守りは呼ぶ側が持つ。
 */
public final class SearchHistoryStore {
    public static final int MAX_RECENT = 50;
    public static final int MAX_FAVORITES = 50;
    public static final int MAX_LENGTH = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 履歴の 1 件。 */
    public record Entry(String text, boolean favorite) {
    }

    private final Map<String, List<Entry>> players = new LinkedHashMap<>();

    /** 語を使ったことを記す。空・長すぎる語は記さない。変わったら true。 */
    public boolean record(String player, String text) {
        String value = normalize(text);
        if (value == null) {
            return false;
        }
        List<Entry> entries = entries(player);
        int index = indexOf(entries, value);
        if (index >= 0) {
            Entry existing = entries.get(index);
            if (existing.favorite()) {
                return false;
            }
            entries.remove(index);
        }
        entries.add(firstRecentIndex(entries), new Entry(value, false));
        trim(entries);
        return true;
    }

    /** 語を消す。変わったら true。 */
    public boolean remove(String player, String text) {
        List<Entry> entries = entries(player);
        int index = indexOf(entries, text);
        if (index < 0) {
            return false;
        }
        entries.remove(index);
        return true;
    }

    /** お気に入りを付ける・外す。お気に入りの数が上限なら付けない。変わったら true。 */
    public boolean toggleFavorite(String player, String text) {
        List<Entry> entries = entries(player);
        int index = indexOf(entries, text);
        if (index < 0) {
            return false;
        }
        Entry existing = entries.remove(index);
        if (existing.favorite()) {
            entries.add(firstRecentIndex(entries), new Entry(existing.text(), false));
        } else {
            if (firstRecentIndex(entries) >= MAX_FAVORITES) {
                entries.add(index, existing);
                return false;
            }
            entries.add(firstRecentIndex(entries), new Entry(existing.text(), true));
        }
        trim(entries);
        return true;
    }

    /** 出す順(お気に入り → 新しい順)の一覧の写し。 */
    public List<Entry> list(String player) {
        return List.copyOf(players.getOrDefault(player, List.of()));
    }

    private List<Entry> entries(String player) {
        return players.computeIfAbsent(player, p -> new ArrayList<>());
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String value = text.strip();
        return value.isEmpty() || value.length() > MAX_LENGTH ? null : value;
    }

    private static int indexOf(List<Entry> entries, String text) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).text().equals(text)) {
                return i;
            }
        }
        return -1;
    }

    private static int firstRecentIndex(List<Entry> entries) {
        int i = 0;
        while (i < entries.size() && entries.get(i).favorite()) {
            i++;
        }
        return i;
    }

    private static void trim(List<Entry> entries) {
        int recent = entries.size() - firstRecentIndex(entries);
        while (recent > MAX_RECENT) {
            entries.remove(entries.size() - 1);
            recent--;
        }
    }

    // ---- ファイル ----

    /** ファイルから読む。無ければ空。形の崩れた部分は捨てる(読めた部分は使う)。 */
    public static SearchHistoryStore load(Path file) throws IOException {
        SearchHistoryStore store = new SearchHistoryStore();
        if (!Files.exists(file)) {
            return store;
        }
        store.read(Files.readString(file, StandardCharsets.UTF_8));
        return store;
    }

    void read(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("players")
                || !root.getAsJsonObject().get("players").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> player : root.getAsJsonObject().getAsJsonObject("players").entrySet()) {
            if (!player.getValue().isJsonArray()) {
                continue;
            }
            List<Entry> favorites = new ArrayList<>();
            List<Entry> recent = new ArrayList<>();
            for (JsonElement e : player.getValue().getAsJsonArray()) {
                if (!e.isJsonObject() || !e.getAsJsonObject().has("text")
                        || !e.getAsJsonObject().get("text").isJsonPrimitive()) {
                    continue;
                }
                String value = normalize(e.getAsJsonObject().get("text").getAsString());
                if (value == null || indexOf(favorites, value) >= 0 || indexOf(recent, value) >= 0) {
                    continue;
                }
                JsonElement fav = e.getAsJsonObject().get("favorite");
                boolean favorite = fav != null && fav.isJsonPrimitive() && fav.getAsBoolean();
                if (favorite && favorites.size() < MAX_FAVORITES) {
                    favorites.add(new Entry(value, true));
                } else if (recent.size() < MAX_RECENT) {
                    recent.add(new Entry(value, false));
                }
            }
            List<Entry> entries = entries(player.getKey());
            entries.clear();
            entries.addAll(favorites);
            entries.addAll(recent);
        }
    }

    String write() {
        JsonObject players = new JsonObject();
        for (Map.Entry<String, List<Entry>> player : this.players.entrySet()) {
            JsonArray array = new JsonArray();
            for (Entry entry : player.getValue()) {
                JsonObject e = new JsonObject();
                e.addProperty("text", entry.text());
                if (entry.favorite()) {
                    e.addProperty("favorite", true);
                }
                array.add(e);
            }
            players.add(player.getKey(), array);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("players", players);
        return GSON.toJson(root) + "\n";
    }

    /** ファイルへ書く(途中で落ちても前のファイルを壊さないよう、隣に書いてから置き換える)。 */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, write(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
