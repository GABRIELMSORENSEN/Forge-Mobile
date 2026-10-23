package com.g4t0xx.forge;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
final class Json {
 static JSONObject obj(Object...args)throws JSONException{JSONObject j=new JSONObject();for(int i=0;i<args.length;i+=2)j.put(args[i].toString(),args[i+1]);return j;}
 static String read(InputStream s,int limit)throws IOException{try(InputStream in=s;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Resposta excede o limite");out.write(b,0,n);}return out.toString("UTF-8");}}
 static void atomic(File f,String value)throws IOException{
  File dir=f.getParentFile();if(!dir.exists()&&!dir.mkdirs())throw new IOException("Não foi possível criar pasta");
  File tmp=new File(dir,f.getName()+".new");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
  Files.move(tmp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
 }
 static String hash(byte[] data)throws Exception{StringBuilder b=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(data))b.append(String.format("%02x",v));return b.toString();}
 static JSONObject parseReply(String text)throws Exception{String s=text.trim();int start=s.indexOf('{'),end=s.lastIndexOf('}');if(start<0||end<start)throw new IOException("O agente não entregou uma ação estruturada");return new JSONObject(s.substring(start,end+1));}
}
