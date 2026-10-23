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
 static java.lang.ref.WeakReference<MainActivity> current=new java.lang.ref.WeakReference<>(null);
 private WebView web; private Vault vault;private Voice voice;private String pendingExport="";private String importKind="file";private String mediaExport="";
 private final java.util.concurrent.ExecutorService pool=Executors.newFixedThreadPool(4);
 private String pendingArtifact="";
 private static final String ORIGIN="https://forge.local/";
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);current=new java.lang.ref.WeakReference<>(this); vault=new Vault(this);
  if(BuildConfig.DEBUG) try {
   File bootstrap=new File(getFilesDir(),"bootstrap.json");
   if(bootstrap.exists()) {
    JSONObject b=new JSONObject(new String(java.nio.file.Files.readAllBytes(bootstrap.toPath()),StandardCharsets.UTF_8));
    for(String k:new String[]{"github","vercel","supabase","codex_auth","openai","pc_url","pc_token","pc_pin"}) if(b.has(k)) vault.put(k,b.getString(k));
    if(b.has("memory")){JSONArray memories=b.getJSONArray("memory");ChatStore store=Assistant.get(this).store;for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);store.remember(m.optString("id",java.util.UUID.randomUUID().toString()),m.getString("text"),m.optString("source","perfil-importado"));}}
    getSharedPreferences("settings",0).edit().putString("hub",b.optString("hub","GABRIELMSORENSEN/Forge-Mobile")).apply();
    if(!bootstrap.delete())throw new IOException("Falha removendo configuração inicial");
   }
  } catch(Exception e) { throw new IllegalStateException("Falha na configuração inicial",e); }
  getWindow().setStatusBarColor(0xff101917); getWindow().setNavigationBarColor(0xff101917);
  web=new WebView(this); web.setBackgroundColor(0xff101917);android.widget.FrameLayout root=new android.widget.FrameLayout(this);root.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop(),right=insets.getSystemWindowInsetRight(),bottom=insets.getSystemWindowInsetBottom();if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;}android.widget.FrameLayout.LayoutParams layout=(android.widget.FrameLayout.LayoutParams)web.getLayoutParams();layout.setMargins(left,top,right,bottom);web.setLayoutParams(layout);return insets;});root.requestApplyInsets();
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
     if(p.startsWith("/media/")){File f=MediaStudio.get(MainActivity.this).file(p.substring(7));return new WebResourceResponse(f.getName().endsWith(".mp4")?"video/mp4":f.getName().endsWith(".webm")?"video/webm":"image/png",null,new FileInputStream(f));}
     String mime=p.endsWith(".js")?"application/javascript":p.endsWith(".css")?"text/css":"text/html";
     return new WebResourceResponse(mime,"UTF-8",getAssets().open(p.substring(1)));
    } catch(Exception ignored) {}
    return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
   }
  });
  web.loadUrl(ORIGIN);
  voice=new Voice(this);
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
  if(op.equals("connections_save")){for(String k:new String[]{"github","vercel","supabase"})if(a.has(k))vault.put(k,a.getString(k).trim());return Json.obj("saved",true);}
  if(op.equals("connections_info"))return Json.obj("github",!vault.get("github").isEmpty(),"vercel",!vault.get("vercel").isEmpty(),"supabase",!vault.get("supabase").isEmpty());
  if(op.equals("media_info"))return MediaStudio.get(this).info();
  if(op.equals("media_generate"))return MediaStudio.get(this).submit(a);
  if(op.equals("media_install")){MediaStudio.get(this).install(a.getString("kind"));return new JSONObject().put("started",true);}
  if(op.equals("media_stop")){MediaStudio.get(this).stop();return new JSONObject().put("stopping",true);}
  if(op.equals("media_export")){mediaExport=a.getString("file");File f=MediaStudio.get(this).file(mediaExport);runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(f.getName().endsWith(".mp4")?"video/mp4":f.getName().endsWith(".webm")?"video/webm":"image/png").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,f.getName()),25));return new JSONObject();}
  if(op.startsWith("chat_")||op.startsWith("engine_")||op.equals("workspace")||op.equals("pc_status"))return Assistant.get(this).api(op,a);
  if(op.equals("voice_info"))return voice.info();
  if(op.equals("account_login")){AccountLogin.begin(this,vault);return new JSONObject().put("started",true);}
  if(op.equals("voice_listen")){voice.listen(a.optBoolean("wake"),a.optBoolean("offline",true));return new JSONObject();}
  if(op.equals("voice_speak")){voice.speak(a.getString("text"),a.optString("voice"),(float)a.optDouble("rate",1),a.optBoolean("offline",true));return new JSONObject();}
  if(op.equals("voice_stop")){voice.stop();return new JSONObject();}
  if(op.equals("voice_synthesize")){voice.synthesize(a.getString("text"),a.optString("voice"));return new JSONObject().put("started",true);}
  if(op.equals("phone_permissions")){runOnUiThread(()->requestPermissions(new String[]{android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO},32));return new JSONObject();}
  if(op.equals("phone_action")){if(!getSharedPreferences("assistant",0).getString("access","ask").equals("full"))throw new Exception("Escolha Acesso completo para usar os controles diretos");return PhoneTools.get(this).perform(a.getString("kind"),a.optJSONObject("args")==null?new JSONObject():a.getJSONObject("args"));}
  if(op.equals("phone_camera")){openCamera();return new JSONObject().put("opened",true);}
  if(op.equals("phone_import")){importKind=a.optString("kind","file");runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,22);});return new JSONObject();}
  if(op.equals("phone_export")){pendingExport=a.optString("name","forge-memory.json");runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,pendingExport);startActivityForResult(i,23);});return new JSONObject();}
  String base="/repos/"+repo(hub());
  switch(op){
   case "config":return config();
   case "save":{
    if(a.has("hub")){String h=repo(a.getString("hub"));JSONObject r=new JSONObject(gh("/repos/"+h,"GET",null));if(!r.optBoolean("private"))throw new Exception("O centro de execução precisa ser privado");getSharedPreferences("settings",0).edit().putString("hub",h).commit();}
    for(String k:new String[]{"github","vercel","supabase"})if(a.has(k)){
     String value=a.getString(k).trim();
     if(!value.isEmpty()){
      String url=k.equals("github")?"https://api.github.com/user":k.equals("vercel")?"https://api.vercel.com/v9/projects?limit=1":"https://api.supabase.com/v1/projects";
      HttpURLConnection check=connect(url,value,"GET",null);try{if(check.getResponseCode()!=200)throw new Exception("Credencial de "+k+" inválida ou sem acesso");}finally{check.disconnect();}
     }
     vault.put(k,value);
    }
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
   case "secret":throw new Exception("Credenciais pessoais ficam apenas neste aparelho. Publicação de segredos desativada.");
   case "secrets":return new JSONObject(gh(base+"/actions/secrets","GET",null));
   case "setup":{
    JSONObject r=new JSONObject(gh(base,"GET",null));if(!r.optBoolean("private"))throw new Exception("Use um centro de execução privado");
    String path=base+"/contents/.github/workflows/remote.yml";
    JSONObject body=new JSONObject().put("message","Enable Forge remote tasks").put("branch","main");
    try{body.put("sha",new JSONObject(gh(path,"GET",null)).getString("sha"));}catch(Exception e){if(!e.getMessage().contains("HTTP 404"))throw e;}
    String content=android.util.Base64.encodeToString(read(getAssets().open("remote.yml"),100000),android.util.Base64.NO_WRAP);
    body.put("content",content);return new JSONObject(gh(path,"PUT",body));
   }
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
  if(requestCode==25&&resultCode==RESULT_OK&&data!=null){Uri uri=data.getData();String name=mediaExport;pool.execute(()->{try(InputStream in=new FileInputStream(MediaStudio.get(this).file(name));OutputStream out=getContentResolver().openOutputStream(uri)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);emit("file",new JSONObject().put("message","Geração salva no destino escolhido"));}catch(Exception e){android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}});}
  if(requestCode==24&&resultCode==RESULT_OK&&data!=null&&data.getExtras()!=null){android.graphics.Bitmap bitmap=(android.graphics.Bitmap)data.getExtras().get("data");if(bitmap!=null)try{File file=new File(getFilesDir(),"workspace/camera-"+System.currentTimeMillis()+".jpg");file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,out);}emit("file",new JSONObject().put("message","Foto salva no espaço do Forge").put("path",file.getName()));}catch(Exception e){android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}
  if((requestCode==22||requestCode==23)&&resultCode==RESULT_OK&&data!=null){Uri uri=data.getData();pool.execute(()->{try{
   if(requestCode==23){try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(Assistant.get(this).store.exportData().toString(2).getBytes(StandardCharsets.UTF_8));}emit("file",new JSONObject().put("message","Memória e conversas exportadas"));}
   else{String name="arquivo-importado-"+System.currentTimeMillis();try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int col=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(col>=0)name=c.getString(col);}}name=new File(name).getName().replaceAll("[^\\p{L}\\p{N}._ -]","_");File dest=new File(getFilesDir(),importKind.equals("model")?"models/"+name:"workspace/"+name);dest.getParentFile().mkdirs();try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(importKind.equals("model"))getSharedPreferences("assistant",0).edit().putString("local_model",name).commit();emit("file",new JSONObject().put("message","Arquivo importado: "+name).put("path",name));}
  }catch(Exception e){try{emit("file",new JSONObject().put("error",e.getMessage()));}catch(Exception ignored){}}});}
  if(requestCode==21&&resultCode==RESULT_OK&&data!=null){String id=pendingArtifact;Uri uri=data.getData();pool.execute(()->{
   String msg="Arquivo salvo";
   try(InputStream in=artifact(id);OutputStream out=getContentResolver().openOutputStream(uri)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}catch(Exception e){msg="Download falhou: "+e.getMessage();}
   String result=msg;runOnUiThread(()->android.widget.Toast.makeText(this,result,android.widget.Toast.LENGTH_LONG).show());
  });}
 }
 void emit(String kind,JSONObject value){runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window.assistantEvent&&window.assistantEvent("+JSONObject.quote(kind)+","+value+")",null);});}
 void openCamera(){runOnUiThread(()->{try{startActivityForResult(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),24);}catch(Exception e){android.widget.Toast.makeText(this,"Câmera indisponível: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}});}
 @Override protected void onDestroy(){if(voice!=null)voice.close();web.removeJavascriptInterface("Native");web.destroy();pool.shutdownNow();super.onDestroy();}
}
