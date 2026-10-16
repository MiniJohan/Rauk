package rauk.com.entity;

import net.minecraft.world.entity.EntityDimensions;

/**
 * How Rauk holds its body, and the collision box that goes with it (design doc 2.2). It only
 * changes to a posture whose box fits where it is.
 */
public enum RaukPosture {
	/** Standing: only in big rooms with 7 or more blocks of clearance. */
	UPRIGHT(1.2f, 6.4f, 5.4f),
	/** On all fours: 3 high, 2 wide. Most of its time in the hotel. */
	QUADRUPED(1.8f, 2.9f, 2.1f),
	/** The spider posture, belly near the floor: 2 high, 2 wide. */
	CRAWL(1.8f, 1.9f, 1.3f),
	/**
	 * Shoulders collapsed to get through doorways and 1-wide passages: 2 high, 1 wide. Narrow
	 * enough to pass an open door's panel.
	 */
	SQUEEZE(0.75f, 1.8f, 1.2f),
	/** Clinging to a wall or ceiling. */
	CLING(1.8f, 1.9f, 1.2f);

	public final float width, height, eyeHeight;

	RaukPosture(float width, float height, float eyeHeight) {
		this.width = width;
		this.height = height;
		this.eyeHeight = eyeHeight;
	}

	public EntityDimensions dimensions() {
		return EntityDimensions.scalable(width, height).withEyeHeight(eyeHeight);
	}

	public static RaukPosture byId(int id) {
		RaukPosture[] all = values();
		return all[Math.clamp(id, 0, all.length - 1)];
	}
}
