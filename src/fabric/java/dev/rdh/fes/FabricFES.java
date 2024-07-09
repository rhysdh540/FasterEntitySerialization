package dev.rdh.fes;

import net.fabricmc.api.ModInitializer;

public final class FabricFES implements ModInitializer {
	@Override
	public void onInitialize() {
		FasterEntitySerialization.registerAll();
	}
}
