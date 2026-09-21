package com.g4t0xx.forge;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.View;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.zip.*;

public class MainActivity extends Activity {
 private WebView web; private Vault vault;
 private final java.util.concurrent.ExecutorService pool=Executors.newFixedThreadPool(4);
 private String pendingArtifact="";
 private static final String ORIGIN="https://forge.local/";
 @Override public void onCreate(Bundle state) {
  super.onCreate(state); vault=new Vault(this);
  if(BuildConfig.DEBUG) try {
   File bootstrap=new File(getFilesDir(),"bootstrap.json");
   if(bootstrap.exists()) {
    JSONObject b=new JSONObject(new String(java.nio.file.Files.readAllBytes(bootstrap.toPath()),StandardCharsets.UTF_8));
    for(String k:new String[]{"github","vercel","supabase"}) if(b.has(k)) vault.put(k,b.getString(k));
    getSharedPreferences("settings",0).edit().putString("hub",b.optString("hub","GABRIELMSORENSEN/Forge-Mobile")).apply();
    if(!bootstrap.delete())throw new IOException("Falha removendo configuração inicial");
   }
  } catch(Exception e) { throw new IllegalStateException("Falha na configuração inicial",e); }
  getWindow().setStatusBarColor(0xff101917); getWindow().setNavigationBarColor(0xff101917);
  web=new WebView(this); web.setBackgroundColor(0xff101917); setContentView(web);
  web.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
  web.addJavascriptInterface(new Bridge(),"Native");
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
    String u=req.getUrl().toString();
    if(u.startsWith(ORIGIN)) try {
     String p=req.getUrl().getPath(); if(p.equals("/"))p="/index.html";
     if(p.contains(".."))throw new IOException();
     String mime=p.endsWith(".js")?"application/javascript":p.endsWith(".css")?"text/css":"text/html";
     return new WebResourceResponse(mime,"UTF-8",getAssets().open(p.substring(1)));
    } catch(Exception ignored) {}
    return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
   }
  });
  web.loadUrl(ORIGIN);
 }
 private String hub() { return getSharedPreferences("settings",0).getString("hub","GABRIELMSORENSEN/Forge-Mobile"); }
 private String repo(String r) throws Exception { if(!r.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new Exception("Repositório inválido: use dono/nome"); return r; }
 private static byte[] read(InputStream in,int limit) throws IOException {
  try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
   byte[] b=new byte[8192];int n;while((n=s.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Resposta excede o limite");out.write(b,0,n);}return out.toByteArray();
  }
 }
 private HttpURLConnection connect(String url,String token,String method,String body) throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(60000);c.setRequestMethod(method);
  c.setRequestProperty("Accept","application/vnd.github+json"); c.setRequestProperty("User-Agent","Forge-Mobile/1.0");
  if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
  if(body!=null){c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
  return c;
 }
 private String request(String service,String path,String method,JSONObject body) throws Exception {
  String base=service.equals("github")?"https://api.github.com":service.equals("vercel")?"https://api.vercel.com":"https://api.supabase.com";
  String token=vault.get(service); if(token.isEmpty())throw new Exception("Conecte "+service+" em Conexões");
  HttpURLConnection c=connect(base+path,token,method,body==null?null:body.toString());
  try {
   int status=c.getResponseCode();
   String result=new String(read(status<400?c.getInputStream():c.getErrorStream(),8*1024*1024),StandardCharsets.UTF_8);
   if(status>=300){String detail="";try{JSONObject j=new JSONObject(result);detail=j.optString("message",j.optString("error"));}catch(Exception ignored){}throw new Exception(service+" HTTP "+status+(detail.isEmpty()?"":": "+detail));}
   return result.isEmpty()?"{}":result;
  }finally{c.disconnect();}
 }
 private String gh(String path,String method,JSONObject body)throws Exception{return request("github",path,method,body);}
 private JSONObject config() throws Exception {
  JSONObject c=new JSONObject(); c.put("hub",hub());
  for(String k:new String[]{"github","vercel","supabase"})c.put(k,!vault.get(k).isEmpty());
  c.put("device",android.os.Build.MODEL); c.put("version",BuildConfig.VERSION_NAME); return c;
 }
 private InputStream artifact(String id)throws Exception {
  if(!id.matches("[0-9]+"))throw new Exception("Artefato inválido");
  HttpURLConnection c=connect("https://api.github.com/repos/"+repo(hub())+"/actions/artifacts/"+id+"/zip",vault.get("github"),"GET",null);
  int status=c.getResponseCode();
  if(status==302){String next=c.getHeaderField("Location");c.disconnect();URL u=new URL(next);String host=u.getHost();
   if(!u.getProtocol().equals("https")||!(host.endsWith(".blob.core.windows.net")||host.endsWith(".githubusercontent.com")||host.endsWith(".actions.githubusercontent.com")))throw new Exception("Destino de download inesperado");
   c=connect(next,"","GET",null);status=c.getResponseCode();
  }
  if(status!=200){c.disconnect();throw new Exception("Download HTTP "+status);}
  final HttpURLConnection connection=c;
  return new FilterInputStream(c.getInputStream()){@Override public void close()throws IOException{try{super.close();}finally{connection.disconnect();}}};
 }
 private Object execute(String op,JSONObject a)throws Exception {
  String base="/repos/"+repo(hub());
  switch(op){
   case "config":return config();
   case "save":{
    if(a.has("hub")){String h=repo(a.getString("hub"));JSONObject r=new JSONObject(gh("/repos/"+h,"GET",null));if(!r.optBoolean("private"))throw new Exception("O centro de execução precisa ser privado");getSharedPreferences("settings",0).edit().putString("hub",h).commit();}
    for(String k:new String[]{"github","vercel","supabase"})if(a.has(k))vault.put(k,a.getString(k).trim());
    return config();
   }
   case "logout":vault.clear();return config();
   case "account":return new JSONObject(gh("/user","GET",null));
   case "repos":{
    JSONArray all=new JSONArray();for(int page=1;page<=10;page++){JSONArray list=new JSONArray(gh("/user/repos?per_page=100&sort=updated&page="+page,"GET",null));for(int i=0;i<list.length();i++)all.put(list.get(i));if(list.length()<100)break;}return all;
   }
   case "createRepo":{
    String name=a.getString("name");if(!name.matches("[A-Za-z0-9_.-]{1,100}"))throw new Exception("Nome inválido");
    return new JSONObject(gh("/user/repos","POST",new JSONObject().put("name",name).put("private",true).put("auto_init",true)));
   }
   case "runs":return new JSONObject(gh(base+"/actions/workflows/remote.yml/runs?per_page=30","GET",null));
   case "dispatch":{
    String runner=a.optString("runner","ubuntu-latest"),mode=a.optString("mode","agent");
    if(!runner.equals("ubuntu-latest")&&!runner.equals("windows-latest"))throw new Exception("Ambiente inválido");
    if(!java.util.Arrays.asList("agent","shell","diagnostic").contains(mode))throw new Exception("Modo inválido");
    String task=a.getString("task").trim();if(task.isEmpty()||task.length()>24000)throw new Exception("Tarefa deve ter de 1 a 24000 caracteres");
    JSONObject inputs=new JSONObject().put("task",task).put("target",repo(a.getString("target"))).put("runner",runner).put("mode",mode).put("request_id",a.optString("request_id",java.util.UUID.randomUUID().toString())).put("ref",a.optString("ref","")).put("model",a.optString("model",""));
    gh(base+"/actions/workflows/remote.yml/dispatches","POST",new JSONObject().put("ref","main").put("inputs",inputs));return new JSONObject().put("queued",true);
   }
   case "run":{long id=a.getLong("id");JSONObject r=new JSONObject(gh(base+"/actions/runs/"+id,"GET",null));r.put("jobs",new JSONObject(gh(base+"/actions/runs/"+id+"/jobs","GET",null)).getJSONArray("jobs"));r.put("artifacts",new JSONObject(gh(base+"/actions/runs/"+id+"/artifacts","GET",null)).getJSONArray("artifacts"));return r;}
   case "cancel":return new JSONObject(gh(base+"/actions/runs/"+a.getLong("id")+"/cancel","POST",new JSONObject()));
   case "result":{
    try(ZipInputStream z=new ZipInputStream(artifact(a.getString("id")))){ZipEntry e;int entries=0;while((e=z.getNextEntry())!=null&&entries++<100){if(e.getName().equals("result.json"))return new JSONObject(new String(readEntry(z,2*1024*1024),StandardCharsets.UTF_8));}}
    throw new Exception("Resultado não encontrado no artefato");
   }
   case "download":{
    pendingArtifact=a.getString("id");if(!pendingArtifact.matches("[0-9]+"))throw new Exception("Artefato inválido");
    runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"forge-"+pendingArtifact+".zip");startActivityForResult(i,21);});return new JSONObject().put("selectDestination",true);
   }
   case "publicKey":return new JSONObject(gh(base+"/actions/secrets/public-key","GET",null));
   case "secret":{
    String name=a.getString("name");if(!java.util.Arrays.asList("OPENAI_API_KEY","VERCEL_TOKEN","SUPABASE_ACCESS_TOKEN").contains(name))throw new Exception("Segredo não permitido");
    return new JSONObject(gh(base+"/actions/secrets/"+name,"PUT",new JSONObject().put("encrypted_value",a.getString("encrypted_value")).put("key_id",a.getString("key_id"))));
   }
   case "secrets":return new JSONObject(gh(base+"/actions/secrets","GET",null));
   case "vercel":return new JSONObject(request("vercel","/v9/projects?limit=100","GET",null));
   case "supabase":return new JSONArray(request("supabase","/v1/projects","GET",null));
   case "open":{
    String url=a.getString("url");Uri uri=Uri.parse(url);String h=uri.getHost();
    if(!"https".equals(uri.getScheme())||h==null||!(h.equals("github.com")||h.equals("vercel.com")||h.equals("supabase.com")||h.equals("platform.openai.com")||h.equals("chatgpt.com")))throw new Exception("Link não permitido");
    runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,uri)));return new JSONObject();
   }
   default:throw new Exception("Operação desconhecida");
  }
 }
 private static byte[] readEntry(InputStream in,int limit)throws IOException{
  ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(o.size()+n>limit)throw new IOException("Resultado muito grande");o.write(b,0,n);}return o.toByteArray();
 }
 public class Bridge {
  @JavascriptInterface public void call(String id,String op,String args){
   pool.execute(()->{JSONObject response=new JSONObject();try{response.put("value",execute(op,new JSONObject(args)));}catch(Exception e){try{response.put("error",e.getMessage()==null?"Falha na operação":e.getMessage());}catch(Exception ignored){}}
    String js="window.nativeReply("+JSONObject.quote(id)+","+response+")";runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(js,null);});
   });
  }
 }
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode==21&&resultCode==RESULT_OK&&data!=null){String id=pendingArtifact;Uri uri=data.getData();pool.execute(()->{
   String msg="Arquivo salvo";
   try(InputStream in=artifact(id);OutputStream out=getContentResolver().openOutputStream(uri)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}catch(Exception e){msg="Download falhou: "+e.getMessage();}
   String result=msg;runOnUiThread(()->android.widget.Toast.makeText(this,result,android.widget.Toast.LENGTH_LONG).show());
  });}
 }
 @Override protected void onDestroy(){web.removeJavascriptInterface("Native");web.destroy();pool.shutdownNow();super.onDestroy();}
}
