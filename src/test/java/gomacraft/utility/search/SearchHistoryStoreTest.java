package gomacraft.utility.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        Files.writeString(file, "{\"players\":{\"p\":[{\"text\":\"x\"},{\"nope\":1},\"bad\",{\"text\":\"x\"}],\"q\":3}}");
        assertEquals(List.of("x"), texts(SearchHistoryStore.load(file), "p"));
        assertEquals(List.of(), texts(SearchHistoryStore.load(dir.resolve("missing.json")), "p"));
    }
}
