package gomacraft.utility.mixin.ae2_repo_update_batch;

import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.GridInventoryEntry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import gomacraft.utility.Features;
import gomacraft.utility.features.ae2_repo_update_batch.Ae2RepoUpdateBatch;
import gomacraft.utility.features.ae2_repo_update_batch.ViewFlush;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 の端末の手元の一覧({@code Repo})の並べ直しを、一覧を読まれる前の 1 回にまとめる(理由は
 * {@code Ae2RepoUpdateBatch})。
 *
 * <ul>
 *   <li>差分の通信({@code handleUpdate})の終わりの {@code updateView} を、すぐには呼ばずに「済んでいない」の印にする。</li>
 *   <li>一覧を読む口({@code get}・{@code size}・{@code getPinnedEntries}・{@code hasPinnedRow})と {@code setPaused} の
 *       頭で、印が在れば 1 回だけ {@code updateView} を呼ぶ。</li>
 *   <li>⚠ 読む口で並べ直すのは描画のスレッドだけ。ほかのスレッド(EMI の検索の焼き付け)からの読みには、並べ直す前の
 *       一覧を返す(同じ一覧を 2 つのスレッドで同時に並べ替えると AE2 の処理が落ちる —— {@code ViewFlush})。</li>
 *   <li>ほかの誰かが {@code updateView} を呼べば(検索語・表示の切り替え)、そこで印を消す(作り直したので)。</li>
 * </ul>
 *
 * <p>⚠ 印の後始末(読む口での {@code updateView})は、機能の有効・無効に依らず走らせる。途中で FAILED になっても、
 * 済んでいない並べ直しを置き去りにしない。⚠ 元の処理({@code original.call}・{@code updateView})は guard の中に
 * 入れない(guard が例外を飲み、AE2 の例外まで隠す)。
 */
@Mixin(Repo.class)
public abstract class RepoUpdateBatchMixin {
    /** 差分の通信を受けたが、まだ並べ直していない(ほかのスレッドからも読むので volatile)。 */
    @Unique
    private volatile boolean gomacraftUtility$viewPending;

    @Group(name = "gomacraft_utility$ae2_repo_update_batch$defer_view", min = 1)
    @WrapOperation(method = "handleUpdate(ZLjava/util/List;)V",
            at = @At(value = "INVOKE", target = "Lappeng/client/gui/me/common/Repo;updateView()V"), require = 0)
    private void gomacraftUtility$deferView(Repo repo, Operation<Void> original) {
        // ⚠ 止めている間(isPaused)は、AE2 が 1 本ごとに後ろへ足す形をそのまま通す(まとめると足す順が変わりうる)
        if (Features.registry().isOn(Ae2RepoUpdateBatch.ID) && !repo.isPaused()) {
            gomacraftUtility$viewPending = true;
            return;
        }
        original.call(repo);
    }

    @Group(name = "gomacraft_utility$ae2_repo_update_batch$view_rebuilt", min = 1)
    @Inject(method = "updateView()V", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$viewRebuilt(CallbackInfo ci) {
        // ⚠ 頭で消す(updateView の中の updateViewListener が size() を読み、ここで消していないと読む口がまた呼ぶ)
        gomacraftUtility$viewPending = false;
    }

    @Group(name = "gomacraft_utility$ae2_repo_update_batch$flush_get", min = 1)
    @Inject(method = "get(I)Lappeng/menu/me/common/GridInventoryEntry;", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforeGet(int idx, CallbackInfoReturnable<GridInventoryEntry> cir) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_update_batch$flush_size", min = 1)
    @Inject(method = "size()I", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforeSize(CallbackInfoReturnable<Integer> cir) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_update_batch$flush_pinned", min = 1)
    @Inject(method = "getPinnedEntries()Ljava/util/List;", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforePinned(CallbackInfoReturnable<List<GridInventoryEntry>> cir) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_update_batch$flush_has_pinned", min = 1)
    @Inject(method = "hasPinnedRow()Z", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforeHasPinned(CallbackInfoReturnable<Boolean> cir) {
        gomacraftUtility$flushView();
    }

    @Group(name = "gomacraft_utility$ae2_repo_update_batch$flush_paused", min = 1)
    @Inject(method = "setPaused(Z)V", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$flushBeforePause(CallbackInfo ci) {
        // 止める前に、止める前に届いていた分を並べ直しておく(AE2 なら届いた時点で並べ直していた)
        gomacraftUtility$flushView();
    }

    @Unique
    private void gomacraftUtility$flushView() {
        // ⚠ 並べ直すのは描画のスレッドだけ(ほかのスレッドには並べ直す前の一覧を返し、印は残す —— ViewFlush)
        if (ViewFlush.mayFlush(gomacraftUtility$viewPending, RenderSystem.isOnRenderThread())) {
            gomacraftUtility$viewPending = false;
            ((Repo) (Object) this).updateView();
        }
    }
}
