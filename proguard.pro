-ignorewarnings
-dontnote
-dontobfuscate
-optimizationpasses 10
-optimizations !class/merging/*,!method/marking/private,!method/marking/static,!*/specialization/*,!method/removal/parameter
-allowaccessmodification
#noinspection ShrinkerInvalidFlags
-optimizeaggressively
-keepattributes Runtime*Annotations,AnnotationDefault # keep annotations

-keep @org.spongepowered.asm.mixin.Mixin class * {
	@org.spongepowered.asm.mixin.Overwrite *;
	@org.spongepowered.asm.mixin.Shadow *;
}
-keepclassmembers,allowoptimization @org.spongepowered.asm.mixin.Mixin class * { *; }

-keep,allowoptimization @*.*.fml.common.Mod class * {
	public <init>(...);
}

-keep,allowoptimization class * implements net.fabricmc.api.ModInitializer {
    public void onInitialize();
}