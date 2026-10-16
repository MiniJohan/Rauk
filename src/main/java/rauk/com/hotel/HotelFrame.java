package rauk.com.hotel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a hotel stands in the world: the world position of its plan's origin (the north-west
 * corner of the footprint, at the lobby floor) and which way it is turned. The plan's north (the
 * sea side) turns with it, so the back of the hotel always faces the sea.
 */
public record HotelFrame(BlockPos origin, Rotation rotation) {
	/** How far round the hotel the ground is shaped to meet it. */
	public static final int MARGIN = 12;

	/** A plan x, z turned by a rotation (about the plan's origin). */
	public static int[] rotate(int x, int z, Rotation rotation) {
		return switch (rotation) {
			case NONE -> new int[] {x, z};
			case CLOCKWISE_90 -> new int[] {-z, x};
			case CLOCKWISE_180 -> new int[] {-x, -z};
			case COUNTERCLOCKWISE_90 -> new int[] {z, -x};
		};
	}

	public BlockPos toWorld(int x, int y, int z) {
		int[] r = rotate(x, z, rotation);
		return origin.offset(r[0], y, r[1]);
	}

	/** The plan cell a world block is in. */
	public int[] toPlan(int wx, int wy, int wz) {
		int dx = wx - origin.getX(), dz = wz - origin.getZ();
		int[] r = switch (rotation) {
			case NONE -> new int[] {dx, dz};
			case CLOCKWISE_90 -> new int[] {dz, -dx};
			case CLOCKWISE_180 -> new int[] {-dx, -dz};
			case COUNTERCLOCKWISE_90 -> new int[] {-dz, dx};
		};
		return new int[] {r[0], wy - origin.getY(), r[1]};
	}

	public int[] toPlan(BlockPos pos) {
		return toPlan(pos.getX(), pos.getY(), pos.getZ());
	}

	/** Whether a block is part of the building (its walls included), not the ground round it. */
	public boolean inBuilding(BlockPos pos) {
		int[] p = toPlan(pos);
		return HotelPlan.inBuilding(p[0], p[1], p[2]);
	}

	/** The world box of a box in the plan. */
	public BoundingBox box(int x0, int y0, int z0, int x1, int y1, int z1) {
		return BoundingBox.fromCorners(toWorld(x0, y0, z0), toWorld(x1, y1, z1));
	}

	/** Everything the hotel touches: the plan box and the shaped ground round it. */
	public BoundingBox bounds() {
		return box(-MARGIN, HotelPlan.Y0, -MARGIN, HotelPlan.WIDTH - 1 + MARGIN, HotelPlan.Y0 + HotelPlan.SY - 1,
				HotelPlan.TOTAL_DEPTH - 1 + MARGIN).encapsulate(box(HotelPlan.X0, HotelPlan.Y0, HotelPlan.Z0, HotelPlan.X0 + HotelPlan.SX - 1,
						HotelPlan.Y0 + HotelPlan.SY - 1, HotelPlan.Z0 + HotelPlan.SZ - 1));
	}

	/** The middle of the building, at the lobby floor. */
	public BlockPos center() {
		return toWorld(HotelPlan.WIDTH / 2, 0, HotelPlan.TOTAL_DEPTH / 2);
	}
}
