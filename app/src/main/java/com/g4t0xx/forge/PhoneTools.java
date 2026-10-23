package com.g4t0xx.forge;
import android.content.*;import android.app.*;import android.net.Uri;import android.os.*;import org.json.*;
final class PhoneTools {
 static PhoneTools instance;static synchronized PhoneTools get(Context c){if(instance==null)instance=new PhoneTools(c.getApplicationContext());return instance;}final Context ctx;PhoneTools(Context c){ctx=c;}
 JSONObject perform(String kind,JSONObject a)throws Exception{
  Intent i=null;
  switch(kind){
   case "camera":{MainActivity activity=MainActivity.current.get();if(activity==null)throw new Exception("Abra o Forge para usar a câmera");activity.openCamera();return Json.obj("opened","camera","note","Aguardando captura pelo usuário");}
   case "screen":case "tap":case "input_text":case "back":case "home":case "scroll":return PhoneAccessService.execute(kind,a);
   case "accessibility_settings":i=new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);break;
   case "open_app":i=ctx.getPackageManager().getLaunchIntentForPackage(a.getString("package"));if(i==null)throw new Exception("Aplicativo indisponível ou não visível ao Forge");break;
   case "open_url":{Uri u=Uri.parse(a.getString("url"));if(!"https".equals(u.getScheme()))throw new Exception("Use URL HTTPS");i=new Intent(Intent.ACTION_VIEW,u);break;}
   case "settings":i=new Intent(android.provider.Settings.ACTION_SETTINGS);break;
   case "calendar":i=new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI).putExtra(android.provider.CalendarContract.Events.TITLE,a.getString("title")).putExtra(android.provider.CalendarContract.Events.DESCRIPTION,a.optString("description")).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME,a.getLong("begin")).putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME,a.getLong("end"));break;
   case "alarm":i=new Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR,a.getInt("hour")).putExtra(android.provider.AlarmClock.EXTRA_MINUTES,a.getInt("minute")).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE,a.optString("label","Forge"));break;
   case "volume":{android.media.AudioManager m=ctx.getSystemService(android.media.AudioManager.class);int percent=Math.max(0,Math.min(100,a.getInt("percent")));m.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,m.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)*percent/100,0);return Json.obj("volume",percent);}
   case "flashlight":{android.hardware.camera2.CameraManager cm=ctx.getSystemService(android.hardware.camera2.CameraManager.class);for(String id:cm.getCameraIdList())if(Boolean.TRUE.equals(cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE))){cm.setTorchMode(id,a.getBoolean("enabled"));return Json.obj("flashlight",a.getBoolean("enabled"));}throw new Exception("Flash indisponível");}
   default:throw new Exception("Recurso Android desconhecido");
  }
  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);return Json.obj("opened",kind,"note","Tela do Android aberta; confirme no aplicativo quando solicitado. Não é prova de gravação do evento.");
 }
}
