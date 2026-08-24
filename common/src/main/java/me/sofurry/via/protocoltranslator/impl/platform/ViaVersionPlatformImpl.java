/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.sofurry.via.protocoltranslator.impl.platform;

import com.github.epsilon.Constants;
import com.github.epsilon.managers.ConfigManager;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.configuration.AbstractViaConfig;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.platform.ViaVersionPlatform;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.protocoltranslator.protocol.ViaVersionProtocol;
import me.sofurry.via.util.ClassLoaderPriorityUtil;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ViaVersionPlatformImpl extends UserConnectionViaVersionPlatform {

    public ViaVersionPlatformImpl(File dataFolder) {
        super(dataFolder);
    }

    @Override
    public Logger createLogger(final String name) {
        final Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            {
                setLevel(Level.ALL);
            }

            @Override
            public void publish(final LogRecord record) {
                if (!isLoggable(record)) {
                    return;
                }

                final String message = record.getMessage();
                final Throwable throwable = record.getThrown();
                if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    if (throwable == null) {
                        Constants.LOGGER.error(message);
                    } else {
                        Constants.LOGGER.error(message, throwable);
                    }
                } else if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    if (throwable == null) {
                        Constants.LOGGER.warn(message);
                    } else {
                        Constants.LOGGER.warn(message, throwable);
                    }
                } else if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                    if (throwable == null) {
                        Constants.LOGGER.info(message);
                    } else {
                        Constants.LOGGER.info(message, throwable);
                    }
                } else if (throwable == null) {
                    Constants.LOGGER.debug(message);
                } else {
                    Constants.LOGGER.debug(message, throwable);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    @Override
    public String getPlatformName() {
        return "ViaVersion";
    }

    @Override
    public String getPlatformVersion() {
        return ViaVersionImpl.INSTANCE.getVersion();
    }

    @Override
    protected AbstractViaConfig createConfig() {
        return ConfigManager.INSTANCE.createViaVersionRuntimeConfig(this.getLogger());
    }

    @Override
    public void sendCustomPayload(UserConnection connection, String channel, byte[] message) {
        final PacketWrapper customPayload = PacketWrapper.create(ViaVersionProtocol.INSTANCE.getCustomPayloadPacketType(), connection);
        customPayload.write(Types.STRING, channel);
        customPayload.write(Types.REMAINING_BYTES, message);
        customPayload.scheduleSendToServer(ViaVersionProtocol.class);
    }

    @Override
    public void sendCustomPayloadToClient(final UserConnection connection, final String channel, final byte[] message) {
        final PacketWrapper customPayload = PacketWrapper.create(ViaVersionProtocol.INSTANCE.getClientboundCustomPayloadPacketType(), connection);
        customPayload.write(Types.STRING, channel);
        customPayload.write(Types.REMAINING_BYTES, message);
        customPayload.scheduleSend(ViaVersionProtocol.class);
    }

    @Override
    public JsonObject getDump() {
        final JsonObject platformDump = new JsonObject();
        platformDump.addProperty("impl_version", ViaVersionImpl.INSTANCE.getImplVersion());
        platformDump.addProperty("native_version", ProtocolTranslator.NATIVE_VERSION.toString());
        platformDump.addProperty("target_version", ProtocolTranslator.getTargetVersion().toString());
        platformDump.addProperty("in_world", Minecraft.getInstance().level != null);

        final List<ViaVersionPlatform.ModInfo> allMods = ViaVersionPlatform.get().getMods();
        final JsonArray mods = new JsonArray(allMods.size());
        for (final ViaVersionPlatform.ModInfo metadata : allMods) {
            final JsonObject mod = new JsonObject();
            mod.addProperty("id", metadata.id());
            mod.addProperty("name", metadata.name());
            mod.addProperty("version", metadata.version());
            final JsonArray authors = new JsonArray(metadata.authors().size());
            for (final ViaVersionPlatform.ModAuthor person : metadata.authors()) {
                final JsonObject info = new JsonObject();
                final Map<String, String> contactMap = person.contact();
                if (!contactMap.isEmpty()) {
                    final JsonObject contact = new JsonObject();
                    contactMap.forEach(contact::addProperty);
                    info.add("contact", contact);
                }
                info.addProperty("name", person.name());
                authors.add(info);
            }
            mod.add("authors", authors);

            mods.add(mod);
        }

        platformDump.add("mods", mods);

        if (!ClassLoaderPriorityUtil.getOverridingJars().isEmpty()) {
            final JsonArray overridingJars = new JsonArray();
            for (final String jar : ClassLoaderPriorityUtil.getOverridingJars()) {
                overridingJars.add(jar);
            }
            platformDump.add("overriding_jars", overridingJars);
        }

        return platformDump;
    }

}
