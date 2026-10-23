package com.g4t0xx.forge;
import android.content.Context;import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.TimeUnit;
/** Device-local execution and provider APIs. No credential forwarding to repositories. */
final class ServiceTools {
 static JSONObject api(Context ctx,Vault vault,JSONObject a)throws Exception{
  if(ctx.getSharedPreferences("assistant",0).getBoolean("offline",false))throw new IOException("Modo offline: API remota desativada");
  String provider=a.getString("provider"),host;
  switch(provider){case "github":host="api.github.com";break;case "vercel":host="api.vercel.com";break;case "supabase":host="api.supabase.com";break;default:throw new IOException("Provedor desconhecido");}
  String path=a.getString("path"),method=a.optString("method","GET");
  if(!path.startsWith("/")||path.startsWith("//")||path.contains("\\")||path.contains("#")||path.contains("\r")||path.contains("\n"))throw new IOException("Caminho de API inválido");
  if(path.toLowerCase(java.util.Locale.ROOT).contains("/secrets"))throw new IOException("Publicação de credenciais desativada");
  if(!java.util.Arrays.asList("GET","POST","PUT","PATCH","DELETE").contains(method))throw new IOException("Método inválido");
  String token=vault.get(provider);if(token.isEmpty())throw new IOException("Conecte "+provider+" nas configurações");
  HttpURLConnection c=(HttpURLConnection)new URL("https://"+host+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("User-Agent","Forge-Personal");c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json");
  try{if(a.has("body")){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(a.get("body").toString().getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String text=in==null?"":Json.read(in,1000000);return Json.obj("status",code,"body",text.replace(token,"[credencial]"));}finally{c.disconnect();}
 }
 static JSONObject terminal(Context ctx,JSONObject a)throws Exception{
  File workspace=new File(ctx.getFilesDir(),"workspace");workspace.mkdirs();File script=File.createTempFile("terminal-",".sh",ctx.getCacheDir()),log=File.createTempFile("terminal-",".log",ctx.getCacheDir());
  try{Json.atomic(script,a.getString("command"));ProcessBuilder builder=new ProcessBuilder("/system/bin/sh",script.getAbsolutePath()).directory(workspace).redirectErrorStream(true).redirectOutput(log);builder.environment().put("HOME",workspace.getAbsolutePath());Process p=builder.start();if(!p.waitFor(120,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Terminal excedeu 120 segundos");}String output=Json.read(new FileInputStream(log),1000000);return Json.obj("exit",p.exitValue(),"output",output,"scope","UID privado do Android; ferramentas dependem do que está instalado");}finally{script.delete();log.delete();}
 }
}
