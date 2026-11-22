package gomacraft.utility.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchHistoryStoreTest {
    private static List<String> texts(SearchHistoryStore store, String player) {
        return store.list(player).stream().map(SearchHistoryStore.Entry::text).toList();
    }

    @Test
    void recentFirstAndDuplicatesMoveUp() {
        SearchHistoryStore store = new SearchHistoryStore();
        store.record("p", "iron");
        store.record("p", "gold");
        store.record("p", " iron ");
        assertEquals(List.of("iron", "gold"), texts(store, "p"));
        assertFalse(store.record("p", "   "));
        assertEquals(List.of(), texts(store, "other"));
    }

    @Test
    void favoritesStayOnTopAndAreNotTrimmed() {
        SearchHistoryStore store = new SearchHistoryStore();
        store.record("p", "fav");
        assertTrue(store.toggleFavorite("p", "fav"));
        for (int i = 0; i < SearchHistoryStore.MAX_RECENT + 5; i++) {
            store.record("p", "t" + i);
        }
        List<SearchHistoryStore.Entry> list = store.list("p");
        assertEquals("fav", list.get(0).text());
        assertTrue(list.get(0).favorite());
        assertEquals(1 + SearchHistoryStore.MAX_RECENT, list.size());
        assertEquals("t" + (SearchHistoryStore.MAX_RECENT + 4), list.get(1).text());
        // お気に入りを使っても位置は変わらない
        assertFalse(store.record("p", "fav"));
        assertTrue(store.toggleFavorite("p", "fav"));
        assertFalse(store.list("p").get(0).favorite());
        assertTrue(store.remove("p", "fav"));
        assertFalse(texts(store, "p").contains("fav"));
    }

    @Test
    void roundTripsAndDropsBrokenParts(@TempDir Path dir) throws Exception {
        SearchHistoryStore store = new SearchHistoryStore();
        store.record("p", "a");
        store.record("p", "b");
        store.toggleFavorite("p", "a");
        Path file = dir.resolve("sub").resolve("history.json");
        store.save(file);
        assertEquals(List.of("a", "b"), texts(SearchHistoryStore.load(file), "p"));

        // 空・重なる語だけは捨てる
        Files.writeString(file, "{\"players\":{\"p\":[{\"text\":\"x\"},{\"text\":\"  \"},{\"text\":\"x\"}]}}");
        assertEquals(List.of("x"), texts(SearchHistoryStore.load(file), "p"));
        assertEquals(List.of(), texts(SearchHistoryStore.load(dir.resolve("missing.json")), "p"));
    }

    @Test
    void matchingFiltersByInputAndLimitIsConfigurable() {
        SearchHistoryStore store = new SearchHistoryStore(3);
        for (String t : List.of("iron ingot", "Gold Ingot", "iron", "copper")) {
            store.record("p", t);
        }
        assertEquals(List.of("copper", "iron", "Gold Ingot"), texts(store, "p"), "上限 3 件で古いものから消す");
        assertEquals(List.of("Gold Ingot"), store.matching("p", "ingot").stream()
                .map(SearchHistoryStore.Entry::text).toList(), "大文字小文字を区別しない");
        assertEquals(List.of(), store.matching("p", "IRON"), "入力そのものは出さない");
        assertEquals(3, store.matching("p", " ").size(), "空の入力なら全部");
        store.setMaxRecent(2);
        assertEquals(List.of("copper", "iron"), texts(store, "p"), "上限を減らしたら古いものから消す");
        store.setMaxRecent(10);
        store.record("p", "tin");
        assertEquals(List.of("tin", "copper", "iron"), texts(store, "p"));
    }

    @Test
    void wrongShapeThrowsInsteadOfReadingAsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("history.json");
        for (String bad : List.of(
                "[]",
                "{\"players\":[]}",
                "{\"version\":1}",
                "{\"players\":{\"p\":3}}",
                "{\"players\":{\"p\":[\"bad\"]}}",
                "{\"players\":{\"p\":[{\"nope\":1}]}}",
                "{\"players\":{\"p\":[{\"text\":1}]}}",
                "{\"players\":{\"p\":[{\"text\":\"x\",\"favorite\":\"yes\"}]}}",
                "{not json")) {
            Files.writeString(file, bad);
            assertThrows(RuntimeException.class, () -> SearchHistoryStore.load(file), bad);
        }
    }
}
