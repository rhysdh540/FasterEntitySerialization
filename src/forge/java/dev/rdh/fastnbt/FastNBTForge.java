package dev.rdh.fastnbt;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import net.minecraft.nbt.ByteTag;
import net.minecraft.world.entity.Entity;

@Mod(FastNBT.ID)
public final class FastNBTForge {
	public FastNBTForge() {
		FastNBT.registerAll();
		FastNBT.register("CanUpdate", entity -> ByteTag.valueOf(entity.canUpdate()));
		FastNBT.register(AttachmentHolder.ATTACHMENTS_NBT_KEY, Entity::serializeAttachments); // may return null
		FastNBT.register("NeoForgeData", entity -> entity.getPersistentData().copy());
	}
}
