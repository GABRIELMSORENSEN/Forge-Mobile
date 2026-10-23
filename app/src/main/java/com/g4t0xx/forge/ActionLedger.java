package com.g4t0xx.forge;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Durable, idempotent file actions. Every write requires an observed prior hash. */
final class ActionLedger {
 final File workspace,ledgerFile; private JSONObject records;
 ActionLedger(File workspace,File ledgerFile)throws Exception{
  this.workspace=workspace.getCanonicalFile();this.ledgerFile=ledgerFile;workspace.mkdirs();
  records=ledgerFile.exists()?new JSONObject(Json.read(new FileInputStream(ledgerFile),4*1024*1024)):new JSONObject();
 }
 private File path(String name)throws Exception{
  if(name.isEmpty()||name.startsWith("/")||name.contains("\\")||name.contains(":"))throw new IOException("Caminho inválido");
  File p=new File(workspace,name).getCanonicalFile();if(!p.toPath().startsWith(workspace.toPath())||p.equals(workspace))throw new IOException("Arquivo fora do projeto");
  return p;
 }
 private void save()throws Exception{Json.atomic(ledgerFile,records.toString());}
 synchronized JSONObject files()throws Exception{
  JSONArray list=new JSONArray();walk(workspace,list);return Json.obj("files",list);
 }
 private void walk(File dir,JSONArray out)throws Exception{
  File[] entries=dir.listFiles();if(entries==null)return;Arrays.sort(entries,Comparator.comparing(File::getName));
  for(File f:entries){if(Files.isSymbolicLink(f.toPath()))continue;if(out.length()>200)break;if(f.isDirectory())walk(f,out);else if(f.length()<=512000)out.put(Json.obj("path",workspace.toPath().relativize(f.toPath()).toString().replace('\\','/'),"size",f.length(),"sha256",Json.hash(Files.readAllBytes(f.toPath()))));}
 }
 synchronized JSONObject perform(JSONObject action)throws Exception{
  String op=action.getString("action");
  if(op.equals("list_files"))return files();
  File f=path(action.getString("path"));
  if(op.equals("read_file")){if(!f.exists())return Json.obj("exists",false,"sha256","missing");if(f.length()>32000)throw new IOException("Arquivo muito grande para este modelo");byte[] b=Files.readAllBytes(f.toPath());return Json.obj("exists",true,"sha256",Json.hash(b),"content",new String(b,StandardCharsets.UTF_8));}
  if(!op.equals("write_file"))throw new IOException("Ferramenta não permitida: "+op);
  byte[] content=action.getString("content").getBytes(StandardCharsets.UTF_8);if(content.length>100000)throw new IOException("Arquivo excede 100 KB");
  String desired=Json.hash(content),name=workspace.toPath().relativize(f.toPath()).toString(),id=Json.hash((name+"\n"+desired).getBytes(StandardCharsets.UTF_8));
  String current=f.exists()?Json.hash(Files.readAllBytes(f.toPath())):"missing";
  if(current.equals(desired)){JSONObject result=Json.obj("path",name,"sha256",desired,"reused",true);records.put(id,Json.obj("status","done","result",result));save();return result;}
  JSONObject prior=records.optJSONObject(id);
  if(prior!=null&&prior.optString("status").equals("started"))throw new IOException("Escrita interrompida com estado incerto. Leia o arquivo antes de tentar outra alteração.");
  if(!action.optString("expected_sha256","missing").equals(current))throw new IOException("Arquivo mudou: leia novamente e use o sha256 atual antes de escrever.");
  records.put(id,Json.obj("status","started","path",name,"before",current,"after",desired));save();
  Json.atomic(f,new String(content,StandardCharsets.UTF_8));
  JSONObject result=Json.obj("path",name,"sha256",desired,"reused",false);
  records.put(id,Json.obj("status","done","result",result));save();return result;
 }
 synchronized JSONArray completed()throws Exception{JSONArray a=new JSONArray();Iterator<String> keys=records.keys();while(keys.hasNext()){JSONObject v=records.getJSONObject(keys.next());if(v.optString("status").equals("done"))a.put(v.getJSONObject("result"));}return a;}
}
