package com.juxin.orin.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA签名验签工具类
 * 算法: SHA1WithRSA
 * 密钥位数: 1024
 * 填充方式: PKCS8
 */
public class RSAUtil {

    private static final String ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1WithRSA";

    /**
     * 使用私钥签名
     *
     * @param data       待签名数据
     * @param privateKey Base64编码的私钥(不含头尾标识)
     * @return Base64编码的签名
     */
    public static String sign(String data, String privateKey) throws Exception {
        // 去除可能存在的换行符和空格
        privateKey = privateKey.replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PrivateKey priKey = keyFactory.generatePrivate(keySpec);

        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(priKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));

        byte[] signBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signBytes);
    }

    /**
     * 使用公钥验签
     *
     * @param data      原始数据
     * @param sign      Base64编码的签名
     * @param publicKey Base64编码的公钥(不含头尾标识)
     * @return 验签是否通过
     */
    public static boolean verify(String data, String sign, String publicKey) throws Exception {
        // 去除可能存在的换行符和空格
        publicKey = publicKey.replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        PublicKey pubKey = keyFactory.generatePublic(keySpec);

        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(pubKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));

        byte[] signBytes = Base64.getDecoder().decode(sign);
        return signature.verify(signBytes);
    }

    /**
     * 生成RSA密钥对 (仅用于测试)
     */
    public static java.security.KeyPair generateKeyPair() throws Exception {
        java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance(ALGORITHM);
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 获取私钥字符串
     */
    public static String getPrivateKeyString(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * 获取公钥字符串
     */
    public static String getPublicKeyString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
