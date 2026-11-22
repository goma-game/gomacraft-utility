package gomacraft.utility;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * mixin の当て先が、組み立ての版の Mod(gradle.properties の版)に在るかを、class ファイルを ASM で読んで確かめる。
 *
 * <p>クライアントの画面に当てる機能は、起動の自己検査(gametest のサーバ)では当たるかを見られない(サーバに当て先の
 * クラスが無い)。本当に当たったかは、gomabase のクライアントの起動の検証が、起動時の 1 行の FAILED で見る。
 * ここはその手前で、版を上げたときに当て先が消えたことを CI で先に知るためのもの。
 *
 * <p>見るもの: 当て先のクラス、injector の {@code method}(名前と、書いてあれば descriptor)、{@code @At} の
 * INVOKE・FIELD の当て先がそのメソッドの中に在ること、{@code @Shadow}・{@code @Accessor} の field とメソッド。
 * ⚠ 知らない {@code @At} の種類・知らない注釈の形は落とす(黙って見ないことにしない)。
 */
class MixinTargetsTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

    @Test
    void everyTargetExistsInTheBuildVersions() throws Exception {
        int checked = 0;
        for (String name : mixinClasses()) {
            String feature = gomacraft.utility.mixinsupport.GomacraftMixinPlugin.featureOf(name);
            // わざと壊した機能(自己検査のため)は、当て先が無いのが正しい
            if (Features.SELFTEST_FEATURES.contains(feature)) {
                continue;
            }
            ClassNode mixin = read(name.replace('.', '/'));
            assertNotNull(mixin, "mixin の class が無い: " + name);
            List<String> targets = targets(mixin);
            assertTrue(!targets.isEmpty(), name + ": @Mixin の当て先を読めない");
            for (String target : targets) {
                ClassNode node = read(target);
                assertNotNull(node, name + ": 当て先のクラスが無い: " + target);
                checkMembers(name, mixin, node);
                checked++;
            }
        }
        assertTrue(checked > 0, "確かめた当て先が 0(一覧を読めていない)");
    }

    private static void checkMembers(String name, ClassNode mixin, ClassNode target) {
        for (FieldNode field : mixin.fields) {
            if (has(field.visibleAnnotations, SHADOW) || has(field.invisibleAnnotations, SHADOW)) {
                assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)),
                        name + ": @Shadow の field " + field.name + " が " + target.name + " に無い");
            }
        }
        for (MethodNode method : mixin.methods) {
            for (AnnotationNode a : annotations(method)) {
                String where = name + "#" + method.name;
                if (a.desc.equals(SHADOW)) {
                    assertTrue(target.methods.stream().anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)),
                            where + ": @Shadow のメソッドが " + target.name + " に無い");
                } else if (a.desc.equals(ACCESSOR)) {
                    String fieldName = (String) value(a, "value");
                    assertNotNull(fieldName, where + ": @Accessor に field の名前を書く(名前からの推測に頼らない)");
                    Type type = Type.getReturnType(method.desc);
                    assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(fieldName) && f.desc.equals(type.getDescriptor())),
                            where + ": @Accessor の field " + fieldName + " が " + target.name + " に無い");
                } else if (a.desc.equals(INVOKER)) {
                    String methodName = (String) value(a, "value");
                    assertNotNull(methodName, where + ": @Invoker にメソッドの名前を書く");
                    assertTrue(target.methods.stream().anyMatch(m -> m.name.equals(methodName) && m.desc.equals(method.desc)),
                            where + ": @Invoker のメソッド " + methodName + " が " + target.name + " に無い");
                } else if (MixinContractTest.isInjector(a.desc)) {
                    checkInjector(where, a, target);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkInjector(String where, AnnotationNode injector, ClassNode target) {
        List<String> selectors = (List<String>) value(injector, "method");
        assertNotNull(selectors, where + ": injector に method が無い");
        List<AnnotationNode> ats = new ArrayList<>();
        Object at = value(injector, "at");
        if (at instanceof AnnotationNode node) {
            ats.add(node);
        } else if (at instanceof List<?> list) {
            for (Object o : list) {
                ats.add((AnnotationNode) o);
            }
        } else {
            fail(where + ": @At を読めない");
        }
        for (String selector : selectors) {
            int paren = selector.indexOf('(');
            String methodName = paren < 0 ? selector : selector.substring(0, paren);
            String desc = paren < 0 ? null : selector.substring(paren);
            List<MethodNode> found = target.methods.stream()
                    .filter(m -> m.name.equals(methodName) && (desc == null || m.desc.equals(desc)))
                    .toList();
            assertTrue(!found.isEmpty(), where + ": 当て先のメソッド " + selector + " が " + target.name + " に無い");
            for (AnnotationNode a : ats) {
                String kind = (String) value(a, "value");
                String atTarget = (String) value(a, "target");
                switch (kind) {
                    case "HEAD", "RETURN", "TAIL" -> {
                    }
                    case "INVOKE" -> assertTrue(found.stream().anyMatch(m -> calls(m, atTarget)),
                            where + ": " + selector + " の中に呼び出し " + atTarget + " が無い");
                    case "FIELD" -> assertTrue(found.stream().anyMatch(m -> touches(m, atTarget)),
                            where + ": " + selector + " の中に field " + atTarget + " が無い");
                    default -> fail(where + ": この試験が知らない @At の種類 " + kind + "(ここに見方を足す)");
                }
            }
        }
    }

    private static boolean calls(MethodNode method, String target) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call
                    && target.equals("L" + call.owner + ";" + call.name + call.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touches(MethodNode method, String target) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof FieldInsnNode f && target.equals("L" + f.owner + ";" + f.name + ":" + f.desc)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> targets(ClassNode mixin) {
        List<String> out = new ArrayList<>();
        for (AnnotationNode a : annotations(mixin)) {
            if (!a.desc.equals(MIXIN)) {
                continue;
            }
            Object value = value(a, "value");
            if (value instanceof List<?> types) {
                for (Object t : types) {
                    out.add(((Type) t).getInternalName());
                }
            }
            Object names = value(a, "targets");
            if (names instanceof List<?> list) {
                for (Object n : list) {
                    out.add(((String) n).replace('.', '/'));
                }
            }
        }
        return out;
    }

    private static List<String> mixinClasses() throws IOException {
        JsonObject config;
        try (InputStream in = MixinTargetsTest.class.getClassLoader().getResourceAsStream("gomacraft_utility.mixins.json")) {
            assertNotNull(in, "mixin の設定が無い");
            config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String pkg = config.get("package").getAsString() + ".";
        List<String> names = new ArrayList<>();
        for (String list : List.of("mixins", "client", "server")) {
            JsonArray array = config.has(list) ? config.getAsJsonArray(list) : new JsonArray();
            for (JsonElement e : array) {
                names.add(pkg + e.getAsString());
            }
        }
        return names;
    }

    private static ClassNode read(String internalName) {
        try (InputStream in = MixinTargetsTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<AnnotationNode> annotations(ClassNode node) {
        List<AnnotationNode> out = new ArrayList<>();
        if (node.visibleAnnotations != null) {
            out.addAll(node.visibleAnnotations);
        }
        if (node.invisibleAnnotations != null) {
            out.addAll(node.invisibleAnnotations);
        }
        return out;
    }

    private static List<AnnotationNode> annotations(MethodNode node) {
        List<AnnotationNode> out = new ArrayList<>();
        if (node.visibleAnnotations != null) {
            out.addAll(node.visibleAnnotations);
        }
        if (node.invisibleAnnotations != null) {
            out.addAll(node.invisibleAnnotations);
        }
        return out;
    }

    private static boolean has(List<AnnotationNode> list, String desc) {
        return list != null && list.stream().anyMatch(a -> a.desc.equals(desc));
    }

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
