package dev.rdh.fastnbt.forge;

import dev.rdh.fastnbt.FastNBT;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

import net.minecraft.nbt.ByteTag;
import net.minecraft.world.entity.Entity;

@Mod(FastNBT.ID)
public final class FastNBTForge {
	public FastNBTForge() {
		if(!FMLLoader.getDist().isDedicatedServer())
			return;
		FastNBT.init();
		FastNBT.register("CanUpdate", entity -> ByteTag.valueOf(entity.canUpdate()));
		FastNBT.register("neoforge:attachments", Entity::serializeAttachments); // may return null
		FastNBT.register("NeoForgeData", entity -> entity.getPersistentData().copy());
	}
}
