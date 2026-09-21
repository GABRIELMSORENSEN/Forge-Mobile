package com.g4t0xx.forge;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Vault {
 private final Context ctx;
 Vault(Context ctx) { this.ctx=ctx; }
 private SecretKey key() throws Exception {
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
  if (!ks.containsAlias("forge-vault")) {
   KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   g.init(new KeyGenParameterSpec.Builder("forge-vault",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); g.generateKey();
  }
  return (SecretKey)ks.getKey("forge-vault",null);
 }
 synchronized void put(String name,String value) throws Exception {
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
  String encoded=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
  if (!ctx.getSharedPreferences("vault",0).edit().putString(name,encoded).commit()) throw new java.io.IOException("Falha ao salvar credencial");
 }
 synchronized String get(String name) throws Exception {
  String s=ctx.getSharedPreferences("vault",0).getString(name,""); if(s.isEmpty())return "";
  String[] p=s.split(":"); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));
  return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
 }
 synchronized void clear() { ctx.getSharedPreferences("vault",0).edit().clear().commit(); }
}
