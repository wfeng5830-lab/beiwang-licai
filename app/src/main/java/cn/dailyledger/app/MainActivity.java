package cn.dailyledger.app;

import android.app.Activity;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

public final class MainActivity extends Activity {
    private WebView web;
    private LedgerStore ledger;
    private boolean pendingLauncherEntry;
    private static final int EXPORT=10, IMPORT=11, CAPTURE=12;
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);ledger=new LedgerStore(this);pendingLauncherEntry=isLauncherIntent(getIntent());
        FrameLayout container=new FrameLayout(this);web=new WebView(this);container.addView(web);setContentView(container);
        container.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        // JavaScript is required by our bundled UI. All requests are allowlisted below;
        // external navigation, arbitrary files and network access are unavailable.
        web.setBackgroundColor(0xfff4f7f8);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){handleEntryIntent();}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString(),prefix="https://ledger.local/";
                if(url.startsWith(prefix)){
                    String name=url.substring(prefix.length());if(name.isEmpty())name="index.html";
                    if(Arrays.asList("index.html","style.css","core.js","app.js").contains(name)){
                        try{return new WebResourceResponse(name.endsWith(".css")?"text/css":name.endsWith(".js")?"application/javascript":"text/html","UTF-8",getAssets().open(name));}catch(Exception ignored){}
                    }
                }
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(new Bridge(),"AndroidLedger");
        web.loadUrl("https://ledger.local/index.html");
    }
    @Override protected void onResume(){super.onResume();DesktopShortcut.refreshAsync(this,false);if(web!=null)web.evaluateJavascript("window.refreshLedger && window.refreshLedger()",null);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);pendingLauncherEntry=isLauncherIntent(intent);handleEntryIntent();}
    private static boolean isLauncherIntent(Intent intent){return intent!=null&&(DesktopShortcut.ACTION_OPEN.equals(intent.getAction())||(Intent.ACTION_MAIN.equals(intent.getAction())&&intent.hasCategory(Intent.CATEGORY_LAUNCHER)));}
    private void handleEntryIntent(){
        if(web==null)return;
        final Intent entry=getIntent();
        if(entry.getBooleanExtra("open_scans",false)){
            pendingLauncherEntry=false;
            web.evaluateJavascript("window.openScanQueue && window.openScanQueue()",value->{if("true".equals(value)&&getIntent()==entry)entry.removeExtra("open_scans");});
        }else if(pendingLauncherEntry){
            // Keep the request pending until bundled JavaScript is ready on cold launch.
            web.evaluateJavascript("window.openLauncherHome && window.openLauncherHome()",value->{if("true".equals(value)&&getIntent()==entry)pendingLauncherEntry=false;});
        }
    }
    @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("AndroidLedger");web.destroy();}super.onDestroy();}
    private void jsNotice(String message){runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript("window.nativeNotice && window.nativeNotice("+JSONObject.quote(message)+")",null);});}
    private void refreshDesktopStatus(){runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript("window.refreshDesktopStatus && window.refreshDesktopStatus()",null);});}
    private final class Bridge {
        @JavascriptInterface public String read(){try{return ledger.read();}catch(Exception ignored){return "null";}}
        @JavascriptInterface public String mutate(String action,String payload){
            String result=ledger.mutate(action,payload);
            if(Arrays.asList("saveMemo","moveMemo","deleteMemo","importBundle").contains(action)){
                try{if(new JSONObject(result).optBoolean("ok"))DesktopShortcut.refreshAsync(MainActivity.this,false);}catch(Exception ignored){}
            }
            return result;
        }
        @JavascriptInterface public String desktopIconStatus(){return DesktopShortcut.status(MainActivity.this);}
        @JavascriptInterface public void addDesktopIcon(){DesktopShortcut.runAsync(()->{jsNotice(DesktopShortcut.requestPin(getApplicationContext()));refreshDesktopStatus();});}
        @JavascriptInterface public void refreshDesktopIcon(){DesktopShortcut.runAsync(()->{DesktopShortcut.refresh(getApplicationContext(),true);jsNotice("已尝试刷新，请查看桌面图标状态");refreshDesktopStatus();});}
        @JavascriptInterface public String scannerStatus(){try{return new JSONObject().put("enabled",Settings.canDrawOverlays(MainActivity.this)).put("connected",BillScanService.connected()).put("month",getSharedPreferences("scanner",MODE_PRIVATE).getString("month",LocalDate.now().toString().substring(0,7))).toString();}catch(Exception ignored){return "{}";}}
        @JavascriptInterface public void openOverlaySettings(){runOnUiThread(()->{try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){jsNotice("请在系统设置中允许日常账本显示在其他应用上层");}});}
        @JavascriptInterface public String startScanner(String month){
            try{
                if(!month.matches("20\\d\\d-(0[1-9]|1[0-2])"))throw new Exception("请选择正确的账单月份");
                if(!Settings.canDrawOverlays(MainActivity.this))throw new Exception("请先允许显示悬浮窗");
                if(!getSharedPreferences("scanner",MODE_PRIVATE).edit().putString("month",month).remove("channel").commit())throw new Exception("保存扫描设置失败");
                if(BillScanService.connected()){return "{\"ok\":true}";}
                runOnUiThread(()->{try{MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);Intent intent=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();startActivityForResult(intent,CAPTURE);}catch(Exception e){jsNotice("系统无法开启屏幕共享，请重试");}});
                return "{\"ok\":true}";
            }catch(Exception e){try{return new JSONObject().put("ok",false).put("error",e.getMessage()).toString();}catch(Exception ignored){return "{\"ok\":false}";}}
        }
        @JavascriptInterface public void stopScanner(){BillScanService.hideFloating();}
        @JavascriptInterface public void exportBackup(){runOnUiThread(()->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"日常账本-"+LocalDate.now()+".json");try{startActivityForResult(intent,EXPORT);}catch(Exception ignored){jsNotice("找不到系统文件选择器");}});}
        @JavascriptInterface public void importBackup(){runOnUiThread(()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");try{startActivityForResult(intent,IMPORT);}catch(Exception ignored){jsNotice("找不到系统文件选择器");}});}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==CAPTURE){
            if(result==RESULT_OK&&data!=null){try{startForegroundService(new Intent(this,BillScanService.class).putExtra("grant",data));}catch(Exception e){jsNotice("扫描服务开启失败，请重试");}}
            else jsNotice("已取消屏幕共享，未开启扫描");return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request!=EXPORT&&request!=IMPORT)return;
        new Thread(()->{try{
            if(request==EXPORT){try(OutputStream output=getContentResolver().openOutputStream(data.getData(),"wt")){if(output==null)throw new Exception();output.write(ledger.exportBackup().getBytes(StandardCharsets.UTF_8));}jsNotice("备份已导出");}
            else {String content;try(InputStream input=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream output=new ByteArrayOutputStream()){
                if(input==null)throw new Exception();byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>20000000)throw new Exception("备份不能超过 20 MB");output.write(buffer,0,count);}content=output.toString("UTF-8");
            }
            String script="window.receiveBackup("+JSONObject.quote(content)+")";runOnUiThread(()->web.evaluateJavascript(script,null));}
        }catch(Exception error){jsNotice(error.getMessage()==null?"文件操作失败，请重试":error.getMessage());}},"ledger-files").start();
    }
}
