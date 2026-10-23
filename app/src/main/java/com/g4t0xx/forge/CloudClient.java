package com.g4t0xx.forge;
import org.json.*;import java.io.*;import java.net.*;
/** Direct private credential use: no relay and no GitHub secret. */
final class CloudClient {
 final Vault vault;volatile boolean low;volatile String quota="Não informada pelo provedor";volatile JSONObject usage=new JSONObject();CloudClient(Vault v){vault=v;}
 String generate(String model,String system,String prompt)throws Exception{
  return generate(model,system,prompt,false);
 }
 private String generate(String model,String system,String prompt,boolean refreshed)throws Exception{
  String api=vault.get("openai"),raw=vault.get("codex_auth");boolean chat=api.isEmpty();JSONObject tokens=null;
  if(chat){if(raw.isEmpty())throw new IOException("Sessão Codex ou chave OpenAI não configurada neste Android");tokens=new JSONObject(raw).optJSONObject("tokens");if(tokens==null)throw new IOException("Sessão Codex inválida");}
  String endpoint=chat?"https://chatgpt.com/backend-api/codex/responses":"https://api.openai.com/v1/responses";
  HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(180000);c.setRequestMethod("POST");c.setRequestProperty("Authorization","Bearer "+(chat?tokens.getString("access_token"):api));c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","text/event-stream");c.setRequestProperty("User-Agent","codex_cli_rs/0.155.1 (Android Forge personal)");c.setRequestProperty("originator","codex_cli_rs");if(chat)c.setRequestProperty("ChatGPT-Account-ID",tokens.optString("account_id"));
  JSONObject body=Json.obj("model",model,"instructions",system,"input",new JSONArray().put(Json.obj("role","user","content",new JSONArray().put(Json.obj("type","input_text","text",prompt)))),"tools",new JSONArray(),"tool_choice","auto","parallel_tool_calls",false,"store",false,"stream",true,"include",new JSONArray());
  c.setDoOutput(true);try{try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}int code=c.getResponseCode();
   double used=-1;for(String h:new String[]{"x-codex-primary-used-percent","x-codex-secondary-used-percent"}){String s=c.getHeaderField(h);if(s!=null)try{used=Math.max(used,Double.parseDouble(s));}catch(Exception ignored){}}if(used>=0){quota="Maior janela usada: "+used+"%";low=used>=90;}
   if(code==401&&chat&&!refreshed){c.disconnect();AccountLogin.refresh(vault);return generate(model,system,prompt,true);}
   if(code>=300){String e=Json.read(c.getErrorStream(),16000);if(code==429||code==402)low=true;throw new IOException("Codex HTTP "+code+": "+e.substring(0,Math.min(600,e.length())));}
   StringBuilder answer=new StringBuilder();boolean completed=false;try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(!line.startsWith("data: "))continue;String data=line.substring(6);if(data.equals("[DONE]"))continue;JSONObject e=new JSONObject(data);String type=e.optString("type");if(type.equals("response.output_text.delta"))answer.append(e.optString("delta"));if(type.equals("response.completed"))completed=true;if(type.equals("error")||type.equals("response.failed"))throw new IOException("Provedor interrompeu a resposta: "+e.toString().substring(0,Math.min(500,e.toString().length())));if(answer.length()>100000)throw new IOException("Resposta muito grande");}}
   if(!completed||answer.length()==0)throw new IOException("Resposta online incompleta; contexto preservado para retomada");return answer.toString();
  }finally{c.disconnect();}
 }
}
