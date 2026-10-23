package com.g4t0xx.forge;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.UUID;

final class OfflineEngine {
 static final String MODEL="Qwen3.5-0.8B-Q4_K_M.gguf";
 static final String SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517";
 final Context ctx;File model;private Process process;private int port;private final String token=UUID.randomUUID().toString();
 private volatile String status="Modelo não carregado";private boolean verified;
 OfflineEngine(Context c){ctx=c;model=new File(c.getFilesDir(),"models/"+MODEL);}
 JSONArray catalog()throws Exception{return new JSONArray(Json.read(ctx.getAssets().open("models.json"),100000));}
 JSONObject selected()throws Exception{String name=ctx.getSharedPreferences("assistant",0).getString("local_model",MODEL);JSONArray list=catalog();for(int i=0;i<list.length();i++)if(list.getJSONObject(i).getString("file").equals(name))return list.getJSONObject(i);return Json.obj("file",name,"size",0,"sha256","");}
 void refreshModel()throws Exception{String name=selected().getString("file");if(!new File(name).getName().equals(name))throw new IOException("Nome de modelo inválido");File f=new File(ctx.getFilesDir(),"models/"+name);if(!f.equals(model)){stop();model=f;verified=false;}}
 boolean installed(){try{refreshModel();return model.exists()&&(selected().optLong("size")==0||model.length()==selected().getLong("size"));}catch(Exception e){return false;}}
 synchronized void ensure()throws Exception{
  refreshModel();if(process!=null&&process.isAlive())return;
  if(!installed())throw new IOException("Modelo offline ausente. Instale o modelo em Equipe → Preparar offline.");
  status="Verificando modelo local";
  if(!verified){java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(model)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02x",b));String expected=selected().optString("sha256");if(!expected.isEmpty()&&!expected.equals(digest.toString()))throw new IOException("Modelo local corrompido: SHA-256 incorreto");verified=true;}
  try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
  status="Carregando Qwen no Android";
  android.content.SharedPreferences prefs=ctx.getSharedPreferences("assistant",0);process=new ProcessBuilder(ctx.getApplicationInfo().nativeLibraryDir+"/libforge_llama.so","-m",model.getAbsolutePath(),"--host","127.0.0.1","--port",String.valueOf(port),"-c",String.valueOf(prefs.getInt("context",4096)),"-ngl","0","-t",String.valueOf(prefs.getInt("threads",3)),"--parallel","1","--jinja","--reasoning","off","--api-key",token).redirectErrorStream(true).redirectOutput(new File(ctx.getFilesDir(),"local-engine.log")).start();
  for(int i=0;i<180;i++){if(!process.isAlive())throw new IOException("Motor local encerrou. Libere memória e retome a tarefa.");try{call("/v1/models",null,2000);status="Qwen pronto · três agentes sequenciais";return;}catch(Exception ignored){}Thread.sleep(500);}throw new IOException("Modelo demorou para carregar");
 }
 JSONObject info()throws Exception{return Json.obj("installed",installed(),"running",process!=null&&process.isAlive(),"status",status,"model",model.getName(),"catalog",catalog());}
 private JSONObject call(String path,JSONObject body,int timeout)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection();c.setConnectTimeout(3000);c.setReadTimeout(timeout);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");
  try{if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}int code=c.getResponseCode();String text=Json.read(code>=400?c.getErrorStream():c.getInputStream(),512000);if(code!=200)throw new IOException("Motor local HTTP "+code);return new JSONObject(text);}finally{c.disconnect();}
 }
 String generate(String system,String prompt)throws Exception{
  if(MediaStudio.get(ctx).busy)throw new IOException("Aguarde a geracao de midia antes de usar o modelo local");ensure();android.content.SharedPreferences p=ctx.getSharedPreferences("assistant",0);int max=p.getInt("output_tokens",1024);if(p.getBoolean("economy",true)&&(system.contains("planejador")||system.contains("revisor")))max=Math.min(max,384);JSONObject response=call("/v1/chat/completions",Json.obj("messages",new JSONArray().put(Json.obj("role","system","content",system)).put(Json.obj("role","user","content",prompt)),"temperature",0.15,"max_tokens",max,"response_format",Json.obj("type","json_object"),"chat_template_kwargs",Json.obj("enable_thinking",false)),1800000);
  return response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
 }
 synchronized void stop(){if(process!=null){process.destroy();try{if(!process.waitFor(300,java.util.concurrent.TimeUnit.MILLISECONDS))process.destroyForcibly();}catch(InterruptedException e){process.destroyForcibly();Thread.currentThread().interrupt();}process=null;}status="Memória liberada";}
 void install()throws Exception{
  refreshModel();if(installed())return;JSONObject spec=selected();long expectedSize=spec.getLong("size");if(expectedSize<=0)throw new IOException("Importe o modelo GGUF pelo seletor de arquivos");if(ctx.getFilesDir().getFreeSpace()<expectedSize+200000000L)throw new IOException("Armazenamento insuficiente para esse modelo");model.getParentFile().mkdirs();File temp=new File(model.getPath()+".part");
  URL url=new URL(spec.getString("url"));
  HttpURLConnection c=null;
  try{
   for(int i=0;i<6;i++){if(!url.getProtocol().equals("https"))throw new IOException("Download exige HTTPS");c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(30000);c.setReadTimeout(60000);int code=c.getResponseCode();if(code==200)break;if(code>=300&&code<400){String loc=c.getHeaderField("Location");c.disconnect();url=new URL(url,loc);continue;}throw new IOException("Download HTTP "+code);}
   if(c==null||c.getResponseCode()!=200)throw new IOException("Download não iniciado");
   long count=0;try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){count+=n;if(count>expectedSize)throw new IOException("Tamanho de modelo inesperado");out.write(b,0,n);status="Baixando modelo: "+(count*100/expectedSize)+"%";}out.getFD().sync();}
   if(temp.length()!=expectedSize)throw new IOException("Download incompleto");java.nio.file.Files.move(temp.toPath(),model.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);verified=false;ensure();
  }finally{if(c!=null)c.disconnect();if(temp.exists())temp.delete();}
 }
}
