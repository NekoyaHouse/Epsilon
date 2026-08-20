package me.sofurry.via.injection.access.core;

import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ClassicProtocolExtension;

import java.util.EnumMap;

public interface IExtensionProtocolMetadataStorage {

    EnumMap<ClassicProtocolExtension, Integer> viaVersion$getServerExtensions();

}
