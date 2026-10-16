package rauk.com.entity.move;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.nav.NavBlock;
import rauk.com.entity.nav.NavRules;

/**
 * Where Rauk can be in the world as it is (design doc 4): the places its body fits, on floors,
 * walls and ceilings, by the rules of {@link NavRules}. The ways between them are found by
 * {@link rauk.com.entity.nav.NavSearch} and the hotel's map ({@link rauk.com.hotel.HotelGraph}).
 */
public final class RaukPathfinder {
	public static final TagKey<Block> UNCLIMBABLE = NavBlock.UNCLIMBABLE;

	/**
	 * Extra cost for being at a place, on top of distance: each intent prices the world its own way
	 * (design doc 6.1). Stalking pays for being seen, for light and for noisy floors; chasing only
	 * for time.
	 */
	public interface Costs {
		Costs NONE = node -> 0;

		double extra(SurfaceNode node);
	}

	private final NavRules rules;

	public RaukPathfinder(Level level) {
		this.rules = NavRules.world(level);
	}

	public NavRules rules() {
		return rules;
	}

	/** Where Rauk can be in this cell, holding this side; null if it can't. */
	@Nullable
	public SurfaceNode node(BlockPos cell, Direction surface) {
		return rules.node(cell, surface);
	}

	@Nullable
	public Vec3 dropPoint(Vec3 landing, double fromY, RaukPosture posture) {
		return rules.dropPoint(landing, fromY, posture);
	}

	public String explain(BlockPos cell, Direction surface) {
		return rules.explain(cell, surface);
	}

	public static Vec3 position(BlockPos cell, Direction surface, RaukPosture p) {
		return NavRules.position(cell, surface, p);
	}

	/** Whether the block stops it: anything solid taller than a carpet, except doors it can open. */
	public boolean blocked(BlockPos pos) {
		return rules.blocked(pos);
	}

	public boolean grips(BlockPos pos, Direction face) {
		return rules.grips(pos, face);
	}

	public static boolean openable(BlockState state) {
		return NavBlock.openable(state);
	}

	/** Whether a box is clear of every block, doors it can open counted open. */
	public boolean fits(AABB box) {
		return rules.fits(box);
	}
}
