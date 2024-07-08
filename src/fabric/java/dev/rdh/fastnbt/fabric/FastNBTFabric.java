package dev.rdh.fastnbt.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;

import dev.rdh.fastnbt.FastNBT;

public final class FastNBTFabric implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		FastNBT.init();
	}
}
