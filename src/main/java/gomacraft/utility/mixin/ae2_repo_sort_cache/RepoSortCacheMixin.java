package gomacraft.utility.mixin.ae2_repo_sort_cache;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import gomacraft.utility.Features;
import gomacraft.utility.features.ae2_repo_sort_cache.Ae2RepoSortCache;
import gomacraft.utility.features.ae2_repo_sort_cache.client.Ae2SortKeys;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 の端末の手元の一覧({@code Repo})の並べ直しを軽くする(理由は {@code Ae2RepoSortCache})。
 *
 * <ul>
 *   <li>並べ替えの鍵: {@code getKeyComparator} が返す比べ方を、鍵を作り置く同じ順序の比べ方に置き換える。</li>
 *   <li>並べ直しのまとめ: 差分の通信({@code handleUpdate})の終わりの {@code updateView} を、すぐには呼ばずに
 *       「済んでいない」の印にする。一覧を読む口({@code get}・{@code size}・{@code getPinnedEntries}・
 *       {@code hasPinnedRow})と {@code setPaused} の頭で、印が在れば 1 回だけ {@code updateView} を呼ぶ。
 *       ほかの誰かが {@code updateView} を呼べば(検索語・表示の切り替え)、そこで印を消す(作り直したので)。</li>
 * </ul>
 *
 * <p>⚠ 印の後始末(読む口での {@code updateView})は、機能の有効・無効に依らず走らせる。途中で FAILED になっても、
 * 済んでいない並べ直しを置き去りにしない。⚠ 元の処理({@code original.call}・{@code updateView})は guard の中に
 * 入れない(guard が例外を飲み、AE2 の例外まで隠す)。
 */
@Mixin(Repo.class)
public abstract class RepoSortCacheMixin {
    /** 差分の通信を受けたが、まだ並べ直していない。 */
    @Unique
    private boolean gomacraftUtility$viewPending;

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$key_comparator", min = 1)
    @ModifyReturnValue(
            method = "getKeyComparator(Lappeng/api/config/SortOrder;Lappeng/api/config/SortDir;)Ljava/util/Comparator;",
            at = @At("RETURN"), require = 0)
    private Comparator<AEKey> gomacraftUtility$cacheSortKeys(Comparator<AEKey> original,
            @Local(argsOnly = true) SortOrder order, @Local(argsOnly = true) SortDir dir) {
        return Features.guard(Ae2RepoSortCache.ID, () -> Ae2SortKeys.comparator(order, dir, original), original);
    }

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$defer_view", min = 1)
    @WrapOperation(method = "handleUpdate(ZLjava/util/List;)V",
            at = @At(value = "INVOKE", target = "Lappeng/client/gui/me/common/Repo;updateView()V"), require = 0)
    private void gomacraftUtility$deferView(Repo repo, Operation<Void> original) {
        // ⚠ 止めている間(isPaused)は、AE2 が 1 本ごとに後ろへ足す形をそのまま通す(まとめると足す順が変わりうる)
        if (Features.registry().isOn(Ae2RepoSortCache.ID) && !repo.isPaused()) {
            gomacraftUtility$viewPending = true;
            return;
        }
        original.call(repo);
    }

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$view_rebuilt", min = 1)
    @Inject(method = "updateView()V", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$viewRebuilt(CallbackInfo ci) {
        // ⚠ 頭で消す(updateView の中の updateViewListener が size() を読み、ここで消していないと読む口がまた呼ぶ)
        gomacraftUtility$viewPending = false;
    }

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$flush_get", min = 1)
    @Inject(method = "get(I)Lappeng/menu/me/common/GridInventoryEntry;", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforeGet(CallbackInfo ci) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$flush_size", min = 1)
    @Inject(method = "size()I", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforeSize(CallbackInfo ci) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$flush_pinned", min = 1)
    @Inject(method = "getPinnedEntries()Ljava/util/List;", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforePinned(CallbackInfo ci) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$flush_has_pinned", min = 1)
    @Inject(method = "hasPinnedRow()Z", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforeHasPinned(CallbackInfo ci) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$flush_paused", min = 1)
    @Inject(method = "setPaused(Z)V", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforePause(CallbackInfo ci) {
        // 止める前に、止める前に届いていた分を並べ直しておく(AE2 なら届いた時点で並べ直していた)
        gomacraftUtility$flushView();
    }

    @Unique
    private void gomacraftUtility$flushView() {
        if (gomacraftUtility$viewPending) {
            gomacraftUtility$viewPending = false;
            ((Repo) (Object) this).updateView();
        }
    }
}
