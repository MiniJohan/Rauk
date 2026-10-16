package rauk.com.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import rauk.com.Rauk;
import rauk.com.block.BloodBlock;
import rauk.com.block.ShutterBlock;

public final class RaukBlocks {
	public static final BloodBlock BLOOD = Registry.register(BuiltInRegistries.BLOCK, Rauk.id("blood"),
			new BloodBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollission().noOcclusion().replaceable()
					.instabreak().noLootTable().randomTicks().sound(SoundType.SLIME_BLOCK).pushReaction(PushReaction.DESTROY)));
	/** The lockdown shutters over the hotel's windows and doors: unbreakable, like the lockdown. */
	public static final ShutterBlock SHUTTER = Registry.register(BuiltInRegistries.BLOCK, Rauk.id("shutter"),
			new ShutterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0f, 3600000.0f).noLootTable()
					.sound(SoundType.METAL).isValidSpawn((state, level, pos, type) -> false).pushReaction(PushReaction.BLOCK)));

	private RaukBlocks() {
	}

	public static void init() {
	}
}
