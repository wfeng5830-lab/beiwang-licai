package cn.dailyledger.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.*;

/** Consent-based screen sharing. The display surface is detached while idle. */
public final class BillScanService extends Service {
    private static volatile BillScanService instance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private WindowManager manager;
    private LinearLayout overlay;
    private WindowManager.LayoutParams params;
    private Button scanButton;
    private int width,height,density,generation;
    private boolean busy,wantFrame,destroyed,visible=true;
    private String captureMonth;
    public static boolean connected(){return instance!=null&&instance.projection!=null;}
    public static boolean floating(){return connected();}
    public static void hideFloating(){BillScanService s=instance;if(s!=null)s.main.post(s::stopSelf);}
    private final MediaProjection.Callback callback=new MediaProjection.Callback(){
        @Override public void onStop(){stopSelf();}
        @Override public void onCapturedContentResize(int w,int h){if(w>0&&h>0&&!destroyed)resize(w,h);}
        @Override public void onCapturedContentVisibilityChanged(boolean isVisible){visible=isVisible;if(!visible&&busy)cancelScan("共享页面已隐藏，请回到账单页面");}
    };
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(projection!=null)return START_NOT_STICKY;
        try{
            manager=(WindowManager)getSystemService(WINDOW_SERVICE);
            NotificationManager notifications=getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel("screen_scan","屏幕扫描",NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop=PendingIntent.getService(this,31,new Intent(this,BillScanService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent back=PendingIntent.getActivity(this,32,new Intent(this,MainActivity.class).putExtra("open_scans",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification=new Notification.Builder(this,"screen_scan").setSmallIcon(cn.dailyledger.app.R.drawable.ic_launcher).setContentTitle("账单扫描已开启").setContentText("仅点击时识别，点此返回账本").setContentIntent(back).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止扫描",stop).build()).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(12,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(12,notification);
            Intent grant=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("grant",Intent.class):intent.getParcelableExtra("grant");
            if(grant==null)throw new IllegalStateException("缺少屏幕共享授权，请重新开启扫描");
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK,grant);
            if(projection==null)throw new IllegalStateException("屏幕共享授权已失效");
            projection.registerCallback(callback,main);
            density=getResources().getConfiguration().densityDpi;
            if(Build.VERSION.SDK_INT>=30){Rect bounds=manager.getMaximumWindowMetrics().getBounds();width=bounds.width();height=bounds.height();}else{DisplayMetrics metrics=new DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(metrics);width=metrics.widthPixels;height=metrics.heightPixels;}
            createReader();
            // One virtual display per consent token; a detached surface collects no idle images.
            display=projection.createVirtualDisplay("LedgerScan",width,height,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,null,null,main);
            instance=this;showOverlay();
        }catch(Exception e){message("无法开启扫描："+(e.getMessage()==null?"请重新授权":e.getMessage()));stopSelf();}
        return START_NOT_STICKY;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String month(){return getSharedPreferences("scanner",MODE_PRIVATE).getString("month",LocalDate.now().toString().substring(0,7));}
    private Button button(String text,int w){Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setTextColor(0xff176a59);b.setAllCaps(false);b.setPadding(0,0,0,0);b.setMinimumWidth(0);b.setMinWidth(0);b.setMinimumHeight(0);b.setMinHeight(0);b.setBackgroundColor(Color.TRANSPARENT);b.setLayoutParams(new LinearLayout.LayoutParams(dp(w),dp(42)));return b;}
    private void showOverlay(){
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.HORIZONTAL);overlay.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xf2edf7f3);bg.setCornerRadius(dp(20));bg.setStroke(dp(1),0xff87b7a7);overlay.setBackground(bg);overlay.setElevation(dp(4));
        TextView handle=new TextView(this);handle.setText("⠿");handle.setTextSize(20);handle.setTextColor(0xff176a59);handle.setGravity(Gravity.CENTER);handle.setContentDescription("拖动悬浮窗");overlay.addView(handle,new LinearLayout.LayoutParams(dp(22),dp(42)));
        scanButton=button("扫描",46);scanButton.setOnClickListener(v->scan());overlay.addView(scanButton);
        Button back=button("账本",42);back.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_scans",true));stopSelf();});overlay.addView(back);
        Button close=button("×",30);close.setTextSize(21);close.setContentDescription("停止扫描并关闭悬浮窗");close.setOnClickListener(v->stopSelf());overlay.addView(close);
        params=new WindowManager.LayoutParams(dp(140),dp(44),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;params.x=dp(10);params.y=dp(130);
        handle.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:x=e.getRawX();y=e.getRawY();px=params.x;py=params.y;return true;case MotionEvent.ACTION_MOVE:params.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-dp(140),px+(int)(e.getRawX()-x)));params.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-dp(80),py+(int)(e.getRawY()-y)));if(overlay!=null)manager.updateViewLayout(overlay,params);return true;case MotionEvent.ACTION_UP:v.performClick();return true;default:return false;}}});
        manager.addView(overlay,params);
    }
    private void createReader(){reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);reader.setOnImageAvailableListener(this::readFrame,main);}
    private void resize(int w,int h){if(w==width&&h==height)return;if(busy)cancelScan("屏幕尺寸改变，请重新扫描");width=w;height=h;if(display!=null){display.setSurface(null);display.resize(w,h,density);}if(reader!=null)reader.close();createReader();}
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);if(Build.VERSION.SDK_INT<34&&manager!=null){DisplayMetrics metrics=new DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(metrics);resize(metrics.widthPixels,metrics.heightPixels);}}
    private void scan(){
        if(busy||projection==null||display==null)return;if(!visible){message("请显示正在共享的账单页面");return;}
        busy=true;captureMonth=month();int token=++generation;scanButton.setEnabled(false);overlay.setVisibility(View.INVISIBLE);
        main.postDelayed(()->{if(destroyed||token!=generation)return;try{Image stale;while((stale=reader.acquireLatestImage())!=null)stale.close();wantFrame=true;display.setSurface(reader.getSurface());}catch(RuntimeException e){cancelScan("无法读取屏幕，请停止后重新授权");}},250);
        main.postDelayed(()->{if(token==generation&&busy)cancelScan("扫描超时，请保持账单页面静止后重试");},20000);
    }
    private void readFrame(ImageReader source){
        Image image=null;Bitmap padded=null,bitmap=null;
        try{
            image=source.acquireLatestImage();if(image==null)return;
            if(!wantFrame||destroyed||source!=reader)return;wantFrame=false;display.setSurface(null);
            Image.Plane plane=image.getPlanes()[0];int stride=plane.getPixelStride();if(stride!=4)throw new IllegalStateException("不支持的屏幕像素格式");
            int rowWidth=plane.getRowStride()/stride;padded=Bitmap.createBitmap(rowWidth,image.getHeight(),Bitmap.Config.ARGB_8888);ByteBuffer buffer=plane.getBuffer();
            // Some devices omit the unused padding after the last image row.
            int required=padded.getByteCount();
            if(buffer.remaining()<required){
                int minimum=plane.getRowStride()*(image.getHeight()-1)+image.getWidth()*stride;
                if(buffer.remaining()<minimum)throw new IllegalStateException("屏幕图像不完整");
                ByteBuffer complete=ByteBuffer.allocate(required);complete.put(buffer);complete.rewind();buffer=complete;
            }
            padded.copyPixelsFromBuffer(buffer);bitmap=Bitmap.createBitmap(padded,0,0,image.getWidth(),image.getHeight());if(bitmap!=padded)padded.recycle();padded=null;
            int token=generation;Bitmap captured=bitmap;bitmap=null;recognize(token,captured,captureMonth);
        }catch(RuntimeException e){cancelScan("读取屏幕失败，请重试或重新授权");}finally{if(image!=null)image.close();if(padded!=null)padded.recycle();if(bitmap!=null)bitmap.recycle();}
    }
    private void recognize(int token,Bitmap bitmap,String selectedMonth){
        try{
            if(recognizer==null)recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(text->{
                if(destroyed||token!=generation)return;
                try{
                    List<BillParser.Line> lines=new ArrayList<>();for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){Rect r=line.getBoundingBox();if(r!=null)lines.add(new BillParser.Line(line.getText(),r.left,r.top,r.right,r.bottom));}
                    BillParser.Result parsed=BillParser.parseAuto(lines,selectedMonth);
                    if(!parsed.pageRecognized||parsed.bills.isEmpty()){finish(token,"未识别到完整支出，请显示账单顶部搜索栏、筛选栏并检查月份；受保护的黑屏无法扫描");return;}
                    JSONObject result=new LedgerStore(this).scan(parsed.bills);finish(token,"新增 "+result.optInt("added")+" 笔，重复 "+result.optInt("duplicates")+" 笔"+(result.optInt("conflicts")>0?"，疑似重复 "+result.optInt("conflicts")+" 笔":"")+(result.optInt("overflow")>0?"；队列已满，请先核对":""));
                }catch(Exception e){finish(token,"保存失败，请检查存储空间后重试");}
            }).addOnFailureListener(error->finish(token,"文字识别失败，请重试")).addOnCompleteListener(task->bitmap.recycle());
        }catch(RuntimeException e){bitmap.recycle();finish(token,"识别组件暂不可用，请重新开启扫描");}
    }
    private void finish(int token,String text){if(destroyed||token!=generation)return;busy=false;wantFrame=false;if(display!=null)display.setSurface(null);if(overlay!=null)overlay.setVisibility(View.VISIBLE);if(scanButton!=null)scanButton.setEnabled(true);message(text);}
    private void cancelScan(String text){int token=++generation;finish(token,text);}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onDestroy(){destroyed=true;generation++;wantFrame=false;if(instance==this)instance=null;if(overlay!=null){try{manager.removeView(overlay);}catch(RuntimeException ignored){}overlay=null;}if(display!=null){display.release();display=null;}if(reader!=null){reader.close();reader=null;}if(projection!=null){projection.unregisterCallback(callback);projection.stop();projection=null;}if(recognizer!=null)recognizer.close();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
