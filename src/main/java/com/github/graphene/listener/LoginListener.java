package com.github.graphene.listener;

import com.github.graphene.Main;
import com.github.graphene.handler.encryption.PacketDecryptionHandler;
import com.github.graphene.handler.encryption.PacketEncryptionHandler;
import com.github.graphene.player.Player;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.UUIDUtil;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.util.crypto.MinecraftEncryptionUtil;
import com.github.retrooper.packetevents.wrapper.handshaking.client.WrapperHandshakingClientHandshake;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientEncryptionResponse;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerLoginSuccess;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.channel.ChannelPipeline;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

public class LoginListener implements PacketListener {
    private final boolean onlineMode;

    public LoginListener(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }


    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = event.getUser();
        Player player = (Player) event.getPlayer();
        if (event.getPacketType() == PacketType.Handshaking.Client.HANDSHAKE) {
            WrapperHandshakingClientHandshake handshake = new WrapperHandshakingClientHandshake(event);
            int protocolVersion = handshake.getProtocolVersion();
            if (handshake.getNextConnectionState() == ConnectionState.LOGIN
                    && protocolVersion != Main.SERVER_PROTOCOL_VERSION
                    || handshake.getNextConnectionState() == ConnectionState.PLAY) {//Why connect to play? xd
                user.closeConnection();
            }
        } else if (event.getPacketType() == PacketType.Login.Client.LOGIN_START) {
            //The client is attempting to log in.
            WrapperLoginClientLoginStart start = new WrapperLoginClientLoginStart(event);
            String username = start.getUsername();
            //If online mode is set to false, we just generate a UUID based on their username.
            UUID uuid = isOnlineMode() ? null : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            player.setUserProfile(new UserProfile(uuid, username));
            //If online mode is enabled, we begin the encryption and authentication process.
            if (isOnlineMode()) {
                //Server ID may be empty
                String serverID = "";
                //Public key of the server's key pair
                PublicKey key = Main.KEY_PAIR.getPublic();
                //Generate a random verify token, it has to be 4 bytes long
                byte[] verifyToken = new byte[4];
                new Random().nextBytes(verifyToken);

                player.setVerifyToken(verifyToken);
                player.setServerId(serverID);
                //Send our encryption request
                WrapperLoginServerEncryptionRequest encryptionRequest = new WrapperLoginServerEncryptionRequest(serverID, key, verifyToken);
                player.sendPacket(encryptionRequest);
            } else {
                boolean alreadyLoggedIn = false;
                for (Player p : Main.PLAYERS) {
                    if (p.getUsername().equals(username)) {
                        alreadyLoggedIn = true;
                    }
                }

                if (!alreadyLoggedIn) {
                    //Since we're not in online mode, we just inform the client that they have successfully logged in.
                    WrapperLoginServerLoginSuccess loginSuccess = new WrapperLoginServerLoginSuccess(player.getUserProfile());
                    player.sendPacket(loginSuccess);
                    JoinManager.handleJoin(user, player);
                } else {
                    player.kick("A user with the username " + username + " is already logged in.");
                }
            }
        }
        //They responded with an encryption response
        else if (event.getPacketType() == PacketType.Login.Client.ENCRYPTION_RESPONSE) {
            //Clone the event so we can process it on another thread
            event = event.clone();
            // Authenticate and handle player connection on our worker threads
            final PacketReceiveEvent finalEvent = event;
            Main.WORKER_THREADS.execute(() -> {
                WrapperLoginClientEncryptionResponse encryptionResponse = new WrapperLoginClientEncryptionResponse(finalEvent);
                //Decrypt the verify token
                byte[] verifyToken = MinecraftEncryptionUtil.decryptRSA(Main.KEY_PAIR.getPrivate(), encryptionResponse.getEncryptedVerifyToken().get());
                //Private key from the server's key pair
                PrivateKey privateKey = Main.KEY_PAIR.getPrivate();
                //Decrypt the shared secret
                byte[] sharedSecret = MinecraftEncryptionUtil.decrypt(privateKey.getAlgorithm(), privateKey, encryptionResponse.getEncryptedSharedSecret());
                MessageDigest digest;
                try {
                    digest = MessageDigest.getInstance("SHA-1");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    user.closeConnection();
                    return; // basically asserts that digest must be not null
                }
                digest.update(player.getServerId().getBytes(StandardCharsets.UTF_8));
                digest.update(sharedSecret);
                digest.update(Main.KEY_PAIR.getPublic().getEncoded());
                //We generate a server id hash that will be used in our web request to mojang's session server.
                String serverIdHash = new BigInteger(digest.digest()).toString(16);
                //Make sure the decrypted verify token from the client is the same one we sent out earlier.
                if (Arrays.equals(player.getVerifyToken(), verifyToken)) {
                    //GET web request using our server id hash.
                    try {
                        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" + player.getUsername() + "&serverId=" + serverIdHash);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestProperty("Authorization", null);
                        connection.setRequestMethod("GET");
                        if (connection.getResponseCode() == 204) {
                            Main.LOGGER.info("Failed to authenticate " + player.getUsername() + "!");
                            player.kick("Failed to authenticate your connection.");
                            return;
                        }
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(connection.getInputStream()));
                        String inputLine;
                        StringBuilder sb = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            sb.append(inputLine);
                        }
                        in.close();
                        //Parse the json response we got from the web request.
                        JsonObject jsonObject = AdventureSerializer.getGsonSerializer().serializer().fromJson(sb.toString(), JsonObject.class);

                        String username = jsonObject.get("name").getAsString();
                        String rawUUID = jsonObject.get("id").getAsString();
                        UUID uuid = UUIDUtil.fromStringWithoutDashes(rawUUID);
                        JsonArray textureProperties = jsonObject.get("properties").getAsJsonArray();
                        for (Player lPlayer : Main.PLAYERS) {
                            if (lPlayer.getUsername().equals(username)) {
                                lPlayer.kick("You logged in from another location!");
                            }
                        }
                        //Update our game profile, feed it with our real UUID, we've been authenticated.
                        UserProfile profile = player.getUserProfile();
                        profile.setUUID(uuid);
                        profile.setName(username);
                        for (JsonElement element : textureProperties) {
                            JsonObject property = element.getAsJsonObject();

                            String name = property.get("name").getAsString();
                            String value = property.get("value").getAsString();
                            String signature = property.get("signature").getAsString();

                            profile.getTextureProperties().add(new TextureProperty(name, value, signature));
                        }
                        //From now on, all packets will be decrypted and encrypted.
                        ChannelPipeline pipeline = player.getChannel().pipeline();
                        SecretKey sharedSecretKey = new SecretKeySpec(sharedSecret, "AES");
                        Cipher decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
                        decryptCipher.init(Cipher.DECRYPT_MODE, sharedSecretKey, new IvParameterSpec(sharedSecret));
                        //Add the decryption handler
                        pipeline.replace("decryption_handler", "decryption_handler", new PacketDecryptionHandler(decryptCipher));
                        Cipher encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
                        encryptCipher.init(Cipher.ENCRYPT_MODE, sharedSecretKey, new IvParameterSpec(sharedSecret));
                        //Add the encryption handler
                        pipeline.replace("encryption_handler", "encryption_handler", new PacketEncryptionHandler(encryptCipher));
                        //We now inform the client that they have successfully logged in.
                        //Note: The login success packet will be encrypted here.
                        WrapperLoginServerLoginSuccess loginSuccess = new WrapperLoginServerLoginSuccess(player.getUserProfile());
                        player.sendPacket(loginSuccess);
                        JoinManager.handleJoin(user, player);
                    } catch (IOException | NoSuchPaddingException | NoSuchAlgorithmException
                            | InvalidKeyException | InvalidAlgorithmParameterException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    Main.LOGGER.warning("Failed to authenticate " + player.getUsername() + ", because they replied with an invalid verify token!");
                    user.closeConnection();
                }
                finalEvent.cleanUp();
            });
        }
    }
}
