package uk.co.thinkofdeath.micromc.network;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import uk.co.thinkofdeath.micromc.log.LogUtil;
import uk.co.thinkofdeath.micromc.network.login.LoginResponse;
import uk.co.thinkofdeath.micromc.network.login.Property;
import uk.co.thinkofdeath.micromc.network.protocol.login.EncryptionRequest;
import uk.co.thinkofdeath.micromc.network.protocol.login.EncryptionResponse;
import uk.co.thinkofdeath.micromc.network.protocol.login.LoginStart;
import uk.co.thinkofdeath.micromc.network.protocol.login.LoginSuccess;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

public class LoginHandler implements PacketHandler {

    private static final Gson gson = new Gson();
    private static final Logger logger = LogUtil.get(LoginHandler.class);
    private NetworkHandler handler;
    private String username;
    private State currentState = State.START;
    private byte[] verifyToken;
    private String serverId = "";
    private UUID uuid;
    private Property[] properties;

    @Override
    public void setNetworkHandler(NetworkHandler handler) {
        this.handler = handler;
    }

    public void handle(LoginStart loginStart) {
        require(State.START);
        username = loginStart.getUsername();
        if (handler.getServer().isOnlineMode()) {
            currentState = State.WAITING_RESPONSE;
            verifyToken = new byte[16];
            try {
                SecureRandom.getInstanceStrong().nextBytes(verifyToken);
            } catch (NoSuchAlgorithmException e) {
                handler.disconnect(e.getMessage());
            }
            handler.sendPacket(new EncryptionRequest(
                    serverId,
                    handler.getServer().getNetworkKeyPair().getPublic(),
                    verifyToken
            ));
        } else {
            finishLogin();
            currentState = State.COMPLETE;
        }
    }

    public void handle(EncryptionResponse encryptionResponse) {
        require(State.WAITING_RESPONSE);

        LoginResponse response;
        try {
            response = tryAuth(encryptionResponse);
            if (response == null) {
                handler.disconnect("Failed to verify username against the session servers");
                return;
            }
        } catch (IOException e) {
            handler.disconnect(e.getMessage());
            return;
        }

        String ustr = response.getId();
        uuid = UUID.fromString(ustr.substring(0, 8) + "-" + ustr.substring(8, 12) + "-" + ustr.substring(12, 16) + "-" + ustr.substring(16, 20) + "-" + ustr.substring(20, 32));
        username = response.getName();

        finishLogin();
        currentState = State.COMPLETE;
    }

    private LoginResponse tryAuth(EncryptionResponse encryptionResponse) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("sha1");

            byte[] testKey = decrypt(encryptionResponse.getVerifyToken());
            byte[] secretKeyBytes = decrypt(encryptionResponse.getSecretKey());

            if (!Arrays.equals(testKey, verifyToken)) {
                handler.disconnect("Verify token incorrect");
            }

            SecretKey secretKey = new SecretKeySpec(secretKeyBytes, "AES");

            digest.update(serverId.getBytes(StandardCharsets.UTF_8));
            digest.update(secretKeyBytes);
            digest.update(handler.getServer().getNetworkKeyPair().getPublic().getEncoded());

            handler.enableEncryption(secretKey);

            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/hasJoined?" +
                    "username=" + username
                    + "&serverId=" + new BigInteger(digest.digest()).toString(16));
            LoginResponse response = gson.fromJson(
                    Resources.asCharSource(url, StandardCharsets.UTF_8).openBufferedStream(),
                    LoginResponse.class);
            return response;
        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException
                | InvalidKeyException
                | IllegalBlockSizeException
                | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] decrypt(byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        PrivateKey privateKey = handler.getServer().getNetworkKeyPair().getPrivate();
        Cipher cipher = Cipher.getInstance(privateKey.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    private void finishLogin() {
        if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        }
        if (properties == null) {
            properties = new Property[0];
        }

        handler.sendPacket(new LoginSuccess(
                uuid.toString(),
                username
        ));
        logger.info("User " + username + " as logged in with uuid " + uuid);
    }

    private void require(State state) {
        if (state != currentState) {
            throw new RuntimeException("Incorrect state");
        }
    }

    private static enum State {
        START,
        WAITING_RESPONSE,
        COMPLETE
    }
}