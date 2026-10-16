package rauk.com.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import rauk.com.Rauk;

/**
 * To the client: you're riding a lift (hotel design 3.2). The view jolts as it starts (or stops)
 * and trembles for as many ticks as the ride lasts.
 */
public record LiftPayload(int ticks, float jolt) implements CustomPacketPayload {
	public static final Type<LiftPayload> TYPE = new Type<>(Rauk.id("lift"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LiftPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.ticks());
		buf.writeFloat(p.jolt());
	}, buf -> new LiftPayload(buf.readVarInt(), buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
