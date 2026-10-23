package com.g4t0xx.forge;
import android.graphics.*;import android.media.*;import java.io.*;import java.nio.*;
/** Small offline image-motion video, using the Android hardware encoder. */
final class LocalVideo {
 static void create(File source,File output,int width,int height,int frames,int fps)throws Exception{
  Bitmap original=BitmapFactory.decodeFile(source.getAbsolutePath());if(original==null)throw new IOException("Imagem inicial inválida");
  Bitmap frame=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(frame);int[] pixels=new int[width*height];
  MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);format.setInteger(MediaFormat.KEY_BIT_RATE,Math.max(300000,width*height*fps));format.setInteger(MediaFormat.KEY_FRAME_RATE,fps);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
  MediaCodec codec=MediaCodec.createEncoderByType("video/avc");MediaMuxer muxer=null;boolean started=false;int track=-1;
  try{codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();int sent=0;boolean eof=false;long deadline=System.currentTimeMillis()+180000;
   while(!eof){if(System.currentTimeMillis()>deadline)throw new IOException("Codificação excedeu o tempo");
    if(sent<=frames){int index=codec.dequeueInputBuffer(10000);if(index>=0){if(sent==frames){codec.queueInputBuffer(index,0,0,sent*1000000L/fps,MediaCodec.BUFFER_FLAG_END_OF_STREAM);sent++;}else{float progress=frames<2?0:(float)sent/(frames-1),scale=Math.max((float)width/original.getWidth(),(float)height/original.getHeight())*(1.03f+0.12f*progress);float w=original.getWidth()*scale,h=original.getHeight()*scale;canvas.drawColor(Color.BLACK);canvas.drawBitmap(original,null,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));frame.getPixels(pixels,0,width,0,0,width,height);Image image=codec.getInputImage(index);if(image==null)throw new IOException("Codificador não oferece entrada YUV");Image.Plane[] planes=image.getPlanes();for(int y=0;y<height;y++)for(int x=0;x<width;x++){int rgb=pixels[y*width+x],r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;put(planes[0],x,y,clamp(((66*r+129*g+25*b+128)>>8)+16));if((x&1)==0&&(y&1)==0){put(planes[1],x/2,y/2,clamp(((-38*r-74*g+112*b+128)>>8)+128));put(planes[2],x/2,y/2,clamp(((112*r-94*g-18*b+128)>>8)+128));}}image.close();codec.queueInputBuffer(index,0,width*height*3/2,sent*1000000L/fps,0);sent++;}}}
    int index=codec.dequeueOutputBuffer(info,10000);if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;}else if(index>=0){ByteBuffer buffer=codec.getOutputBuffer(index);if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;if(info.size>0){if(!started)throw new IOException("Formato de vídeo ausente");buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info);}eof=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);}
   }
  }finally{try{codec.stop();}catch(Exception ignored){}codec.release();if(muxer!=null){try{if(started)muxer.stop();}finally{muxer.release();}}original.recycle();frame.recycle();}
 }
 static int clamp(int x){return Math.max(0,Math.min(255,x));}
 static void put(Image.Plane p,int x,int y,int value){p.getBuffer().put(y*p.getRowStride()+x*p.getPixelStride(),(byte)value);}
}
