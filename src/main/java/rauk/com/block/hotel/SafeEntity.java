package rauk.com.block.hotel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The reception safe's dials (puzzle 1): the figure each of the four is turned to, seen by everyone. */
public class SafeEntity extends BlockEntity {
	private final int[] dials = new int[4];

	public SafeEntity(BlockPos pos, BlockState state) {
		super(SafeBlock.entityType, pos, state);
	}

	public int dial(int i) {
		return dials[i];
	}

	public int[] dials() {
		return dials.clone();
	}

	/** Turns a dial a figure on (or back), and lets everyone see it. */
	public void turn(int i, boolean back) {
		dials[i] = Math.floorMod(dials[i] + (back ? -1 : 1), 10);
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putIntArray("Dials", dials);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		int[] d = tag.getIntArray("Dials");
		for (int i = 0; i < Math.min(4, d.length); i++) {
			dials[i] = d[i];
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
