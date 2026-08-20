package me.sofurry.via.api.events;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

/**
 * This event is fired when the user changes the target version in the screen, or if the user joins a server with a different version.
 * If the user disconnects, the event will also be fired with the current version.
 */
public interface ChangeProtocolVersionCallback {

    void onChangeProtocolVersion(final ProtocolVersion oldVersion, final ProtocolVersion newVersion);

}
