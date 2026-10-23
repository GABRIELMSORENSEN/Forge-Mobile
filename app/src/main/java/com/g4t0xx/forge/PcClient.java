package com.g4t0xx.forge;
import org.json.*;import java.net.*;import java.io.*;import javax.net.ssl.*;import java.security.cert.X509Certificate;
final class PcClient {
 final Vault vault;PcClient(Vault v){vault=v;}
 JSONObject call(String path,JSONObject body)throws Exception{
  String url=vault.get("pc_url"),token=vault.get("pc_token"),pin=vault.get("pc_pin");if(url.isEmpty()||token.isEmpty()||pin.isEmpty())throw new IOException("Computador não pareado");
  URL u=new URL(url+path);if(!u.getProtocol().equals("https"))throw new IOException("O computador exige HTTPS");
  SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{try{if(c.length==0||!Json.hash(c[0].getEncoded()).equalsIgnoreCase(pin))throw new Exception("Certificado diferente do pareamento");}catch(Exception e){throw new java.security.cert.CertificateException(e);}}}},null);
  HttpsURLConnection c=(HttpsURLConnection)u.openConnection();if(!pin.equals("system")){c.setSSLSocketFactory(ssl.getSocketFactory());c.setHostnameVerifier((h,s)->h.equals(u.getHost()));}c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(240000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");
  try{if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}int code=c.getResponseCode();String text=Json.read(code>=400?c.getErrorStream():c.getInputStream(),4*1024*1024);if(code!=200)throw new IOException("Computador HTTP "+code+": "+text.substring(0,Math.min(300,text.length())));return new JSONObject(text);}finally{c.disconnect();}
 }
}
