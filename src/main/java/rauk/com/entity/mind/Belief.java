package rauk.com.entity.mind;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Rauk's best guess about one prey (design doc 6.1): where it is, which way it was going, how sure
 * Rauk is, and when and how it last sensed it. Rauk acts on this guess, never on where the prey
 * really is, so the guess can be wrong. Between sightings the guess drifts along the prey's last
 * heading, slows, and grows less certain.
 */
public final class Belief {
	public enum Sense { SIGHT, HEARING, SMELL, TOUCH }

	/** Seconds for confidence to halve with nothing new sensed: slowly at first, then faster. */
	private static final double HALF_LIFE = 25;
	/** How fast the guess spreads, blocks per second, and the most it can spread. */
	private static final double SPREAD = 0.9, MAX_SPREAD = 24;

	public final UUID prey;
	public Vec3 position = Vec3.ZERO;
	public Vec3 velocity = Vec3.ZERO;
	/** 0 to 1: how sure it is the prey is near the guess. */
	public double confidence;
	/** Radius in blocks the prey is thought to be within. */
	public double uncertainty = MAX_SPREAD;
	public long lastSensed = Long.MIN_VALUE;
	/** When it last saw the prey. */
	public long lastSeen = Long.MIN_VALUE;
	public Sense lastSense = Sense.HEARING;
	/** Whether the prey is in sight right now. */
	public boolean seen;
	/** Exactly where it last saw the prey, and how it was moving then (the guess drifts on from there). */
	public Vec3 seenAt = Vec3.ZERO, seenVelocity = Vec3.ZERO;

	public Belief(UUID prey) {
		this.prey = prey;
	}

	/**
	 * Something sensed: blends the observation in by how sure it is against how sure the old guess
	 * still is. A clear sighting all but replaces the guess; a faint sound only nudges it.
	 */
	public void observe(Vec3 at, Vec3 moving, double accuracy, double sureness, Sense sense, long time) {
		double weight = sureness / Math.max(sureness + confidence * (1 - sureness), 1e-6);
		position = position == Vec3.ZERO || confidence < 0.02 ? at : position.lerp(at, weight);
		if (moving != null) {
			velocity = moving;
		} else if (sense != Sense.SIGHT) {
			velocity = velocity.scale(0.5);
		}
		uncertainty = Math.min(uncertainty * (1 - weight) + accuracy * weight, accuracy + uncertainty * 0.5);
		confidence = Math.min(1, Math.max(confidence, sureness) + 0.1 * sureness * (1 - confidence));
		lastSensed = time;
		lastSense = sense;
		if (sense == Sense.SIGHT) {
			seen = true;
			lastSeen = time;
			seenAt = at;
			seenVelocity = moving != null ? moving : Vec3.ZERO;
		}
	}

	/** One tick with nothing new: the guess moves on, slows down, spreads out and fades. */
	public void tick() {
		double dt = 0.05;
		if (!seen) {
			position = position.add(velocity.scale(dt));
			velocity = velocity.scale(Math.pow(0.6, dt));
			uncertainty = Math.min(MAX_SPREAD, uncertainty + SPREAD * dt);
			confidence *= Math.pow(0.5, dt / HALF_LIFE);
		}
		seen = false;
	}

	public double secondsSince(long now) {
		return lastSensed == Long.MIN_VALUE ? Double.MAX_VALUE : (now - lastSensed) / 20.0;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("prey", prey);
		tag.putDouble("x", position.x);
		tag.putDouble("y", position.y);
		tag.putDouble("z", position.z);
		tag.putDouble("confidence", confidence);
		tag.putDouble("uncertainty", uncertainty);
		tag.putLong("lastSensed", lastSensed);
		tag.putString("sense", lastSense.name());
		return tag;
	}

	public static Belief load(CompoundTag tag) {
		Belief b = new Belief(tag.getUUID("prey"));
		b.position = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
		b.confidence = tag.getDouble("confidence");
		b.uncertainty = tag.getDouble("uncertainty");
		b.lastSensed = tag.getLong("lastSensed");
		try {
			b.lastSense = Sense.valueOf(tag.getString("sense"));
		} catch (IllegalArgumentException ignored) {
			b.lastSense = Sense.HEARING;
		}
		return b;
	}
}
