package dev.rdh.fastnbt;

import net.fabricmc.api.ModInitializer;

public final class FastNBTFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		FastNBT.init();
	}
}
