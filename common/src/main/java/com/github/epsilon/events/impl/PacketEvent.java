package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.network.protocol.Packet;

public class PacketEvent {

    public static class Send extends Cancellable {

        private Packet<?> packet;

        public Send(Packet<?> packet) {
            this.packet = packet;
        }

        public Packet<?> getPacket() {
            return this.packet;
        }

        public void setPacket(Packet<?> packet) {
            this.packet = packet;
        }

    }

    public static class Receive extends Cancellable {

        private Packet<?> packet;

        public Receive(Packet<?> packet) {
            this.packet = packet;
        }

        public Packet<?> getPacket() {
            return this.packet;
        }

        public void setPacket(Packet<?> packet) {
            this.packet = packet;
        }

    }

}
