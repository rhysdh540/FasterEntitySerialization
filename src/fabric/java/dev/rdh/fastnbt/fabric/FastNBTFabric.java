package dev.rdh.fastnbt.fabric;

import net.fabricmc.api.ModInitializer;

import dev.rdh.fastnbt.FastNBT;

public final class FastNBTFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		FastNBT.init();
	}
}
