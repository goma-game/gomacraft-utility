package gomacraft.utility.feature;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

/** 機能の初期化に渡すもの。 */
public record FeatureContext(IEventBus modBus, Dist dist) {
}
