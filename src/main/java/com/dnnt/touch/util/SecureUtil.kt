package com.dnnt.touch.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.dnnt.touch.domain.User
import com.dnnt.touch.mapper.UserMapper
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.UnsupportedEncodingException
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

fun getMD5Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("MD5")
    val md5Bytes = digest.digest(bytes)
    return bytesToHex(md5Bytes)
}


fun bytesToHex(bytes: ByteArray): String {
    val hexArray = "0123456789ABCDEF".toCharArray()
    val hexChars = CharArray(bytes.size shl 1)
    for (j in bytes.indices) {
        val v = bytes[j].toInt() and 0xFF
        hexChars[j * 2] = hexArray[v ushr 4]
        hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
}

fun hexToBytes(hex: String): ByteArray{
    val bytes = ByteArray(hex.length shr 1)
    for(i in 0 until hex.length step  2){
        val d1 = Character.digit(hex[i],16) shl 4
        val d2 = Character.digit(hex[i + 1],16)
        bytes[i shr 1] = (d1 xor d2).toByte()
    }
    return bytes
}

fun hexToPublicKey(hex: String): PublicKey {
    val bytes = hexToBytes(hex)
    return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
}

fun rsaDecrypt(key: Key, bytes: ByteArray): ByteArray{
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.DECRYPT_MODE,key)
    return cipher.doFinal(bytes)
}

fun rsaEncrypt(key: Key, bytes: ByteArray): ByteArray{
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE,key)
    return cipher.doFinal(bytes)
}

fun aesEncrypt(key: Key, bytes: ByteArray): ByteArray{
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE,key)
    return cipher.doFinal(bytes)
}

fun aesDecrypt(key: Key, bytes: ByteArray): ByteArray{
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE,key)
    return cipher.doFinal(bytes)
}

fun genSecretKey(hex: String): SecretKey {
    val keyGen = KeyGenerator.getInstance("AES")
    val random = SecureRandom.getInstance("SHA1PRNG")
    random.setSeed(hex.toByteArray())
    keyGen.init(128,random)
    return keyGen.generateKey()
}


fun getSSLContext(): SSLContext{
    val password = "123456"
    val keyStore = KeyStore.getInstance("jks")

    var jksFile = File("../conf/tomcat.jks")
    //用于测试
    if (!jksFile.exists()){
        jksFile = File("/opt/tomcat9/conf/tomcat.jks")
    }
    val fis = FileInputStream(jksFile)
    keyStore.load(fis,password.toCharArray())
    fis.close()
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(keyStore,password.toCharArray())
    val kms = kmf.keyManagers
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kms,null,null)
    return sslContext
}

fun verifyToken(token: String,userMapper: UserMapper): User? {
    val id = JWT.decode(token).getClaim(User.ID).asLong()!!
    val user = userMapper.selectById(id)
    try {
        JWT.require(Algorithm.HMAC256(Constant.TOKEN_KEY + user.password))
                .build()
                .verify(token)
        return user
    } catch (e: UnsupportedEncodingException) {
        e.printStackTrace()
    }
    return null
}