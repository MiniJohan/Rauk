package rauk.com.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * The blocks Rauk finds its way through, by world position: the world itself, or the hotel's plan
 * (which can be read away from the game's thread).
 */
public interface NavView {
	NavBlock at(int x, int y, int z);

	default NavBlock at(BlockPos p) {
		return at(p.getX(), p.getY(), p.getZ());
	}

	/** The world as it is. One per search: it isn't safe to share between threads. */
	static NavView of(BlockGetter level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		return (x, y, z) -> NavBlock.of(level.getBlockState(pos.set(x, y, z)));
	}
}
