package dev.rdh.fes;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import net.minecraft.nbt.ByteTag;
import net.minecraft.world.entity.Entity;

@Mod(FasterEntitySerialization.ID)
public final class ForgeFES {
	public ForgeFES() {
		FasterEntitySerialization.registerAll();
		FasterEntitySerialization.register("CanUpdate", entity -> ByteTag.valueOf(entity.canUpdate()));
		FasterEntitySerialization.register(AttachmentHolder.ATTACHMENTS_NBT_KEY, Entity::serializeAttachments); // may return null
		FasterEntitySerialization.register("NeoForgeData", entity -> entity.getPersistentData().copy());
	}
}
