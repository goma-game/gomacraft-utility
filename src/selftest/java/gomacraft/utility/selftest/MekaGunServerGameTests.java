package gomacraft.utility.selftest;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.features.mekagun_server.MekaGunServerFix;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 機能 {@code mekagun_server} の自己検査(配る jar には入らない)。gametest のサーバは専用サーバと同じく
 * クライアントのクラスを持たないので、直していなければ Meka-Gun を撃った時点で {@code NoClassDefFoundError} になる。
 */
@GameTestHolder(GomacraftUtility.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MekaGunServerGameTests {
    private MekaGunServerGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mekaGunFiresOnTheDedicatedServer(GameTestHelper helper) {
        helper.assertTrue(Features.registry().isOn(MekaGunServerFix.ID),
                "mekagun_server は on のはず: " + Features.registry().status(MekaGunServerFix.ID));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekaweapons:meka_gun"));
        helper.assertTrue(item != Items.AIR, "mekaweapons:meka_gun が無い");
        ItemStack gun = new ItemStack(item);
        IEnergyContainer energy = StorageUtils.getEnergyContainer(gun, 0);
        helper.assertTrue(energy != null, "Meka-Gun のエネルギーの入れ物が無い");
        energy.setEnergy(energy.getMaxEnergy());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // サバイバルで撃つ(クリエイティブはエネルギーを使わないので、撃ったかを確かめられない)
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        // ⚠ 撃つ間隔(最後に撃った時刻からの tick)を満たすよう、時刻が進んでから撃つ
        helper.runAfterDelay(40, () -> {
            // 撃つ(当たり判定・エネルギー・熱はサーバの処理のまま。外したのはクライアントの部品の呼び出しだけ)
            long now = helper.getLevel().getGameTime();
            // 撃つと、最後に撃った時刻が今の時刻になる(この記録の後に、音の呼び出しが続く —— 4.3 の onUseTick)。
            // ⚠ 直していなければ、その音の呼び出しで NoClassDefFoundError になり、ここへ戻らない
            gun.getItem().onUseTick(helper.getLevel(), player, gun, gun.getUseDuration(player));
            DataComponentType<?> lastFire =
                    BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("mekaweapons:last_fire_tick"));
            Object fired = lastFire == null ? null : gun.get(lastFire);
            helper.assertTrue(fired instanceof Long tick && tick == now,
                    "撃っていない(最後に撃った時刻が今でない): now=" + now + " last_fire_tick=" + fired);
            gun.getItem().releaseUsing(gun, helper.getLevel(), player, 0);
            helper.succeed();
        });
    }
}
