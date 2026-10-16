package rauk.com.entity.combat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.block.BloodBlock;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.mind.Prey;
import rauk.com.entity.mind.RaukMind;
import rauk.com.sound.RaukSounds;

/**
 * Rauk's side of a fight (design doc 7): its attacks, and the grab and kill. Nothing hurts it
 * (decided in review, 2026-10-06), and nothing makes it let go: whoever it catches dies, however
 * they hit it (decided in review, 2026-10-14).
 *
 * <ul>
 * <li><b>Pounce / ceiling drop</b>: lands on the prey and pins it, then grabs.</li>
 * <li><b>Faced</b>: armed prey that faces it and is still strong isn't attacked head on; the brain
 * circles round to its side or back and grabs from there.</li>
 * <li><b>Shield hook</b>: hooks a raised shield away and disables it, like an axe.</li>
 * <li><b>Grab</b>: lifts the prey up to its face, the jaw opens fully and it bites: no armor and
 * no totem helps.</li>
 * </ul>
 */
public final class RaukCombat {
	public static final int PHASE_NONE = 0, PHASE_LIFT = 1, PHASE_STRUGGLE = 2, PHASE_BITE = 3, PHASE_RECOIL = 4;

	private static final int LIFT_TICKS = 10, BITE_TICKS = 16, RECOIL_TICKS = 20;
	private static final float POUNCE_DAMAGE = 8;
	private static final org.joml.Vector3f BLOOD_RED = new org.joml.Vector3f(0.32f, 0.02f, 0.03f);

	private final RaukEntity rauk;

	@Nullable
	private LivingEntity held;
	private int phase = PHASE_NONE, phaseTick;
	private Vec3 liftFrom = Vec3.ZERO;
	/** How the last grab ended: true if it killed, false if the prey got away. */
	private boolean lastKilled;
	private int grabsEnded;

	private long attackReady;
	private int pinTicks;
	@Nullable
	private LivingEntity pinned;
	/** Where it last let a drop of blood fall, for the drip trail. */
	private Vec3 lastDrip = Vec3.ZERO;

	public RaukCombat(RaukEntity rauk) {
		this.rauk = rauk;
	}

	private long now() {
		return rauk.level().getGameTime();
	}

	// --- every tick -----------------------------------------------------------------------------

	public void tick(ServerLevel level) {
		long now = now();
		if (held != null) {
			tickGrab(level, now);
		} else if (phase == PHASE_RECOIL && ++phaseTick >= RECOIL_TICKS) {
			setPhase(PHASE_NONE);
		}
		if (pinned != null && --pinTicks <= 0) {
			LivingEntity p = pinned;
			pinned = null;
			if (held == null && p.isAlive() && reach(p)) {
				grab(p);
			}
		}
		// Soaked in other things' blood, it drips: now and then a drop falls from its jaws, a faint
		// trail across the floor behind it.
		Vec3 c = rauk.position();
		if (rauk.surface() == Direction.DOWN && c.distanceTo(lastDrip) > 8) {
			lastDrip = c;
			if (rauk.getRandom().nextBoolean()) {
				Vec3 at = rauk.mind().eye().add((rauk.getRandom().nextDouble() - 0.5) * 0.6, 0, (rauk.getRandom().nextDouble() - 0.5) * 0.6);
				BloodBlock.stain(level, new Vec3(at.x, Math.floor(rauk.getY()) + 0.5, at.z), BloodBlock.Shape.DROPS,
						Direction.Plane.HORIZONTAL.getRandomDirection(rauk.getRandom()));
			}
		}
	}

	// --- attacks --------------------------------------------------------------------------------

	public enum Outcome { NONE, GRABBED, FACED, HOOKED, PINNED }

	/**
	 * Whether the prey is within reach of its hands: close to its face, or right up against its
	 * body, with room for an arm to get to it (no grabbing round a door frame or through the steps
	 * of a stair).
	 */
	public boolean reach(LivingEntity prey) {
		Vec3 eye = rauk.mind().eye();
		AABB box = prey.getBoundingBox();
		boolean close = nearest(box, eye).distanceTo(eye) < 2.4 || rauk.getBoundingBox().inflate(0.8, 0.2, 0.8).intersects(box);
		return close && armRoom(prey);
	}

	/**
	 * The long reach of one arm into a vent, a window or a gap, or up over a railing: up to about
	 * three and a half blocks from its eyes to the nearest part of the prey, through an opening
	 * the arm fits through.
	 */
	public boolean reachIn(LivingEntity prey) {
		Vec3 eye = rauk.mind().eye();
		return nearest(prey.getBoundingBox(), eye).distanceTo(eye) < 3.5 && armRoom(prey);
	}

	private static Vec3 nearest(AABB box, Vec3 to) {
		return new Vec3(Mth.clamp(to.x, box.minX, box.maxX), Mth.clamp(to.y, box.minY, box.maxY), Mth.clamp(to.z, box.minZ, box.maxZ));
	}

	/**
	 * Room for an arm from its head to the prey's middle or head: the line itself clear, and lines
	 * to either side of the prey a hand's width apart, so a crack it could see through isn't enough.
	 */
	private boolean armRoom(LivingEntity prey) {
		Vec3 eye = rauk.mind().eye();
		for (Vec3 to : new Vec3[] {prey.getBoundingBox().getCenter(), prey.getEyePosition()}) {
			Vec3 along = to.subtract(eye);
			if (along.lengthSqr() < 1e-4) {
				return true;
			}
			Vec3 dir = along.normalize();
			Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
			Vec3 up = side.cross(dir).normalize();
			boolean clear = true;
			for (Vec3 off : new Vec3[] {Vec3.ZERO, side.scale(0.25), side.scale(-0.25), up.scale(0.25), up.scale(-0.25)}) {
				var hit = rauk.level().clip(new ClipContext(eye, to.add(off), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk));
				if (hit.getType() != HitResult.Type.MISS) {
					clear = false;
					break;
				}
			}
			if (clear) {
				return true;
			}
		}
		return false;
	}

	/**
	 * It has reached its prey: hooks a raised shield away; armed prey that faces it and is still
	 * strong it won't take head on (the brain circles round); anything else it grabs.
	 */
	public Outcome engage(LivingEntity prey, boolean facingArmed) {
		long now = now();
		if (now < attackReady || held != null || !reach(prey)) {
			return Outcome.NONE;
		}
		if (prey instanceof Player p && p.isBlocking()) {
			attackReady = now + 14;
			rauk.setAction(RaukMind.ACTION_HOOK, prey.getBoundingBox().getCenter());
			p.disableShield();
			rauk.level().playSound(null, prey.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1, 0.7f);
			RaukSounds.event(rauk, RaukSounds.EV_HOOK);
			return Outcome.HOOKED;
		}
		if (facingArmed && prey.getHealth() > prey.getMaxHealth() * 0.4f) {
			return Outcome.FACED;
		}
		grab(prey);
		return Outcome.GRABBED;
	}

	/** A pounce that killed: as good as a kill from the grab. */
	private void killedOutright(LivingEntity prey) {
		if (!prey.isAlive()) {
			lastKilled = true;
			grabsEnded++;
			pinned = null;
			takeCarcass(prey);
		}
	}

	/**
	 * Whatever it kills it keeps: the body is in its jaws from now on (drawn by the client), to be
	 * dragged to a larder and fed on there.
	 */
	private void takeCarcass(LivingEntity prey) {
		CompoundTag tag = new CompoundTag();
		tag.putString("type", net.minecraft.world.entity.EntityType.getKey(prey.getType()).toString());
		if (prey instanceof Player p) {
			tag.putUUID("player", p.getUUID());
			tag.putString("name", p.getGameProfile().getName());
		} else {
			// The body it carries is the one you see from now on.
			prey.remove(Entity.RemovalReason.KILLED);
		}
		rauk.setCarcass(tag);
	}

	/** Landed on the prey from a leap or a ceiling drop: it is pinned, then grabbed. */
	public void pin(LivingEntity prey) {
		lastKilled = false;
		if (prey.hurt(rauk.damageSources().mobAttack(rauk), POUNCE_DAMAGE)) {
			prey.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 10, false, false));
		}
		prey.setDeltaMovement(Vec3.ZERO);
		prey.hurtMarked = true;
		pinned = prey;
		pinTicks = 8;
		attackReady = now() + 8;
		RaukSounds.event(rauk, RaukSounds.EV_PIN);
		killedOutright(prey);
	}

	public boolean pinning() {
		return pinned != null;
	}

	// --- grab, struggle, kill -------------------------------------------------------------------

	public void grab(LivingEntity prey) {
		held = prey;
		liftFrom = prey.position();
		rauk.setGrabbed(prey);
		setPhase(PHASE_LIFT);
		rauk.setGazeTarget(prey);
		rauk.setAttention(1);
		rauk.setTension(1);
		rauk.stopWalking();
		RaukSounds.event(rauk, RaukSounds.EV_GRAB);
	}

	public boolean holding() {
		return held != null;
	}

	@Nullable
	public LivingEntity held() {
		return held;
	}

	public int phase() {
		return phase;
	}

	/** How the last grab ended (true if it killed), and how many have ended so far. */
	public boolean lastKilled() {
		return lastKilled;
	}

	public int grabsEnded() {
		return grabsEnded;
	}

	private void setPhase(int phase) {
		this.phase = phase;
		phaseTick = 0;
		rauk.setGrabPhase(phase);
	}

	private void tickGrab(ServerLevel level, long now) {
		LivingEntity prey = held;
		phaseTick++;
		if (!prey.isAlive() || prey.isRemoved() || prey.level() != rauk.level() || !rauk.isAlive()
				|| (prey instanceof Player p && (p.isSpectator() || !Prey.is(p) && !Prey.huntCreative))) {
			// Dead in its grip is a kill, whether the bite or the grip did it.
			release(!prey.isAlive());
			return;
		}
		// Held up at its face.
		Vec3 hold = rauk.holdPoint();
		Vec3 feet = hold.subtract(0, prey.getEyeHeight(), 0);
		double lift = phase == PHASE_LIFT ? Mth.clamp(phaseTick / (double) LIFT_TICKS, 0, 1) : 1;
		Vec3 at = liftFrom.lerp(feet, lift * lift * (3 - 2 * lift));
		prey.resetFallDistance();
		prey.setDeltaMovement(Vec3.ZERO);
		if (prey instanceof ServerPlayer player) {
			// The client keeps itself there; this only catches it if it doesn't.
			if (player.position().distanceTo(at) > 2.5) {
				player.teleportTo(at.x, at.y, at.z);
			}
		} else {
			prey.setPos(at.x, at.y, at.z);
		}
		rauk.setGazeTarget(prey);
		rauk.getLookControl().setLookAt(prey, 90, 90);

		switch (phase) {
			case PHASE_LIFT -> {
				// Up at its face, and straight into the bite.
				if (phaseTick >= LIFT_TICKS) {
					setPhase(PHASE_BITE);
				}
			}
			case PHASE_BITE -> {
				if (phaseTick == BITE_TICKS - 4) {
					bite(level, prey);
				}
				if (phaseTick >= BITE_TICKS) {
					release(true);
				}
			}
			default -> {
			}
		}
	}

	private void bite(ServerLevel level, LivingEntity prey) {
		Vec3 head = prey.getEyePosition();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), head.x, head.y,
				head.z, 40, 0.25, 0.25, 0.25, 0.3);
		level.sendParticles(new DustParticleOptions(BLOOD_RED, 2.2f), head.x, head.y, head.z, 30, 0.3, 0.3, 0.3, 0.1);
		// It runs down onto the floor below.
		BloodBlock.stain(level, head, BloodBlock.Shape.POOL, rauk.getDirection());
		RaukSounds.event(rauk, RaukSounds.EV_BITE);
		if (prey instanceof Player p && p.isCreative()) {
			// Testing in creative: the bite is shown, nobody dies.
			return;
		}
		prey.hurt(RaukDamage.of(rauk, RaukDamage.BITE), Float.MAX_VALUE);
	}

	private void release(boolean killed) {
		LivingEntity prey = held;
		held = null;
		lastKilled = killed;
		grabsEnded++;
		rauk.setGrabbed(null);
		if (killed && prey != null && !prey.isAlive()) {
			takeCarcass(prey);
		}
		if (phase != PHASE_RECOIL) {
			setPhase(PHASE_NONE);
		}
		if (prey != null) {
			prey.resetFallDistance();
		}
	}

	// --- being hit ------------------------------------------------------------------------------

	/** A rough position of each region, worked out on the server from its posture and facing. */
	public Vec3 regionCenter(Region r) {
		Vec3 center = rauk.getBoundingBox().getCenter();
		Vec3 fwd = Vec3.directionFromRotation(0, rauk.yBodyRot);
		Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
		double h = rauk.getBbHeight();
		return switch (r) {
			case HEAD -> rauk.mind().eye();
			case TORSO -> center;
			case ARM_L -> center.add(fwd.scale(0.7)).add(side.scale(0.6)).add(0, -h * 0.1, 0);
			case ARM_R -> center.add(fwd.scale(0.7)).add(side.scale(-0.6)).add(0, -h * 0.1, 0);
			case LEG_L -> rauk.position().add(fwd.scale(-0.3)).add(side.scale(0.45)).add(0, Math.min(1.2, h * 0.3), 0);
			case LEG_R -> rauk.position().add(fwd.scale(-0.3)).add(side.scale(-0.45)).add(0, Math.min(1.2, h * 0.3), 0);
			case TAIL -> rauk.position().add(fwd.scale(-2.2)).add(0, 0.4, 0);
		};
	}

	/** It was killed by a command or fell out of the world: whatever it held goes free. */
	public void died() {
		if (held != null) {
			release(false);
		}
		pinned = null;
	}
}
