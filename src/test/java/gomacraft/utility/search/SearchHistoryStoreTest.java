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
