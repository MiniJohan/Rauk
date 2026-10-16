package rauk.com.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.combat.RaukCombat;
import rauk.com.entity.hunt.RaukBrain;
import rauk.com.entity.mind.RaukHearing;
import rauk.com.entity.mind.RaukMind;
import rauk.com.entity.move.RaukMover;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.sound.RaukSounds;

/**
 * Rauk. The server owns where it is and what state it is in; the client turns that state into
 * the full procedural body (design doc 5.11).
 *
 * <p>The synced state: posture (which also sets the collision box), the surface it holds on to
 * (floor, a wall or the ceiling), the leap phase, Tension (0 resting, 0.5 alert, 1 committed),
 * Attention (0 scanning, 1 locked on), the entity it is looking at, whether it has risen to its
 * full height to observe, the last thing that startled it, the last thing it noticed (the head
 * snaps there, the body stays), a point it is looking at, and what its hands are doing (tapping,
 * listening). Its senses and memory are in {@link RaukMind}.
 */
public class RaukEntity extends PathfinderMob {
	private static final EntityDataAccessor<Byte> POSTURE = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> SURFACE = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> LEAP = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> TENSION = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ATTENTION = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> GAZE = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RISEN = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BOOLEAN);
	/** Counts up with every startle, so the client sees each one even if two come from the same place. */
	private static final EntityDataAccessor<Integer> STARTLES = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> STARTLE_AT = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> NOTICES = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> NOTICE_AT = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3);
	/** A point to look at when there's no one to watch; NaN for none. */
	private static final EntityDataAccessor<Vector3f> LOOK_AT = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Vector3f> ACTION_AT = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3);
	/** Its gallop burst is spent: mouth open, ribs heaving, no faster than a stride for a while. */
	private static final EntityDataAccessor<Boolean> PANTING = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BOOLEAN);
	/** What it holds (entity id, -1 for nothing) and the grab's phase (lift, struggle, bite, recoil). */
	private static final EntityDataAccessor<Integer> GRABBED = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> GRAB_PHASE = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BYTE);
	/** The tightest posture on the next few blocks of its path, so the body can lower before it gets there. */
	private static final EntityDataAccessor<Byte> POSTURE_AHEAD = SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.BYTE);
	/** What it killed and carries in its jaws (entity type, and the player if it was one); empty for nothing. */
	private static final EntityDataAccessor<CompoundTag> CARCASS = SynchedEntityData.defineId(RaukEntity.class,
			EntityDataSerializers.COMPOUND_TAG);
	/**
	 * Where its path goes next: points one to five blocks on along it (NaN past its end or where it
	 * changes surface), so the body can follow the way the head is going.
	 */
	private static final List<EntityDataAccessor<Vector3f>> AHEAD = List.of(
			SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3),
			SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3),
			SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3),
			SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3),
			SynchedEntityData.defineId(RaukEntity.class, EntityDataSerializers.VECTOR3));
	private static final Vector3f NOWHERE = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
	/** Set by the client: plays the sounds and effects of the entity events in {@link RaukSounds}. */
	public static java.util.function.BiConsumer<RaukEntity, Byte> clientEvents = (e, id) -> {
	};

	/** The client's procedural body for this entity; only ever set on the client. */
	public Object clientBody;

	private final RaukMover mover = new RaukMover(this);
	private final RaukMind mind = new RaukMind(this);
	private final RaukCombat combat = new RaukCombat(this);
	private final DynamicGameEventListener<RaukHearing> hearing = new DynamicGameEventListener<>(new RaukHearing(this));
	/** On the floor it stands upright or goes on all fours, as its mind prefers, when there is room. */
	private RaukPosture floorStyle = RaukPosture.UPRIGHT;
	private boolean brainOn = true;
	/** Set in registerGoals, which runs from the superclass constructor before field initializers. */
	private RaukBrain brain;
	/** The gallop is an anaerobic burst (design doc 4.2): about five seconds, then eight to recover. */
	private float stamina = 1;
	private int recovering;
	@Nullable
	private BlockPos home;

	public RaukEntity(EntityType<? extends RaukEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 360)
				.add(Attributes.ARMOR, 8)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 12)
				.add(Attributes.FOLLOW_RANGE, 48)
				.add(Attributes.SAFE_FALL_DISTANCE, 24)
				.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(POSTURE, (byte) RaukPosture.UPRIGHT.ordinal());
		builder.define(SURFACE, (byte) Direction.DOWN.ordinal());
		builder.define(LEAP, (byte) RaukMover.LEAP_NONE);
		builder.define(TENSION, 0.2f);
		builder.define(ATTENTION, 0f);
		builder.define(GAZE, -1);
		builder.define(RISEN, false);
		builder.define(STARTLES, 0);
		builder.define(STARTLE_AT, new Vector3f());
		builder.define(NOTICES, 0);
		builder.define(NOTICE_AT, new Vector3f());
		builder.define(LOOK_AT, new Vector3f(Float.NaN, Float.NaN, Float.NaN));
		builder.define(ACTION, (byte) RaukMind.ACTION_NONE);
		builder.define(ACTION_AT, new Vector3f());
		builder.define(PANTING, false);
		builder.define(GRABBED, -1);
		builder.define(GRAB_PHASE, (byte) 0);
		builder.define(POSTURE_AHEAD, (byte) RaukPosture.UPRIGHT.ordinal());
		builder.define(CARCASS, new CompoundTag());
		for (EntityDataAccessor<Vector3f> a : AHEAD) {
			builder.define(a, NOWHERE);
		}
	}

	/** No swimming: it sinks (design doc 4.5), and walks the bottom. */
	@Override
	protected void registerGoals() {
		brain = new RaukBrain(this);
		goalSelector.addGoal(1, brain);
	}

	/** Dense and heavy: it doesn't float, and water barely slows it. */
	@Override
	protected float getWaterSlowDown() {
		return 0.9f;
	}

	@Override
	public void aiStep() {
		Vec3 before = position();
		if (level() instanceof ServerLevel server && isAlive()) {
			if (home == null) {
				home = blockPosition();
				mind.territory.survey(server, home, 40, server.getGameTime());
			}
			mind.tick(server);
			combat.tick(server);
			mover.tick();
			if (tickCount % 5 == 0) {
				clearUndead(server);
			}
		}
		super.aiStep();
		if (!level().isClientSide) {
			tickStamina(position().subtract(before).horizontalDistance() * 20);
		}
	}

	private void tickStamina(double speed) {
		if (recovering > 0) {
			stamina = Math.min(1, stamina + 1 / 160f);
			if (--recovering == 0) {
				entityData.set(PANTING, false);
			}
			return;
		}
		if (speed > 6.5) {
			stamina -= 1 / 100f;
			if (stamina <= 0) {
				stamina = 0;
				recovering = 160;
				entityData.set(PANTING, true);
			}
		} else {
			stamina = Math.min(1, stamina + 1 / 300f);
		}
	}

	public float stamina() {
		return stamina;
	}

	public boolean panting() {
		return entityData.get(PANTING);
	}

	/** The fastest it can go right now, blocks per second: while recovering, no faster than a stride. */
	public double speedCap() {
		return recovering > 0 ? 3.5 : 20;
	}

	/** Its den, where it rests: where it first found itself, unless moved. */
	public BlockPos home() {
		return home == null ? blockPosition() : home;
	}

	public void setHome(BlockPos home) {
		this.home = home.immutable();
	}

	public RaukBrain brain() {
		return brain;
	}

	public RaukCombat combat() {
		return combat;
	}

	/**
	 * The undead aren't food (design doc 6.7): it ignores them, but one that gets in its way, or
	 * goes for it, it kills without a sound.
	 */
	private void clearUndead(ServerLevel level) {
		if (combat.holding()) {
			return;
		}
		Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
		AABB ahead = getBoundingBox().inflate(0.6).expandTowards(fwd.scale(mover.isMoving() ? 1.6 : 0.4));
		for (net.minecraft.world.entity.monster.Monster undead : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
				ahead, m -> m.isAlive() && m.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD))) {
			boolean inWay = mover.isMoving() || undead.getTarget() == this || undead.getLastHurtMob() == this;
			if (!inWay) {
				continue;
			}
			setAction(RaukMind.ACTION_LUNGE, undead.getEyePosition());
			undead.setSilent(true);
			undead.hurt(damageSources().mobAttack(this), Float.MAX_VALUE);
			RaukSounds.event(this, RaukSounds.EV_KILL_SILENT);
			return;
		}
	}

	/** Where it holds what it grabs: up in front of its face. The held creature's eyes go here. */
	public Vec3 holdPoint() {
		Vec3 eye = mind.eye();
		Vec3 fwd = Vec3.directionFromRotation(0, getYHeadRot());
		// Close under its face, within a bent arm's reach of the shoulder.
		Vec3 want = eye.add(fwd.scale(0.75)).add(0, -0.3, 0);
		if (surface() == Direction.DOWN) {
			// Lifted well off the ground, up to its head, as high as the room allows.
			double lift = Math.max(want.y, getY() + 3.0);
			var up = level().clip(new ClipContext(want, new Vec3(want.x, lift + 0.6, want.z), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, this));
			want = new Vec3(want.x, up.getType() == HitResult.Type.MISS ? lift : Math.max(want.y, up.getLocation().y - 0.6), want.z);
		}
		var hit = level().clip(new ClipContext(eye, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS ? want : hit.getLocation().subtract(want.subtract(eye).normalize().scale(0.4));
	}

	/** Killed by a command or the void (nothing else can): what it holds goes free, a kill is dropped. */
	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel server) {
			combat.died();
			brain.dropKill(server);
		}
		super.die(source);
	}

	/** Its ears: registered with the level's game event system the way the Warden's are. */
	@Override
	public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> consumer) {
		if (level() instanceof ServerLevel server) {
			consumer.accept(hearing, server);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.put("RaukMind", mind.save());
		tag.put("RaukBrain", brain.save());
		tag.putFloat("RaukStamina", stamina);
		if (home != null) {
			tag.putLong("RaukHome", home.asLong());
		}
		tag.put("RaukCarcass", carcass());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("RaukMind")) {
			mind.load(tag.getCompound("RaukMind"));
		}
		if (tag.contains("RaukBrain")) {
			brain.load(tag.getCompound("RaukBrain"));
		}
		if (tag.contains("RaukStamina")) {
			stamina = tag.getFloat("RaukStamina");
		}
		if (tag.contains("RaukHome")) {
			home = BlockPos.of(tag.getLong("RaukHome"));
		}
		if (tag.contains("RaukCarcass")) {
			entityData.set(CARCASS, tag.getCompound("RaukCarcass"));
		}
	}

	public RaukMind mind() {
		return mind;
	}

	/** Holding a wall or ceiling it moves exactly as the mover says, with no gravity and a firm grip. */
	@Override
	public void travel(Vec3 input) {
		if (surface() != Direction.DOWN || mover.isLeaping()) {
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.5));
			return;
		}
		super.travel(input);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return posture().dimensions();
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (POSTURE.equals(accessor)) {
			refreshDimensions();
		}
	}

	public RaukMover mover() {
		return mover;
	}

	/** Whether the posture's box would fit where it is now (doors it can open count as clear). */
	public boolean fits(RaukPosture posture) {
		return new RaukPathfinder(level()).fits(posture.dimensions().makeBoundingBox(positionFor(posture)).deflate(0.02));
	}

	/**
	 * Where it would be in a new posture: the box keeps its face against whatever it holds, so on
	 * a ceiling it changes from the top and on a wall from the wall side.
	 */
	private Vec3 positionFor(RaukPosture posture) {
		RaukPosture old = posture();
		Direction surface = surface();
		if (surface == Direction.UP) {
			return position().add(0, old.height - posture.height, 0);
		}
		if (surface != Direction.DOWN) {
			double shift = (old.width - posture.width) / 2;
			return position().add(surface.getStepX() * shift, 0, surface.getStepZ() * shift);
		}
		return position();
	}

	// --- synced state ---------------------------------------------------------------------------

	public RaukPosture posture() {
		return RaukPosture.byId(entityData.get(POSTURE));
	}

	/** Takes a new posture and its box, keeping hold of whatever it is holding. */
	public void setPosture(RaukPosture posture) {
		if (posture() == posture) {
			return;
		}
		Vec3 at = positionFor(posture);
		setPos(at.x, at.y, at.z);
		entityData.set(POSTURE, (byte) posture.ordinal());
		refreshDimensions();
	}

	public Direction surface() {
		return Direction.from3DDataValue(entityData.get(SURFACE));
	}

	public void setSurface(Direction surface) {
		entityData.set(SURFACE, (byte) surface.get3DDataValue());
	}

	public int leapState() {
		return entityData.get(LEAP);
	}

	public void setLeapState(int state) {
		entityData.set(LEAP, (byte) state);
	}

	public RaukPosture floorStyle() {
		return floorStyle;
	}

	/**
	 * Upright or on all fours on the floor. Only the preference: the mover takes it whenever there
	 * is room, and squeezes or crawls where there isn't.
	 */
	public void setFloorStyle(RaukPosture style) {
		floorStyle = style == RaukPosture.UPRIGHT ? RaukPosture.UPRIGHT : RaukPosture.QUADRUPED;
	}

	public float tension() {
		return entityData.get(TENSION);
	}

	public void setTension(float value) {
		entityData.set(TENSION, Mth.clamp(value, 0, 1));
	}

	public float attention() {
		return entityData.get(ATTENTION);
	}

	public void setAttention(float value) {
		entityData.set(ATTENTION, Mth.clamp(value, 0, 1));
	}

	@Nullable
	public Entity gazeTarget() {
		int id = entityData.get(GAZE);
		return id < 0 ? null : level().getEntity(id);
	}

	public void setGazeTarget(@Nullable Entity target) {
		entityData.set(GAZE, target == null ? -1 : target.getId());
	}

	public boolean risen() {
		return entityData.get(RISEN);
	}

	/** Rises slowly to its full height to observe (only when standing upright and still), or lowers itself again. */
	public void setRisen(boolean risen) {
		entityData.set(RISEN, risen);
	}

	public int startleCount() {
		return entityData.get(STARTLES);
	}

	public Vec3 startlePoint() {
		Vector3f v = entityData.get(STARTLE_AT);
		return new Vec3(v.x(), v.y(), v.z());
	}

	/** Something at this point startled it: the head snaps there, the body follows. */
	public void startle(Vec3 at) {
		entityData.set(STARTLE_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
		entityData.set(STARTLES, entityData.get(STARTLES) + 1);
		getLookControl().setLookAt(at.x, at.y, at.z, 90, 90);
	}

	public int noticeCount() {
		return entityData.get(NOTICES);
	}

	public Vec3 noticePoint() {
		Vector3f v = entityData.get(NOTICE_AT);
		return new Vec3(v.x(), v.y(), v.z());
	}

	/** Something caught its attention here: the head snaps to it; the body doesn't move. */
	public void notice(Vec3 at) {
		entityData.set(NOTICE_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
		entityData.set(NOTICES, entityData.get(NOTICES) + 1);
		getLookControl().setLookAt(at.x, at.y, at.z, 90, 90);
	}

	@Nullable
	public Vec3 lookPoint() {
		Vector3f v = entityData.get(LOOK_AT);
		return Float.isNaN(v.x()) ? null : new Vec3(v.x(), v.y(), v.z());
	}

	/** A place to keep looking at while there's no one to watch: where it heard something, a door. */
	public void setLookPoint(@Nullable Vec3 at) {
		entityData.set(LOOK_AT, at == null ? new Vector3f(Float.NaN, Float.NaN, Float.NaN)
				: new Vector3f((float) at.x, (float) at.y, (float) at.z));
		if (at != null) {
			getLookControl().setLookAt(at.x, at.y, at.z, 30, 30);
		}
	}

	public int action() {
		return entityData.get(ACTION);
	}

	public Vec3 actionPoint() {
		Vector3f v = entityData.get(ACTION_AT);
		return new Vec3(v.x(), v.y(), v.z());
	}

	public void setAction(int action, Vec3 at) {
		entityData.set(ACTION_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
		entityData.set(ACTION, (byte) action);
	}

	/**
	 * Nothing hurts it (decided in review, 2026-10-06): not blades, arrows, fire, falls or drowning.
	 * A blow still lands: it startles, it turns on whoever struck it, and a blow to the face while it
	 * holds someone still loosens its grip. Only a command or the void can remove it.
	 */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (level().isClientSide || isDeadOrDying()) {
			return false;
		}
		// It takes no harm, and whoever it holds it kills whatever they do: a blow it doesn't even
		// look round at then. Otherwise it turns on whoever struck it.
		Entity attacker = source.getEntity();
		if (attacker != null && !combat.holding()) {
			startle(attacker.getEyePosition());
		}
		brain.struck(source);
		return false;
	}

	/** Fire doesn't take hold on it. */
	@Override
	public boolean fireImmune() {
		return true;
	}

	/** Sounds are played by the client, through walls muffled (see {@link RaukSounds}). */
	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return null;
	}

	/** Its feet are the animation's business: clicks, creaks and pounding by what it walks on. */
	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
	}

	@Override
	protected void playBlockFallSound() {
	}

	/** Landed from a leap or a fall: from more than four blocks up, the floor shakes for anyone near. */
	public void landedFrom(double drop) {
		RaukSounds.event(this, drop > 4 ? RaukSounds.EV_LAND_HEAVY : RaukSounds.EV_LAND);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		if (distance > 1.5f && !level().isClientSide) {
			landedFrom(distance);
		}
		return super.causeFallDamage(distance, multiplier, source);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id >= 100) {
			clientEvents.accept(this, id);
			return;
		}
		super.handleEntityEvent(id);
	}

	// --- what it carries --------------------------------------------------------------------------

	/** What it carries in its jaws: empty when nothing. */
	public CompoundTag carcass() {
		return entityData.get(CARCASS);
	}

	public boolean carrying() {
		return !entityData.get(CARCASS).isEmpty();
	}

	public void setCarcass(@Nullable CompoundTag tag) {
		entityData.set(CARCASS, tag == null ? new CompoundTag() : tag);
	}

	public RaukPosture postureAhead() {
		return RaukPosture.byId(entityData.get(POSTURE_AHEAD));
	}

	public void setPostureAhead(RaukPosture posture) {
		entityData.set(POSTURE_AHEAD, (byte) posture.ordinal());
	}

	/** The points along its path ahead, nearest first (only those there are). */
	public List<Vec3> pathAhead() {
		List<Vec3> out = new ArrayList<>(AHEAD.size());
		for (EntityDataAccessor<Vector3f> a : AHEAD) {
			Vector3f v = entityData.get(a);
			if (Float.isNaN(v.x)) {
				break;
			}
			out.add(new Vec3(v.x, v.y, v.z));
		}
		return out;
	}

	/** Sets the path ahead; a point is only sent again once it has moved a little. */
	public void setPathAhead(List<Vec3> points) {
		for (int i = 0; i < AHEAD.size(); i++) {
			Vector3f now = entityData.get(AHEAD.get(i));
			if (i >= points.size()) {
				if (!Float.isNaN(now.x)) {
					entityData.set(AHEAD.get(i), NOWHERE);
				}
				continue;
			}
			Vec3 p = points.get(i);
			if (Float.isNaN(now.x) || p.distanceToSqr(now.x, now.y, now.z) > 0.03 * 0.03) {
				entityData.set(AHEAD.get(i), new Vector3f((float) p.x, (float) p.y, (float) p.z));
			}
		}
	}

	@Nullable
	public Entity grabbed() {
		int id = entityData.get(GRABBED);
		return id < 0 ? null : level().getEntity(id);
	}

	public void setGrabbed(@Nullable Entity e) {
		entityData.set(GRABBED, e == null ? -1 : e.getId());
	}

	public int grabPhase() {
		return entityData.get(GRAB_PHASE);
	}

	public void setGrabPhase(int phase) {
		entityData.set(GRAB_PHASE, (byte) phase);
	}

	// --- test controls --------------------------------------------------------------------------

	public void walkTo(Vec3 target, double blocksPerSecond) {
		mover.moveTo(target, null, blocksPerSecond);
	}

	/** Goes to a place and takes hold of the given surface there: a wall or the ceiling. */
	public void climbTo(Vec3 target, Direction surface, double blocksPerSecond) {
		mover.moveTo(target, surface, blocksPerSecond);
	}

	public void stopWalking() {
		mover.stop();
	}

	public boolean isWalkingToTarget() {
		return mover.isMoving();
	}

	/** Runs at the target on all fours until within stopAt blocks of it. */
	public void chase(Entity target, double blocksPerSecond, double stopAt) {
		setFloorStyle(RaukPosture.QUADRUPED);
		setRisen(false);
		setGazeTarget(target);
		setAttention(1);
		mover.follow(target, blocksPerSecond, stopAt);
	}

	public boolean brainEnabled() {
		return brainOn;
	}

	public void setBrainEnabled(boolean enabled) {
		brainOn = enabled;
	}

	/** The neck reaches about four blocks forward and the tail runs past the feet, well outside the hitbox. */
	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().inflate(5, 5, 5);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}
}
