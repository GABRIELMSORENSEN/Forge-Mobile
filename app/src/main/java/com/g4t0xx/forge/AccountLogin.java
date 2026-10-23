package com.g4t0xx.forge;
import android.content.*;import android.net.Uri;import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
/** Official Codex OAuth PKCE flow, with a loopback callback on the Android itself. */
final class AccountLogin {
 static final String CLIENT="app_EMoamEEZ73f0CkXaXp7hrann",REDIRECT="http://localhost:1455/auth/callback";
 static volatile boolean running;
 static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return android.util.Base64.encodeToString(b,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);}
 static String encode(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
 static void begin(MainActivity activity,Vault vault)throws Exception{
  if(running)throw new IOException("Login já aberto no navegador");ServerSocket server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),1455));server.setSoTimeout(300000);running=true;
  String verifier=random(),state=random(),challenge=android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
  String auth="https://auth.openai.com/oauth/authorize?response_type=code&client_id="+CLIENT+"&redirect_uri="+encode(REDIRECT)+"&scope="+encode("openid profile email offline_access")+"&code_challenge="+challenge+"&code_challenge_method=S256&state="+state+"&codex_cli_simplified_flow=true&originator=codex_cli_rs";
  new Thread(()->{try(ServerSocket socket=server){
   try(Socket callback=socket.accept()){
    BufferedReader in=new BufferedReader(new InputStreamReader(callback.getInputStream(),StandardCharsets.US_ASCII));String line=in.readLine();String target=line==null?"":line.split(" ")[1];Uri uri=Uri.parse("http://localhost"+target);
    if(!"/auth/callback".equals(uri.getPath())||!state.equals(uri.getQueryParameter("state")))throw new IOException("Retorno de login inválido");String code=uri.getQueryParameter("code");if(code==null)throw new IOException("Login não concluído");
    JSONObject result=exchange("grant_type=authorization_code&client_id="+CLIENT+"&code="+encode(code)+"&code_verifier="+encode(verifier)+"&redirect_uri="+encode(REDIRECT));
    JSONObject tokens=Json.obj("access_token",result.getString("access_token"),"refresh_token",result.optString("refresh_token"),"id_token",result.optString("id_token"));
    String jwt=result.optString("id_token",result.getString("access_token"));JSONObject payload=new JSONObject(new String(android.util.Base64.decode(jwt.split("\\.")[1],android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP),StandardCharsets.UTF_8));JSONObject claims=payload.optJSONObject("https://api.openai.com/auth");tokens.put("account_id",claims==null?"":claims.optString("chatgpt_account_id"));
    vault.put("codex_auth",Json.obj("auth_mode","chatgpt","tokens",tokens,"phone_dedicated",true,"last_refresh",java.time.Instant.now().toString()).toString());
    byte[] body="Login concluído. Volte ao Forge no celular.".getBytes(StandardCharsets.UTF_8);OutputStream out=callback.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();activity.emit("account",Json.obj("message","Conta conectada somente neste Android"));
   }
  }catch(Exception e){try{activity.emit("account",Json.obj("error","Login não concluído: "+e.getMessage()));}catch(Exception ignored){}}finally{running=false;}},"forge-login").start();
  activity.runOnUiThread(()->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(auth))));
 }
 static JSONObject exchange(String body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("https://auth.openai.com/oauth/token").openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");c.setDoOutput(true);try{try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();if(code!=200)throw new IOException("Autenticação HTTP "+code);return new JSONObject(Json.read(c.getInputStream(),30000));}finally{c.disconnect();}}
 static void refresh(Vault vault)throws Exception{JSONObject auth=new JSONObject(vault.get("codex_auth"));if(!auth.optBoolean("phone_dedicated"))throw new IOException("A sessão importada expirou. Use Entrar com ChatGPT para criar uma sessão própria do celular.");JSONObject tokens=auth.getJSONObject("tokens");JSONObject result=exchange("grant_type=refresh_token&client_id="+CLIENT+"&refresh_token="+encode(tokens.getString("refresh_token")));for(String k:new String[]{"access_token","refresh_token","id_token"})if(result.has(k))tokens.put(k,result.getString(k));auth.put("last_refresh",java.time.Instant.now().toString());vault.put("codex_auth",auth.toString());}
}
