package rauk.com.entity.move;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukPosture;

/**
 * A place Rauk can be: an air cell, the side of it whose block it holds on to (DOWN means
 * standing on the floor, UP hanging from the ceiling, the others clinging to a wall), the largest
 * posture whose box fits there, and the exact position (the bottom centre of that box).
 */
public record SurfaceNode(BlockPos cell, Direction surface, RaukPosture posture, Vec3 position) {
	public boolean onFloor() {
		return surface == Direction.DOWN;
	}

	public Vec3 center() {
		return Vec3.atCenterOf(cell);
	}
}
