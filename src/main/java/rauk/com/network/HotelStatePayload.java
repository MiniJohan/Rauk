package rauk.com.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import rauk.com.Rauk;

/**
 * To the client: where this world's hotel is (its origin and how it's turned), if it has one, and
 * whether its power is on. The client needs it for the hotel's atmosphere (hotel design 6.3, 6.4):
 * the darkness, the fog and the sounds of wherever you are in it.
 */
public record HotelStatePayload(boolean present, long origin, int rotation, boolean power) implements CustomPacketPayload {
	public static final Type<HotelStatePayload> TYPE = new Type<>(Rauk.id("hotel_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HotelStatePayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeBoolean(p.present());
		buf.writeLong(p.origin());
		buf.writeVarInt(p.rotation());
		buf.writeBoolean(p.power());
	}, buf -> new HotelStatePayload(buf.readBoolean(), buf.readLong(), buf.readVarInt(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
