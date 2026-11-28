package gomacraft.utility.feature;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 起動時の 1 行。gomabase の起動の検証(クライアントとサーバ)がこの行を読むので、形は 2 つのリポジトリの間の約束。
 *
 * <pre>
 * gomacraft_utility: features none
 * gomacraft_utility: features &lt;id&gt;=&lt;on|off|FAILED&gt;[(&lt;理由&gt;)] ...
 * </pre>
 *
 * <ul>
 *   <li>語は空白 1 つで区切る。id は {@code [a-z0-9_]+}。理由は括弧を含まない(空白は含みうる)。</li>
 *   <li>状態が変わったら(起動の後に mixin の当て損ねが分かったときなど)、同じ形の行をもう一度出す。
 *       読む側は、全部の行のどれかに FAILED があれば壊れたと見る。</li>
 * </ul>
 */
public final class StatusLine {
    public static final String PREFIX = "gomacraft_utility: features ";

    private StatusLine() {
    }

    public static String render(Collection<FeatureStatus> statuses) {
        if (statuses.isEmpty()) {
            return PREFIX + "none";
        }
        return PREFIX + statuses.stream().map(FeatureStatus::token).collect(Collectors.joining(" "));
    }
}
