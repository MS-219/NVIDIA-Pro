package com.juxin.orin.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * DES加密解密工具类
 * 模式: ECB
 * 填充方式: PKCS5Padding
 */
public class DESUtil {

    private static final String ALGORITHM = "DES";
    private static final String TRANSFORMATION = "DES/ECB/PKCS5Padding";

    /**
     * DES加密
     *
     * @param plainText 明文
     * @param key       DES密钥
     * @return Base64编码后的密文
     */
    public static String encrypt(String plainText, String key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKey secretKey = generateKey(key);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * DES解密
     *
     * @param encryptedText Base64编码的密文
     * @param key           DES密钥
     * @return 解密后的明文
     */
    public static String decrypt(String encryptedText, String key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKey secretKey = generateKey(key);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * 生成DES密钥
     */
    private static SecretKey generateKey(String key) throws Exception {
        DESKeySpec desKeySpec = new DESKeySpec(key.getBytes(StandardCharsets.UTF_8));
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
        return keyFactory.generateSecret(desKeySpec);
    }
}
