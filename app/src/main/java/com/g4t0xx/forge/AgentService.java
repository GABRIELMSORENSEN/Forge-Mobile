package com.g4t0xx.forge;
import android.app.*;import android.content.*;import android.os.*;
public final class AgentService extends Service {
 static final java.util.concurrent.atomic.AtomicInteger clients=new java.util.concurrent.atomic.AtomicInteger();
 static void begin(Context c){clients.incrementAndGet();c.startForegroundService(new Intent(c,AgentService.class));}
 static void finished(Context c){if(clients.updateAndGet(v->Math.max(0,v-1))==0)c.stopService(new Intent(c,AgentService.class));}
 public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"STOP".equals(i.getAction())){Assistant.get(this).stop();MediaStudio.get(this).stop();clients.set(0);stopSelf();return START_NOT_STICKY;}NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("agent","Assistente Forge",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,AgentService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);Notification n=new Notification.Builder(this,"agent").setSmallIcon(com.g4t0xx.forge.R.drawable.icon).setContentTitle("Forge está trabalhando").setContentText("Tarefa e relatório salvos no celular").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Pausar",stop).build()).build();startForeground(12,n);return START_NOT_STICKY;}
 public IBinder onBind(Intent i){return null;}
}
