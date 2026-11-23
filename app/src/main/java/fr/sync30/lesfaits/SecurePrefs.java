package fr.sync30.lesfaits;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecurePrefs {
    private static final String ALIAS="lesfaits_sync30_aes";
    private final SharedPreferences prefs;
    SecurePrefs(Context c){ prefs=c.getSharedPreferences("sync30",Context.MODE_PRIVATE); }
    String url(){return prefs.getString("url","");}
    void url(String s){prefs.edit().putString("url",s).apply();}
    String deviceId(){
        String id=prefs.getString("deviceId","");
        if(id.isEmpty()){id=java.util.UUID.randomUUID().toString(); prefs.edit().putString("deviceId",id).apply();}
        return id;
    }
    String value(String name){return prefs.getString("oauth_"+name,"");}
    void value(String name,String value){prefs.edit().putString("oauth_"+name,value).apply();}
    void clearOAuth(){
        prefs.edit().remove("oauth_client_id").remove("oauth_expiration")
            .remove("secured_oauth_access").remove("secured_oauth_refresh")
            .remove("secured_oauth_verifier").remove("secured_oauth_state").apply();
    }
    void protectedValue(String name,String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=c.getIV(),enc=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] all=new byte[iv.length+enc.length];
        System.arraycopy(iv,0,all,0,iv.length);
        System.arraycopy(enc,0,all,iv.length,enc.length);
        prefs.edit().putString("secured_oauth_"+name,Base64.encodeToString(all,Base64.NO_WRAP)).apply();
    }
    String protectedValue(String name) {
        try {
            String encrypted=prefs.getString("secured_oauth_"+name,"");
            if(encrypted.isEmpty())return "";
            byte[] all=Base64.decode(encrypted,Base64.NO_WRAP);
            if(all.length<29)return "";
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(all,0,12)));
            return new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return g.generateKey();
    }
    void token(String token) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=c.getIV(),enc=c.doFinal(token.getBytes(StandardCharsets.UTF_8));
        byte[] both=new byte[iv.length+enc.length];
        System.arraycopy(iv,0,both,0,iv.length);
        System.arraycopy(enc,0,both,iv.length,enc.length);
        prefs.edit().putString("secret",Base64.encodeToString(both,Base64.NO_WRAP)).apply();
    }
    String token(){
        try {
            String data=prefs.getString("secret","");
            if(data.isEmpty())return "";
            byte[] all=Base64.decode(data,Base64.NO_WRAP);
            if(all.length<29)return "";
            byte[] iv=java.util.Arrays.copyOfRange(all,0,12);
            byte[] ciphertext=java.util.Arrays.copyOfRange(all,12,all.length);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new String(c.doFinal(ciphertext),StandardCharsets.UTF_8);
        } catch(Exception e){return "";}
    }
}
