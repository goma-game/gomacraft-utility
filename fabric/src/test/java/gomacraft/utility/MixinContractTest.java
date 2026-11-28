package gomacraft.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gomacraft.utility.mixinsupport.GomacraftMixinPlugin;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * mixin の書き方の約束。injector は全部 {@code require = 0} と {@code @Group(min >= 1)} で書く。
 *
 * <p>{@code require} の不足は Mixin が Error(InjectionError)を投げて起動ごと止まり、この Mod の
 * エラーの扱い({@code MixinFailureHandler})に届かない。group の不足は検証の例外になり、警告に落として
 * その機能だけを FAILED にできる(起動の自己検査の {@code selftest_at} で確かめている)。
 *
 * <p>Mixin の注釈の一部({@code @Group})は実行時に見えないので、class ファイルを ASM で読む。
 */
class MixinContractTest {
    private static final String INJECTION = "Lorg/spongepowered/asm/mixin/injection/";
    private static final String MIXINEXTRAS = "Lcom/llamalad7/mixinextras/";
    private static final String GROUP = INJECTION + "Group;";
    /**
     * injector でない、決まった部品(当て先の書き方・group・代わりのメソッド・MixinExtras の式)。
     * ⚠ これ以外の mixin と MixinExtras の注釈は、全部 injector として見る(実行時は MixinExtras も載るので、
     * 名前を並べて injector を決めると {@code @WrapOperation} などが素通りし、黙って当たらない)。
     */
    private static final Set<String> NOT_INJECTORS = Set.of(
            GROUP, INJECTION + "At;", INJECTION + "Slice;", INJECTION + "Desc;", INJECTION + "Surrogate;",
            INJECTION + "Coerce;", INJECTION + "Constant;",
            MIXINEXTRAS + "expression/Expression;", MIXINEXTRAS + "expression/Definition;",
            MIXINEXTRAS + "expression/Definitions;");

    static boolean isInjector(String desc) {
        return (desc.startsWith(INJECTION) || desc.startsWith(MIXINEXTRAS)) && !NOT_INJECTORS.contains(desc);
    }

    @Test
    void injectorsAreRecognisedFailClosed() {
        assertTrue(isInjector(INJECTION + "Inject;"));
        assertTrue(isInjector(MIXINEXTRAS + "injector/wrapoperation/WrapOperation;"));
        assertTrue(isInjector(MIXINEXTRAS + "injector/SomethingNewer;"), "知らない注釈は injector として見る");
        assertTrue(!isInjector(GROUP) && !isInjector(INJECTION + "At;"));
        assertTrue(!isInjector("Lorg/spongepowered/asm/mixin/Shadow;"));
    }

    @Test
    void everyInjectorHasRequireZeroAndAGroup() throws Exception {
        JsonObject config;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("gomacraft_utility.mixins.json")) {
            assertNotNull(in, "mixin の設定が無い");
            config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String pkg = config.get("package").getAsString() + ".";
        assertEquals(GomacraftMixinPlugin.MIXIN_PACKAGE, pkg);
        List<String> names = new ArrayList<>();
        for (String list : List.of("mixins", "client", "server")) {
            JsonArray array = config.has(list) ? config.getAsJsonArray(list) : new JsonArray();
            for (JsonElement e : array) {
                names.add(pkg + e.getAsString());
            }
        }
        int injectors = 0;
        java.util.Map<String, String> groups = new java.util.HashMap<>();
        for (String name : names) {
            assertNotNull(GomacraftMixinPlugin.featureOf(name), "機能の包みに無い mixin: " + name);
            ClassNode node = new ClassNode();
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
                assertNotNull(in, "mixin の class が無い: " + name);
                new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
            }
            for (MethodNode method : node.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (method.visibleAnnotations != null) {
                    annotations.addAll(method.visibleAnnotations);
                }
                if (method.invisibleAnnotations != null) {
                    annotations.addAll(method.invisibleAnnotations);
                }
                AnnotationNode group = annotations.stream().filter(a -> a.desc.equals(GROUP)).findFirst().orElse(null);
                for (AnnotationNode injector : annotations) {
                    if (!isInjector(injector.desc)) {
                        continue;
                    }
                    injectors++;
                    String where = name + "#" + method.name;
                    assertEquals(0, value(injector, "require"), where + ": require = 0 で書く(Error で起動ごと止まる)");
                    assertNotNull(group, where + ": @Group(min = 1) が無い(当て損ねを拾えない)");
                    Object min = value(group, "min");
                    assertTrue(min instanceof Integer m && m >= 1, where + ": @Group の min が 1 以上でない");
                    Object groupName = value(group, "name");
                    assertTrue(groupName instanceof String s && !s.isEmpty(), where + ": @Group に名前が無い");
                    // group は中の成功を合計で数える。共有すると片方の当て損ねが見えないので、injector ごとに分ける
                    String previous = groups.put((String) groupName, where);
                    assertTrue(previous == null, where + ": @Group の名前 " + groupName + " を " + previous + " と共有している");
                }
            }
        }
        // 0 件で通る形にしない(一覧を読めていない・注釈を読めていない、を緑にしない)
        assertTrue(injectors > 0, "injector を 1 つも見つけられない");
    }

    /** 注釈に書かれた値(書かれていなければ null)。 */
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }
}
