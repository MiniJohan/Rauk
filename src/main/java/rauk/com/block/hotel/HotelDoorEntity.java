package rauk.com.block.hotel;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import rauk.com.sound.RaukSounds;

/**
 * A hotel door's swing (its lower half keeps it): how far open the panel is, how far it's going
 * and how fast. It gathers speed as it starts and slows as it arrives, unless it's flung, when it
 * bangs against the wall and bounces back an inch. The server moves it and tells the client where
 * it's going; the client moves its own copy the same way, frame by frame, to draw it. Past halfway
 * the door counts as open.
 *
 * <p>How it sounds is how it was swung: a careful hand or a slow claw gets a long creak, quiet
 * enough not to carry far; a door opened as doors are opened sounds like one, and Rauk hears it
 * as such; a flung door bangs.
 */
public class HotelDoorEntity extends BlockEntity {
	private float angle, prev, target, speed = HotelDoorBlock.NORMAL, velocity;
	private boolean synced;
	private int creak;

	public HotelDoorEntity(BlockPos pos, BlockState state) {
		super(HotelDoorBlock.entityType, pos, state);
		angle = prev = target = state.getValue(DoorBlock.OPEN) ? HotelDoorBlock.FULL : 0;
	}

	public float angle(float partial) {
		return Mth.lerp(partial, prev, angle);
	}

	public float target() {
		return target;
	}

	/** Starts the door swinging to an angle at a speed (degrees a tick), with the sound of how it's done. */
	public void swing(float to, float how, @Nullable Entity who) {
		if (to == target && how == speed) {
			return;
		}
		boolean opening = to > angle;
		target = to;
		speed = how;
		velocity = Math.min(velocity, how * 0.3f);
		Level level = getLevel();
		if (level != null && !level.isClientSide) {
			DoorBlock door = (DoorBlock) getBlockState().getBlock();
			if (how >= HotelDoorBlock.SLAM) {
				level.playSound(null, worldPosition, door.type().doorOpen(), SoundSource.BLOCKS, 1.2f, 0.65f);
				level.gameEvent(who, opening ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, worldPosition);
			} else if (how >= HotelDoorBlock.NORMAL) {
				if (opening) {
					level.playSound(null, worldPosition, door.type().doorOpen(), SoundSource.BLOCKS, 1, 0.9f + level.random.nextFloat() * 0.1f);
				}
				level.gameEvent(who, opening ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, worldPosition);
			} else {
				// Slow: a creak, heard only close by.
				level.gameEvent(who, GameEvent.BLOCK_CHANGE, worldPosition);
				creak = 0;
			}
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, HotelDoorEntity door) {
		door.prev = door.angle;
		float d = door.target - door.angle;
		if (Math.abs(d) < 0.001f) {
			door.velocity = 0;
			return;
		}
		// Gathers speed as it starts; slows as it arrives, unless it was flung.
		door.velocity = Math.min(door.speed, door.velocity + door.speed * 0.3f + 0.05f);
		float step = door.velocity;
		if (door.speed < HotelDoorBlock.SLAM) {
			step = Math.min(step, Math.max(Math.abs(d) * 0.35f, Math.min(0.35f, door.speed)));
		}
		boolean arrives = step >= Math.abs(d);
		door.angle = arrives ? door.target : door.angle + Math.signum(d) * step;
		// Flung back against the wall, it bounces back an inch (both sides do this, so both see it).
		boolean banged = arrives && door.target >= HotelDoorBlock.FULL && door.speed >= HotelDoorBlock.SLAM;
		if (banged) {
			door.angle = door.target - 10;
			door.speed = HotelDoorBlock.NORMAL / 3;
			door.velocity = 0;
		}
		if (level.isClientSide) {
			return;
		}
		// Past halfway it's open.
		boolean open = door.angle >= HotelDoorBlock.FULL / 2;
		if (state.getValue(DoorBlock.OPEN) != open) {
			level.setBlock(pos, state.setValue(DoorBlock.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
		}
		if (door.speed < HotelDoorBlock.NORMAL && door.creak-- <= 0) {
			level.playSound(null, pos, RaukSounds.CREAK, SoundSource.BLOCKS, 0.35f, 0.55f + level.random.nextFloat() * 0.25f);
			door.creak = 14 + level.random.nextInt(10);
		}
		if (banged) {
			level.playSound(null, pos, RaukSounds.KNOCK_WOOD, SoundSource.BLOCKS, 1.6f, 0.7f);
			level.gameEvent(null, GameEvent.BLOCK_CLOSE, pos);
		} else if (arrives && door.target == 0 && door.speed >= HotelDoorBlock.NORMAL) {
			DoorBlock block = (DoorBlock) state.getBlock();
			level.playSound(null, pos, block.type().doorClose(), SoundSource.BLOCKS, door.speed >= HotelDoorBlock.SLAM ? 1.3f : 1,
					door.speed >= HotelDoorBlock.SLAM ? 0.7f : 0.95f);
		}
		if (arrives) {
			door.setChanged();
		}
	}

	// --- saving and syncing -------------------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putFloat("Angle", angle);
		tag.putFloat("Target", target);
		tag.putFloat("Speed", speed);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		float a = tag.getFloat("Angle");
		// The client keeps its own swing once it has one; only a big difference snaps it.
		if (!synced || Math.abs(a - angle) > 30) {
			angle = prev = a;
		}
		synced = true;
		target = tag.getFloat("Target");
		speed = tag.contains("Speed") ? tag.getFloat("Speed") : HotelDoorBlock.NORMAL;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
