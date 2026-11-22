package gomacraft.utility.feature;

import java.util.Objects;

/**
 * 1 つの機能の状態と、その理由。
 *
 * <p>理由は起動時の 1 行の括弧の中に出すので、括弧と改行を含めない({@link #sanitize})。
 */
public record FeatureStatus(String id, FeatureState state, String reason) {
    private static final int MAX_REASON = 160;

    public FeatureStatus {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        reason = reason == null || reason.isBlank() ? null : sanitize(reason);
    }

    /** 起動時の 1 行の 1 語({@code id=state} か {@code id=state(理由)})。 */
    public String token() {
        return reason == null ? id + "=" + state.word() : id + "=" + state.word() + "(" + reason + ")";
    }

    /** 括弧を角括弧に、改行と連続する空白を 1 つの空白にし、長さを切る。 */
    static String sanitize(String text) {
        String s = text.replace('(', '[').replace(')', ']').replaceAll("\\s+", " ").strip();
        return s.length() <= MAX_REASON ? s : s.substring(0, MAX_REASON - 3) + "...";
    }
}
