package uk.co.thinkofdeath.micromc.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

public class CipherCodec extends ByteToMessageCodec<ByteBuf> {

    private Cipher cipherEncrypt;
    private Cipher cipherDecrypt;

    private byte[] encryptBuffer = new byte[8192];
    private byte[] dataBuffer = new byte[8192];

    public CipherCodec(SecretKey secretKey) {
        super(false);
        try {
            cipherEncrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            cipherEncrypt.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(secretKey.getEncoded()));

            cipherDecrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(secretKey.getEncoded()));
        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException
                | InvalidAlgorithmParameterException
                | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        byte[] data;
        int offset = 0;
        int dataSize;
        if (!msg.isDirect()) {
            data = msg.array();
            msg.skipBytes(msg.readableBytes());
            offset = msg.arrayOffset();
            dataSize = msg.readableBytes();
        } else {
            dataSize = msg.readableBytes();
            if (dataBuffer.length < dataSize) {
                dataBuffer = new byte[dataSize];
            }
            msg.readBytes(dataBuffer, 0, dataSize);
            data = dataBuffer;
        }
        int size = cipherEncrypt.getOutputSize(msg.readableBytes());
        if (encryptBuffer.length < size) {
            encryptBuffer = new byte[size];
        }
        int count = cipherEncrypt.update(data, offset, dataSize, encryptBuffer);
        out.writeBytes(encryptBuffer, 0, count);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] data = in.array();
        int size = cipherEncrypt.getOutputSize(in.readableBytes());
        ByteBuf buf = ctx.alloc().heapBuffer(size);
        buf.writerIndex(cipherEncrypt.update(data, in.arrayOffset(), in.readableBytes(), buf.array(), buf.arrayOffset()));
        out.add(buf);
    }
}