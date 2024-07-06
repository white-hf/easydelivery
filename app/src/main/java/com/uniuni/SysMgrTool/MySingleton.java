package com.uniuni.SysMgrTool;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;
import androidx.preference.PreferenceManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.uniuni.SysMgrTool.Event.Publisher;
import com.uniuni.SysMgrTool.Response.DateTime;
import com.uniuni.SysMgrTool.Response.DeliveringListData;
import com.uniuni.SysMgrTool.Response.OrderDetailData;
import com.uniuni.SysMgrTool.Response.Path;
import com.uniuni.SysMgrTool.Task.MyHandler;
import com.uniuni.SysMgrTool.Task.TaskBase;
import com.uniuni.SysMgrTool.bean.ScanOrder;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;
import com.uniuni.SysMgrTool.dao.ScannedRecord;
import com.uniuni.SysMgrTool.manager.DeliveredPackagesMgr;
import com.uniuni.SysMgrTool.manager.DeliveryinfoMgr;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MySingleton extends Application {
    public static String ScanText ;
    public static int iScannedCount;

    public static final String ITEM_CURRENT_BATCH_ID = "current_batch_id";
    public static final String ITEM_AUTO_SAVE_SCAN   = "auto_save_scan";
    public static final String ITEM_AUTO_COMMIT      = "auto_commit";
    public static final String ITEM_DRIVER_ID        = "driver_id";
    public static final String ITEM_ANOTHER_DRIVER_ID    = "another_driver_id";
    public static final String ITEM_SYN_ORDERS_ID        = "syn_orders_id";
    public static final String ITEM_TRANSFER_PACKAGE_ID      = "transfer_package_id";

    private static MySingleton instance;
    private RequestQueue requestQueue;
    private com.uniuni.SysMgrTool.Event.Publisher publisher;
    private DeliveredPackagesMgr mDeliveredPackagesMgr;

    public static final String TAG = "MySingleton";

    public  Context getCtx() {
        return ctx;
    }

    private Context ctx;

    private ServerInterface mServerInterface;

    private HashMap<String, ScanOrder> mHashOrders;
    private HashMap<String , String> mHashArea = new HashMap<>();

    public Handler getMainHandler() {
        return myHandler;
    }

    private MyHandler myHandler;
    private MyDb mMydb = new MyDb();
    private LoginInfo mLoginInfo = new LoginInfo();

    public DeliveryinfoMgr getdDeliveryinfoMgr() {
        return dDeliveryinfoMgr;
    }
    public DeliveredPackagesMgr getmDeliveredPackagesMgr() {
        return mDeliveredPackagesMgr;
    }


    public  class LoginInfo{
        public String loginName = "5010";
        public Short loginId = 5010;
        public String userToken = "";
        public String loginLocation = "Halifax Warehouse";
        public Integer warehouseId = 17;
    }

    private DeliveryinfoMgr dDeliveryinfoMgr;

    public MySingleton() {

    }

    public Handler getDbHandler() {
        return mMydb.getHandler();
    }

     public MyDb getmMydb() {
        return mMydb;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this.getApplicationContext();
        requestQueue = getRequestQueue();

        CrashHandler handler = CrashHandler.getInstance();
        handler.init(this);

        mHashOrders = new HashMap<String , ScanOrder>();

        instance = this;
        iScannedCount = 0;

        myHandler = new  MyHandler(Looper.getMainLooper());
        mServerInterface = new ServerInterface(myHandler);

        publisher = new Publisher();

        mMydb.initDb(ctx);

        dDeliveryinfoMgr = new DeliveryinfoMgr();
        mDeliveredPackagesMgr = new DeliveredPackagesMgr();
        FileLog.getInstance().init();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        shutdownExecutorService();
    }

    private void shutdownExecutorService() {
        final ExecutorService executorService = mDeliveredPackagesMgr.getExecutorService();
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        System.err.println("ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public Publisher getPublisher()
    {
        return publisher;
    }

    public void sendMessage(Message m)
    {
        myHandler.sendMessage(m);
    }

    public void onStop()
    {
        FileLog.getInstance().close();
    }

    public LoginInfo getLoginInfo()
    {
        return mLoginInfo;
    }

    public final ServerInterface getServerInterface()
    {
        return mServerInterface;
    }

    public String getProperty(String item)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.getString(item,"");
    }

    public boolean getBooleanProperty(String item) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.getBoolean(item, false);
    }

    public Integer getIntProperty(String item) {
        Integer de = 0;
        if (item == null || item.isEmpty())
            return de;

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            String s = settings.getString(item,"");
            return Integer.valueOf(s);
        }catch (Exception e)
        {
            e.printStackTrace();
            return de;
        }
    }

    public void addScanOrder(String id, ScanOrder o)
    {
        mHashOrders.put(id , o);
    }

    public void saveOrderIds() {
        for (Map.Entry<String, ScanOrder> entry : mHashOrders.entrySet()) {
            ScanOrder o = entry.getValue();
            try {
                mMydb.saveOrderIds(o);
            }catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public String getOrderIdByPickId(Integer pickId)
    {
        if (pickId == null)
            return null;

        for (Map.Entry<String , ScanOrder> entry : mHashOrders.entrySet())
        {
            ScanOrder o = entry.getValue();
            if (o.getPackId().equals(pickId))
                return o.getId();
        }

        return null;
    }

    public void clearScanOrder()
    {
        mHashOrders.clear();
    }

    public ScanOrder findScanOrder(String tId)
    {
        ScanOrder scanOrder = mHashOrders.get(tId);
        return scanOrder;
    }

    public int getOrderCount()
    {
        return mHashOrders.size();
    }

    public static synchronized MySingleton getInstance() {
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    public <T> Request<T> addToRequestQueue(Request<T> req) {
        req.setShouldRetryConnectionErrors(false);
        req.setShouldRetryServerErrors(false);
        return getRequestQueue().add(req);
    }

    /**
     * 根据邮编查询所在区域中文
     * @param zip 邮编格式 B4b 1V1
     * @return
     */
    public String getAreaByPostCode(String zip)
    {
        if (zip == null || zip.isEmpty())
            return "";
        else {
            String k = zip;
            final int i = zip.indexOf(" ");
            if ( i != -1)
                k = zip.substring(0 , i);

            String v = mHashArea.get(k);
            if (v == null || v.isEmpty())
                return "未知区域或非哈法";
            else
                return v;
        }
    }

    public String formatScannedOrderInfo(ScannedRecord ordersOfDetail) {
        if (ordersOfDetail == null)
            return "";
        StringBuffer sb = new StringBuffer();

        sb.append("单号：");
        sb.append(ordersOfDetail.tid);
        sb.append("\n");

        sb.append("包裹号：");
        sb.append(ordersOfDetail.pickId);
        sb.append(" ");

        sb.append("ID：");
        sb.append(ordersOfDetail.orderId);
        sb.append(" ");

        sb.append("Order Sn：");
        sb.append(ordersOfDetail.orderSn);
        sb.append("");

        return sb.toString();
    }

    public String formatOrderDetailInfo(OrderDetailData ordersOfDetail)
    {
        if (ordersOfDetail == null)
            return "";

        StringBuffer sb = new StringBuffer();

        sb.append("单号：");
        sb.append(ordersOfDetail.getOrders().getTno());
        sb.append("\n");

        sb.append("包裹号：");
        sb.append(ordersOfDetail.getOrders().getPack_id());
        sb.append("\n");

        sb.append("库存号：");
        sb.append(ordersOfDetail.getTracking().getStorage_info());
        sb.append("\n");

        sb.append("司机号：");
        sb.append(ordersOfDetail.getOrders().getDriver_name());
        sb.append("\n");

        sb.append("邮编：");
        String area = MySingleton.getInstance().getAreaByPostCode(ordersOfDetail.getTracking().getZip());
        sb.append(ordersOfDetail.getTracking().getZip() + "|" + area);

        sb.append("\n");

        sb.append("批次号：");
        sb.append(ordersOfDetail.getOrders().getSub_referer());
        sb.append("\n");

        sb.append("地址：");
        sb.append(ordersOfDetail.getOrders().getAddress());
        sb.append("\n");


        final List<Path> path1 = ordersOfDetail.getPath();
        if (path1 != null && !path1.isEmpty())
        {
            sb.append("最后轨迹：");
            Path p = path1.get(path1.size() - 1);
            DateTime dt = p.getDateTime();

            String strD = formatDate(p.getPathTime());
            if (dt != null && dt.getLocalTime() != null)
            {
                strD = dt.getLocalTime();
            }

            sb.append(ordersOfDetail.getOrders().getLatest_status());
            sb.append("|");

            sb.append(p.getPathInfo() + "|" + p.getPathAddr() + "|" + p.getStaff_id() + "|" + strD);


            sb.append("\n");
        }


        String mMsg =  sb.toString();

        return mMsg;
    }

    private String formatDate(long d)
    {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = new Date(d*1000);
        return df.format(date);
    }

    public String formatDate(Date d)
    {
        if (d == null)
            return "";

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(d);
    }


    public void showToastInThread(final Activity context , String msg)
    {
        if ("main".equals(Thread.currentThread().getName())){
            Toast.makeText(context, msg , Toast.LENGTH_SHORT).show();
        }else
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
            });
    }

    public void showMsg(String msg)
    {
        new  AlertDialog.Builder(ctx)
                .setMessage(msg)
                .setPositiveButton("确定" ,  null )
                .show();
    }

    public void playRing() {
        try {
            Uri ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(ctx, ringUri);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            mMediaPlayer.setLooping(false);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isNumeric(String str){
        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(str);
        if( !isNum.matches() ){
            return false;
        }  return true;
    }
}
