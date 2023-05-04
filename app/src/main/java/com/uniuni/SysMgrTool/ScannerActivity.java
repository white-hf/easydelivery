package com.uniuni.SysMgrTool;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.uniuni.SysMgrTool.bean.ScanOrder;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.dao.ScannedRecord;
import com.uniuni.SysMgrTool.dao.ScannedRecordDao;
import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.Date;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

import android.os.Vibrator;


public class ScannerActivity extends AppCompatActivity implements ZXingScannerView.ResultHandler {

    private ZXingScannerView mScannerView;

    private TextView mTextPackId;
    private TextView mTextScanSummary;
    private RecyclerView mRecyclerBarCodeList;

    private Vibrator mVibrator;
    private MyHandler mMyhandler;
    private String mStrLastScannedValue = "";
    private Long mLastScannedTime;

    private Integer mDriverId;
    private Integer mAnotherDriverId;

    @SuppressLint("MissingInflatedId")
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        mMyhandler = new MyHandler(getMainLooper() , getSupportFragmentManager());
        setContentView(R.layout.activity_scanner);

        mMyhandler.setScannerActivity(this);

        mTextPackId = (TextView)findViewById(R.id.textPackId);
        mTextScanSummary= (TextView)findViewById(R.id.textViewScanSummary);
        mRecyclerBarCodeList = (RecyclerView)findViewById(R.id.recyclerBarCodeList);
        mRecyclerBarCodeList.setVisibility(View.GONE);
        mTextScanSummary.setTextIsSelectable(true);

        // permission check
        String permission = Manifest.permission.CAMERA;
        int grant = ContextCompat.checkSelfPermission(this, permission);
        if (grant != PackageManager.PERMISSION_GRANTED) {
            String[] permission_list = new String[1];
            permission_list[0] = permission;
            ActivityCompat.requestPermissions(this, permission_list, 1);
        }

        ViewGroup contentFrame = (ViewGroup) findViewById(R.id.content_frame);
        mScannerView = new ZXingScannerView(this);
        contentFrame.addView(mScannerView);

        SetRecyclerView();
        InitToolbar();

        // 震动效果的系统服务
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        mbAutoSave   =  MySingleton.getInstance().getBooleanProperty(MySingleton.ITEM_AUTO_SAVE_SCAN);
        mbAutoCommit =  MySingleton.getInstance().getBooleanProperty(MySingleton.ITEM_AUTO_COMMIT);

        mAnotherDriverId = MySingleton.getInstance().getIntProperty(MySingleton.ITEM_ANOTHER_DRIVER_ID);
        mDriverId        = MySingleton.getInstance().getIntProperty(MySingleton.ITEM_DRIVER_ID);


        loadScanData();
        svrLooperThread.start();
    }

    private void loadScanData()
    {
        String currentBatchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);

        if (currentBatchId == null || currentBatchId.isEmpty())
            return;

        if (mDriverId == null || mDriverId < 1)
            return;

        MySingleton.getInstance().clearScanOrder();
        MySingleton.getInstance().getServerInterface().loadOrdersByDriver(currentBatchId , mDriverId,null);


        if (mAnotherDriverId == null || mAnotherDriverId < 1)
            return;

        MySingleton.getInstance().getServerInterface().loadOrdersByDriver(currentBatchId , mAnotherDriverId,null);
    }

    @Override
    protected void onStop()
    {
        mbAutoCommit = false;
        super.onStop();
    }

    private SwitchCompat mGetParcelInfoSwitch;
    private SwitchCompat mOperateSwith;
    private SwitchCompat mScanOParcelSwitch;

    private ArrayList<String> CodeArray = new ArrayList<String>();

    private boolean mbAutoSave = false;
    private boolean mbAutoCommit = false;


    private Thread svrLooperThread = new Thread () {

        //query uncommitted scanned data periodically, then commit to server.

        public void run() {

            while (mbAutoCommit) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                ScannedRecordDao dao = MySingleton.getInstance().getmMydb().getScannedRecordDao();
                String currentBatchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);

                if (currentBatchId == null || currentBatchId.isEmpty())
                    continue;

                if (mDriverId == null || mDriverId < 1)
                    continue;

                final ScannedRecord[] scannedRecords;

                try{
                    scannedRecords= dao.loadUnCommittedRecords(currentBatchId , mDriverId);
                }catch (Exception e)
                {
                    e.printStackTrace();
                    continue;
                }

                if (scannedRecords == null || scannedRecords.length == 0)
                    continue;

                FileLog.getInstance().writeLog(String.format("Ready to commit %d scanned data " , scannedRecords.length));

                ServerInterface svr = MySingleton.getInstance().getServerInterface();
                MyDb db = MySingleton.getInstance().getmMydb();

                Date currentDate = new Date();
                for(ScannedRecord r : scannedRecords)
                {
                    //update committed status in DB
                   r.isCommitted = 1;
                   r.committedDate = currentDate;

                   svr.getMyHandler().addReqContext(r.orderId , r);
                   svr.handleScanParcel(r.orderId , r.orderSn , false);

                   //Only when we get successful response, then update the DB.
                   //db.updateScannedData(r);
                    try {
                        //avoid too many requests to server in a while
                        sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    public void setScanSummary(String msg)
    {
        mTextScanSummary.setText(msg);
        vibrate();

        Toast.makeText(getApplicationContext(), "查询成功", Toast.LENGTH_SHORT).show();
    }

    public boolean isOperateModel()
    {
        return mOperateSwith.isChecked();
    }

    public void setScannedStatus(Long orderId, String orderSn)
    {
        if (!mScanOParcelSwitch.isChecked())
            return;

        MySingleton.getInstance().getServerInterface().handleScanParcel(orderId , orderSn , true);
    }

    private void InitToolbar()
    {
        /**

        androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar)findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.action_settings);
        toolbar.inflateMenu(R.menu.menu_main);

        toolbar.setOnMenuItemClickListener(new androidx.appcompat.widget.Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int menuItemId = item.getItemId();
                if (menuItemId == R.id.action_down) {
                    boolRealTime = Boolean.TRUE;
                }
                else if (menuItemId == R.id.action_up)
                    mBoolCommitScannedStatus = Boolean.TRUE;

                return true;
            }
        });
        */

        mGetParcelInfoSwitch = (SwitchCompat) findViewById(R.id.switch_query_svr);
        mScanOParcelSwitch = (SwitchCompat) findViewById(R.id.switch_scan_parcel);
        mOperateSwith = (SwitchCompat) findViewById(R.id.switch_operate);

        if (MySingleton.getInstance().getOrderCount() == 0)
            mGetParcelInfoSwitch.setChecked(true);
    }

    private void SetRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerBarCodeList);
        //recyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager rLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(rLayoutManager);

        RecyclerView.ItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(itemDecoration);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                final int fromPos = viewHolder.getAbsoluteAdapterPosition();
                final int toPos = target.getAbsoluteAdapterPosition();
                //adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);

                if(viewHolder != null){
                    TextView textView= viewHolder.itemView.findViewById(R.id.mtext);
                    Log.d("debug", "Value : " + textView.getText().toString());
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void vibrate()
    {
        long[] pattern = {200, 200 };
        mVibrator.vibrate(pattern, -1);
    }

    @Override
    public void handleResult(Result rawResult) {

            // Note:
            // * Wait 2 seconds to resume the preview.
            // * On older devices continuously stopping and resuming camera preview can result in freezing the app.
            // * I don't know why this is the case but I don't have the time to figure out.

            // mScannerView.stopCameraPreview();
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScannerView.resumeCameraPreview(ScannerActivity.this);
                }
            }, 10);

            if (mStrLastScannedValue.equalsIgnoreCase(rawResult.getText()) &&
                    System.currentTimeMillis()/1000L - mLastScannedTime < 5)
                return;

            boolean boolRealTime = mGetParcelInfoSwitch.isChecked();

            if (boolRealTime)
            {
                MySingleton.getInstance().getServerInterface().getOrderDetail(rawResult.getText() , mMyhandler);
                mScannerView.resumeCameraPreview(ScannerActivity.this);

                mStrLastScannedValue = rawResult.getText();
                mLastScannedTime = System.currentTimeMillis()/1000L;
                return;
            }

            ScanOrder scanOrder = MySingleton.getInstance().findScanOrder(rawResult.getText());
            if (scanOrder != null)
            {
                if(!CodeArray.contains(rawResult.getText())){
                    CodeArray.add(rawResult.getText());
                    // notify adapter
                }

                Log.d("debug", String.format("Scanned %s ok, pack id %d" , rawResult.getText() , scanOrder.getPackId()));

                mTextPackId.setTextColor(Color.RED);
                mTextPackId.setText(String.valueOf(scanOrder.getPackId()));

                //it's another driver's parcel, we need to take care of it.
                if (scanOrder.getDriverId().equals(mAnotherDriverId))
                {
                    mTextPackId.setTextColor(Color.BLUE);
                    MySingleton.getInstance().playRing();
                }

                if (!scanOrder.getScanned()) {
                    scanOrder.setScanned(Boolean.TRUE);
                    MySingleton.getInstance().iScannedCount++;

                    vibrate();

                    int allC = MySingleton.getInstance().getOrderCount();
                    mTextScanSummary.setText(String.format("Subtotal:%d，scanned:%d ，waiting:%d",
                            allC , MySingleton.getInstance().iScannedCount,
                            allC - MySingleton.getInstance().iScannedCount));
                    int iLastStatus = scanOrder.getLastStatus();
                    if(mbAutoSave)
                    {
                        if(iLastStatus == ServerInterface.ParcelStatus.GATEWAY_TRANSIT_OUT.getValue()||
                                iLastStatus == ServerInterface.ParcelStatus.GATEWAY_TRANSIT.getValue())
                            MySingleton.getInstance().getmMydb().saveScannedData(scanOrder);
                        else {
                            Toast.makeText(getApplicationContext(),R.string.str_not_scan_status,Toast.LENGTH_SHORT).show();
                            FileLog.getInstance().writeLog(String.format("unknown status %d when scanning,tid:%s" , iLastStatus,scanOrder.getId()));
                        }
                    }
                }

                mStrLastScannedValue = rawResult.getText();
                mLastScannedTime = System.currentTimeMillis()/1000L;

                this.setTitle(mStrLastScannedValue);
            }
            else
                Toast.makeText(getApplicationContext(), R.string.str_unknown_bar, Toast.LENGTH_SHORT).show();

            mScannerView.resumeCameraPreview(ScannerActivity.this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }
}