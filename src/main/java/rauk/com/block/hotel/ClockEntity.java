package rauk.com.block.hotel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The study clock's hands (puzzle 5): where they stand, in minutes past twelve, seen by everyone. */
public class ClockEntity extends BlockEntity {
	/** Where Wennberg left them: twenty past six. */
	public static final int STOPPED_AT = 6 * 60 + 20;
	private int minutes = STOPPED_AT;

	public ClockEntity(BlockPos pos, BlockState state) {
		super(ClockBlock.entityType, pos, state);
	}

	public int minutes() {
		return minutes;
	}

	/** Where the hands stand (a clock moved keeps its time). */
	public void set(int minutes) {
		this.minutes = Math.floorMod(minutes, 12 * 60);
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	/** The minute hand on a minute, or the hour hand on an hour (the minutes stay where they are). */
	public void turn(boolean hour) {
		int h = minutes / 60, m = minutes % 60;
		minutes = hour ? Math.floorMod(h + 1, 12) * 60 + m : h * 60 + Math.floorMod(m + 1, 60);
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Minutes", minutes);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("Minutes")) {
			minutes = tag.getInt("Minutes");
		}
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
