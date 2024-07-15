package com.github.graphene.player;

import com.github.graphene.Main;
import com.github.graphene.util.entity.ClientSettings;
import com.github.graphene.util.entity.EntityInformation;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage_v1_16;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerDisconnect;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import io.netty.channel.Channel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.UUID;

public class Player {
    private final User user;
    private final int entityID = Main.ENTITIES++;
    private GameMode gameMode = GameMode.SURVIVAL;
    private GameMode previousGameMode = null;
    private String serverID = "";
    private byte[] verifyToken;
    private String serverAddress;
    private UserProfile userProfile;
    private long lastKeepAliveTime = Long.MAX_VALUE;
    private long keepAliveTimer = System.currentTimeMillis();
    private long latency = 0L;
    private long sendKeepAliveTime = 0L;
    private EntityInformation entityInformation;
    private ClientSettings clientSettings;
    public final ItemStack[] inventory = new ItemStack[45];
    public int currentSlot;

    public Player(User user) {
        this.user = user;
        this.clientSettings = new ClientSettings("", 0, SkinSection.ALL, WrapperPlayClientSettings.ChatVisibility.FULL, HumanoidArm.RIGHT);

    }

    @Nullable
    public ItemStack getCurrentItem() {
        return getHotbarIndex(currentSlot);
    }

    public void setCurrentItem(@Nullable ItemStack item) {
        setHotbarIndex(currentSlot, item);
    }

    @Nullable
    public ItemStack getHotbarIndex(int slot) {
        return inventory[slot + 36];
    }

    public void setHotbarIndex(int slot, @Nullable ItemStack itemStack) {
        inventory[slot + 36] = itemStack;
    }

    public String getUsername() {
        return userProfile.getName();
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    public Channel getChannel() {
        return (Channel) user.getChannel();
    }

    public int getEntityId() {
        return entityID;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public GameMode getPreviousGameMode() {
        return previousGameMode;
    }

    public void setPreviousGameMode(GameMode previousGameMode) {
        this.previousGameMode = previousGameMode;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getServerId() {
        return serverID;
    }

    public void setServerId(String serverID) {
        this.serverID = serverID;
    }

    public byte[] getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken;
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress) getChannel().remoteAddress();
    }

    public void writePacket(PacketWrapper<?> wrapper) {
        wrapper.setServerVersion(getClientVersion().toServerVersion());
        PacketEvents.getAPI().getProtocolManager().writePacket(getChannel(), wrapper);
    }

    public ClientVersion getClientVersion() {
        return user.getClientVersion();
    }

    public void sendPacket(PacketWrapper<?> wrapper) {
        //wrapper.setServerVersion(getClientVersion().toServerVersion());
        user.sendPacket(wrapper);
    }

    public void sendMessage(Component component) {
        user.sendMessage(component);
    }

    @Deprecated
    public void sendMessage(String message) {
        user.sendMessage(message);
    }

    public void closeConnection() {
        user.closeConnection();
    }

    public void kick(Component component) {
        ConnectionState state = PacketEvents.getAPI().getPlayerManager().getConnectionState(this);

        PacketWrapper<?> wrapper = switch (state) {
            case HANDSHAKING, STATUS -> null;
            case CONFIGURATION -> new WrapperConfigServerDisconnect(component);
            case LOGIN -> new WrapperLoginServerDisconnect(component);
            case PLAY -> new WrapperPlayServerDisconnect(component);
        };

        if (wrapper != null) {
            user.sendPacket(wrapper);
        }
        closeConnection();

    }

    public void kick(String legacyReason) {
        Component component = AdventureSerializer.fromLegacyFormat(legacyReason);
        kick(component);
    }

    public long getLastKeepAliveTime() {
        return lastKeepAliveTime;
    }

    public void setLastKeepAliveTime(long lastKeepAliveTime) {
        this.lastKeepAliveTime = lastKeepAliveTime;
    }

    public long getKeepAliveTimer() {
        return keepAliveTimer;
    }

    public void setKeepAliveTimer(long keepAliveTimer) {
        this.keepAliveTimer = keepAliveTimer;
    }

    public long getLatency() {
        return latency;
    }

    public void setLatency(long latency) {
        this.latency = latency;
    }

    public long getSendKeepAliveTime() {
        return sendKeepAliveTime;
    }

    public void setSendKeepAliveTime(long sendKeepAliveTime) {
        this.sendKeepAliveTime = sendKeepAliveTime;
    }

    public EntityInformation getEntityInformation() {
        return entityInformation;
    }

    public void setEntityInformation(EntityInformation entityInformation) {
        this.entityInformation = entityInformation;
    }

    public ClientSettings getClientSettings() {
        return clientSettings;
    }

    public void setClientSettings(ClientSettings clientSettings) {
        this.clientSettings = clientSettings;
    }

    public void updateHotbar() {
        for (int i = 0; i < inventory.length; i++) {
            ItemStack item = inventory[i];
            if (item == null) {
                item = ItemStack.builder().type(ItemTypes.AIR).amount(64).build();
            }
            WrapperPlayServerSetSlot setSlot = new WrapperPlayServerSetSlot(0, (int) (Math.random() * 1000), i, item);
            sendPacket(setSlot);
        }
    }
}
