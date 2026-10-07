package org.lts.litematicaplus.share;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncmaticaPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<SyncmaticaPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SyncCodec.NAMESPACE, "main"));
    public static final StreamCodec<FriendlyByteBuf, SyncmaticaPayload> CODEC =
            CustomPacketPayload.codec(SyncmaticaPayload::write, SyncmaticaPayload::read);

    private static SyncmaticaPayload read(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new SyncmaticaPayload(data);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBytes(this.data);
    }

    @Override
    public Type<SyncmaticaPayload> type() {
        return TYPE;
    }
}
