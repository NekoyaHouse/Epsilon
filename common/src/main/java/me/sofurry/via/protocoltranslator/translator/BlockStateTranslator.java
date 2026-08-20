package me.sofurry.via.protocoltranslator.translator;

import com.github.epsilon.Constants;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_17_1to1_18.packet.ClientboundPackets1_18;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockStateTranslator {

    private static final UserConnection DUMMY_USER_CONNECTION = ProtocolTranslator.createDummyUserConnection(ProtocolTranslator.NATIVE_VERSION, ProtocolVersion.v1_18_2);

    /**
     * Converts a 1.18.2-block state to a native block state (the current version)
     *
     * @param blockStateId The block state id
     * @return The native block state
     */
    public static BlockState via1_18_2toMc(final int blockStateId) {
        try {
            final PacketWrapper levelEvent = PacketWrapper.create(ClientboundPackets1_18.LEVEL_EVENT, DUMMY_USER_CONNECTION);
            levelEvent.write(Types.INT, 2001); // eventId
            levelEvent.write(Types.BLOCK_POSITION1_14, new BlockPosition(0, 0, 0)); // position
            levelEvent.write(Types.INT, blockStateId); // data
            levelEvent.write(Types.BOOLEAN, false); // global

            levelEvent.resetReader();
            levelEvent.user().getProtocolInfo().getPipeline().transform(Direction.CLIENTBOUND, State.PLAY, levelEvent);

            levelEvent.read(Types.INT); // eventId
            levelEvent.read(Types.BLOCK_POSITION1_14); // position
            return Block.stateById(levelEvent.read(Types.INT)); // data
        } catch (Throwable t) {
            Constants.LOGGER.error("Error converting ViaVersion 1.18.2 block state to native block state", t);
            return Blocks.AIR.defaultBlockState();
        }
    }

}
