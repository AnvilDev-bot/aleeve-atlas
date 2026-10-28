package dev.dubhe.map.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ServerKeyTest {
    private static final String ID = "a00a78df-1c3c-4e66-947b-a679437aaeb1";

    @AfterEach void clearIdentity() { ServerKey.clearRemoteCacheId(); }

    @Test void usesActualSaveFolderAndSignedSeed() {
        assertEquals("sp_新的世界_-123456789", ServerKey.singleplayerKey(
                Path.of("run", "saves", "新的世界", "."), -123456789));
        assertNotEquals(ServerKey.singleplayerKey(Path.of("saves", "A"), 1),
                ServerKey.singleplayerKey(Path.of("saves", "B"), 1));
        assertNotEquals(ServerKey.singleplayerKey(Path.of("saves", "A"), 1),
                ServerKey.singleplayerKey(Path.of("saves", "A"), 2));
    }

    @Test void remoteIdentityIsReadableStableAndOverridesChangingLanAddress() {
        assertEquals("server_127.0.0.1:25565", ServerKey.multiplayerKey("127.0.0.1:25565"));
        ServerKey.setRemoteCacheId(ID);
        assertEquals("server_" + ID, ServerKey.multiplayerKey("192.168.1.2:49152"));
        assertEquals("server_" + ID, ServerKey.multiplayerKey("192.168.1.5:49153"));
        assertEquals("sp_LAN host_7", ServerKey.singleplayerKey(Path.of("saves", "LAN host"), 7));
        ServerKey.clearRemoteCacheId();
        assertEquals("server_other.example:25565", ServerKey.multiplayerKey("other.example:25565"));
    }

    @Test void rejectsInvalidRemoteIdsInsteadOfUsingThemAsPaths() {
        ServerKey.setRemoteCacheId("../../another-world");
        assertEquals("server_localhost", ServerKey.multiplayerKey("localhost"));
        ServerKey.setRemoteCacheId(ID.toUpperCase());
        assertEquals("server_" + ID, ServerKey.multiplayerKey("localhost"));
    }
}
