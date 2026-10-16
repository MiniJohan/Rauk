package rauk.com.hotel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukEntity;
import rauk.com.hotel.HotelPlan.Mark;
import rauk.com.registry.RaukEntities;

/**
 * The whole hotel as one structure piece. World generation calls it once for every chunk it
 * covers; each time it shapes the ground in that chunk and copies in that chunk's share of the
 * plan. The chunk with Rauk's starting place in it also gets Rauk (see {@link RaukStart}).
 */
public class HotelPiece extends StructurePiece {
	private final HotelFrame frame;

	public HotelPiece(HotelFrame frame) {
		super(Hotel.PIECE, 0, frame.bounds());
		this.frame = frame;
	}

	public HotelPiece(CompoundTag tag) {
		super(Hotel.PIECE, tag);
		this.frame = new HotelFrame(BlockPos.of(tag.getLong("Origin")), Rotation.values()[tag.getInt("Rot")]);
	}

	public HotelFrame frame() {
		return frame;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Origin", frame.origin().asLong());
		tag.putInt("Rot", frame.rotation().ordinal());
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
			BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
		HotelWriter.shapeGround(level, frame, chunk, generator.getSeaLevel());
		HotelWriter.write(level, frame, chunk, 2, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
		HotelPlan plan = HotelBuilder.plan();
		Mark den = plan.marks.get("rauk_den");
		BlockPos home = frame.toWorld(den.x(), den.y(), den.z());
		// Its home is the Cabinet, but it may be anywhere in the hotel when you find it.
		int[] start = RaukStart.pick(plan, level.getSeed() ^ frame.origin().asLong());
		BlockPos at = frame.toWorld(start[0], start[1], start[2]);
		if (chunk.isInside(at)) {
			RaukEntity rauk = RaukEntities.RAUK.create(level.getLevel());
			if (rauk != null) {
				rauk.moveTo(Vec3.atBottomCenterOf(at), random.nextFloat() * 360, 0);
				rauk.setHome(home);
				rauk.setPersistenceRequired();
				level.addFreshEntity(rauk);
			}
		}
	}
}
