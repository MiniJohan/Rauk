package rauk.com.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;

/** The biome source a world's structures are placed by, to find the hotel's coast. */
@Mixin(ChunkGeneratorStructureState.class)
public interface StructureStateAccessor {
	@Accessor("biomeSource")
	BiomeSource rauk$biomeSource();
}
