package com.uniuni.SysMgrTool;

import static com.uniuni.SysMgrTool.MySingleton.ITEM_SYN_ORDERS_ID;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.uniuni.SysMgrTool.Request.TransferPackagesReq;
import com.uniuni.SysMgrTool.View.MyAdapter;
import com.uniuni.SysMgrTool.View.ScannedDataFragment;
import com.uniuni.SysMgrTool.View.SettingsActivity;
import com.uniuni.SysMgrTool.dao.OrderIdRecord;
import com.uniuni.SysMgrTool.routeplanning.PlaceManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static MainActivity instance;

    private AppCompatButton btn_save;
    AppCompatButton  btn_order_detail;
    private String BarcodeValue,BarcodeType;
    private EditText mEditText;
    private EditText mPickId;
    private Spinner spinner;

    private MyHandler myHandler;

    private Bitmap bit;

    private ArrayList<String> mSearchOrder = new ArrayList<String>();
    private MyAdapter mSearchOrderAdapter;

    private ListView mLvSearchOrder;

    private AlertDialog mLoginDialog;
    private AlertDialog.Builder  mSystemOperationDialog;

    private boolean mBright = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        instance = this;

        setContentView(R.layout.activity_barcode);
        //btn_save = (AppCompatButton)findViewById(R.id.btn_)

        MySingleton.getInstance().ScanText = "";
        mPickId   = (EditText)findViewById(R.id.editTextPickNumber);

        mSearchOrderAdapter = new MyAdapter(mSearchOrder , this);
        mLvSearchOrder = (ListView) findViewById(R.id.lv_search_order);
        mLvSearchOrder.setAdapter(mSearchOrderAdapter);

        mEditText = (EditText)findViewById(R.id.editTextBarcode);

        initLoginDialog();

        mEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditText.setHint(null);
            }
        });

        mLvSearchOrder.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String s = mSearchOrder.get(position);
                mEditText.setText(s);
                mSearchOrder.clear();
                mSearchOrderAdapter.notifyDataSetChanged();
                mLvSearchOrder.setVisibility(View.INVISIBLE);

                btn_order_detail.performClick();
            }
        });


        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String txt = mEditText.getText().toString();
                if (mEditText.hasFocus() && txt.length() == 4)
                {
                    //if user has typed four characters, search the whole order id asynchronously
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            OrderIdRecord[] results = MySingleton.getInstance().getmMydb().searchOrderIds(txt);
                            if (results == null || results.length == 0)
                                return;

                            mSearchOrder.clear();
                            for(OrderIdRecord o : results)
                            {
                                mSearchOrder.add(o.tid);
                            }

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mSearchOrder.size() == 1) {
                                        mEditText.setText(results[0].tid);
                                        btn_order_detail.performClick();
                                    }
                                    else
                                    {
                                        mSearchOrderAdapter.notifyDataSetChanged();
                                        mLvSearchOrder.setVisibility(View.VISIBLE);
                                        mLvSearchOrder.setFocusable(true);
                                        mLvSearchOrder.setFocusableInTouchMode(true);
                                        mLvSearchOrder.requestFocus();
                                        mLvSearchOrder.bringToFront();
                                    }
                                }
                            });
                        }
                    }).start();

                }
            }
        });


        mEditText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int DRAWABLE_RIGHT = 2;

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mEditText.getRight() - mEditText.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {

                        Intent intent = new Intent(getApplication(), ScannerActivity.class);
                        startActivity(intent);
                        // Gets a handle to the clipboard service.
                        //ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        // Creates a new text clip to put on the clipboard
                       // ClipData clip = ClipData.newPlainText("text", mEditText.getText());
                        // Set the clipboard's primary clip.
                        //clipboard.setPrimaryClip(clip);

                       // Toast.makeText(MainActivity.this, "copied to clipboard", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                return false;
            }
        });

        AppCompatButton btn_Setting = (AppCompatButton)findViewById(R.id.btn_setting);
        btn_Setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplication(), SettingsActivity.class);
                startActivity(intent);
            }
        });




        AppCompatButton btn_viewScannedData = (AppCompatButton)findViewById(R.id.btn_scanneddata);
        btn_viewScannedData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ScannedDataFragment dialog = ScannedDataFragment.newInstance("1","2");
                dialog.show(getSupportFragmentManager(),"");
            }
        });


        AppCompatButton  btn_Barcode_scan = (AppCompatButton)findViewById(R.id. btn_Barcode_scan);
        btn_Barcode_scan.setText(R.string.action_scan);
        btn_Barcode_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplication(), ScannerActivity.class);
                startActivity(intent);
            }
        });


        spinner = (Spinner)findViewById(R.id.spinner);
        List<BarcodeFormat> BarcodeEnumValues = Arrays.asList(BarcodeFormat.values());
        ArrayAdapter<BarcodeFormat>  BarcodeList = new ArrayAdapter<BarcodeFormat>(this, android.R.layout.simple_spinner_dropdown_item, BarcodeEnumValues);
        spinner.setAdapter(BarcodeList);
        spinner.setSelection(4);
        spinner.setVisibility(View.GONE);

        SetRecyclerView();
        initOrderDetail();

        initLogin();

        initSystemOperation();

    }

    private void transferPackages()
    {
        String currentBatchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);
        String driverId = MySingleton.getInstance().getProperty(MySingleton.ITEM_DRIVER_ID);
        String transferPackages = MySingleton.getInstance().getProperty(MySingleton.ITEM_TRANSFER_PACKAGE_ID);

        String[] ids = transferPackages.split("\\s+");
        if (ids == null || ids.length % 3 != 0) {

            Toast.makeText(MainActivity.this, R.string.invaid_transfer_package_id, Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < ids.length; ) {
            TransferPackagesReq req = new TransferPackagesReq();
            req.setReceiver(ids[i]);
            req.setStart(ids[i + 1]);
            req.setEnd(ids[i + 2]);

            req.setSub_batch(currentBatchId);
            req.setRoute_no(driverId);

            MySingleton.getInstance().getServerInterface().transferPackages(req);
            i = i + 3;
        }

        Toast.makeText(MainActivity.this, R.string.operation_completion, Toast.LENGTH_SHORT).show();
    }

    private void checkRoutePlan()
    {
        PlaceManager mgr = new PlaceManager();
        mgr.initPlace();
        String s = mgr.getJudgeResult();

        MySingleton.getInstance().saveOrderIds();

        if (s.isEmpty()) {
            Toast.makeText(MainActivity.this, R.string.str_no_route_error, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        final AlertDialog dialog = builder.create();
        dialog.setMessage(s);
        dialog.show();
    }

    private void cacheOrderId()
    {
        String ids = MySingleton.getInstance().getProperty(ITEM_SYN_ORDERS_ID);
        if (ids == null || ids.isEmpty())
            return;

        String[] orderLoaderIds = ids.split(",");
        if (orderLoaderIds == null || orderLoaderIds.length == 0)
            return;

        String currentBatchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);
        if (currentBatchId == null || currentBatchId.isEmpty())
            return;

        for (String id : orderLoaderIds) {
            String trueId;
            if (id.length() == 1)
                trueId = String.format("16000%s", id);
            else if (id.length() == 2)
                trueId = String.format("1600%s", id);
            else
                trueId = id;

            MySingleton.getInstance().getServerInterface().loadOrdersByDriver(currentBatchId, Integer.valueOf(trueId),
                    MySingleton.getInstance().getmMydb().getHandler());
        }
    }

    private void handleGetOrderDetailEvent()
    {
        String orderId = mEditText.getText().toString();
        String strPickId = mPickId.getText().toString();
        if (!strPickId.isEmpty()) {
            //firstly, get order id by pickid, then get order detail info.
            String queryId = MySingleton.getInstance().getOrderIdByPickId(Integer.valueOf(strPickId));
            if (queryId != null)
                orderId = queryId;
        }

        if (orderId.isEmpty()) {
            Toast.makeText(MainActivity.this, R.string.str_input_orderid, Toast.LENGTH_SHORT).show();
            return;
        }

        if (orderId.equalsIgnoreCase("999"))
        {
            Toast.makeText(MainActivity.this, R.string.version, Toast.LENGTH_SHORT).show();
            return;
        }

        MySingleton.getInstance().getServerInterface().getOrderDetail(orderId , myHandler);
    }

    private void initOrderDetail()
    {
        myHandler = new MyHandler(Looper.getMainLooper() , getSupportFragmentManager());
        btn_order_detail = (AppCompatButton)findViewById(R.id.btn_queryOrderDetail);
        btn_order_detail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleGetOrderDetailEvent();
            }
        });
    }

    private void initLogin()
    {
        AppCompatButton btn_login = (AppCompatButton)findViewById(R.id.btnLlogin);
        btn_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLoginDialog.show();
               }
        });
    }

    private ArrayList<String> CodeArray = new ArrayList<String>();
    private MyAdapter adapter;
    private void SetRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.mRecyclerView);
        recyclerView.setHasFixedSize(true);

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
                  mEditText.setText(textView.getText().toString());
                  getCode();
              }
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void initSystemOperation()
    {
        AppCompatButton btn_systemOperation = (AppCompatButton)findViewById(R.id.btn_cacheOrders);
        btn_systemOperation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSystemOperationDialog.show();
             }
        });

        mSystemOperationDialog = new AlertDialog.Builder(this);

        mSystemOperationDialog.setTitle(R.string.operation_choose);
        final String[] opertionsArray = new String[] { "Cache Order Id", "Transfer Packages", "Check Route Plan"};

        final int selectedIndex[] = { 0 };

        mSystemOperationDialog.setSingleChoiceItems(opertionsArray, 0,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedIndex[0] = which;

                    }
                });

        mSystemOperationDialog.setPositiveButton(R.string.str_confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (selectedIndex[0])
                        {
                            case 0:
                                cacheOrderId();
                                break;
                            case 1:
                                transferPackages();
                                break;
                            case 2:
                                checkRoutePlan();
                                break;
                            default:
                                break;
                        }

                        dialog.dismiss();
                    }
                });

        mSystemOperationDialog.setNegativeButton(R.string.str_cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
    }

    private void initLoginDialog() {
        AlertDialog.Builder  dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.str_login);

        // 取得自定义View
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        final View myLoginView = layoutInflater.inflate(
                R.layout.my_login_layout, null);
        dialog.setView(myLoginView);

        EditText loginAccountEt = (EditText) myLoginView
                .findViewById(R.id.my_login_account_et);
        EditText loginPasswordEt = (EditText) myLoginView
                .findViewById(R.id.my_login_password_et);

        loginAccountEt.setText("");
        loginPasswordEt.setText("");

        dialog.setPositiveButton(R.string.str_confirm,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        Spinner spinnerLocation = (Spinner)myLoginView.findViewById(R.id.spinner_location);
                        Log.d("MyLogin Dialog", "输入的用户名是："
                                + loginAccountEt.getText().toString());
                        Log.d("MyLogin Dialog", "输入的密码是："
                                + loginPasswordEt.getText().toString());

                        String name = loginAccountEt.getText().toString();
                        String pwd  = loginPasswordEt.getText().toString();

                        if (name.isEmpty() || pwd.isEmpty())
                        {
                            Toast.makeText(MainActivity.this , R.string.str_login_tips,Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            MySingleton.getInstance().getLoginInfo().loginName = name;
                            MySingleton.getInstance().getLoginInfo().loginLocation = spinnerLocation.getSelectedItem().toString();
                            MySingleton.getInstance().getServerInterface().login(name , pwd);
                        }
                    }
                });

        dialog.setNegativeButton(R.string.str_cancel,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLoginDialog.dismiss();
                    }
                });

        mLoginDialog = dialog.create();
    }

    public void getCode() {
        ImageView ivCode = (ImageView) findViewById(R.id.imageView);

        BarcodeEncoder encoder = new BarcodeEncoder();
        try {
            BarcodeValue = mEditText.getText().toString();
            BarcodeType = spinner.getSelectedItem().toString();
            int mHeight = 200,mWidth = 800;
            if(BarcodeType.equals("QR_CODE")){
                mHeight = 500;
                mWidth = 500;
            }
            bit = encoder.encodeBitmap(mEditText.getText().toString(), (BarcodeFormat)spinner.getSelectedItem(), mWidth, mHeight);
            ivCode.setImageBitmap(bit);


            //btn_save.setVisibility(View.VISIBLE);
        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "It doesn't work", Toast.LENGTH_SHORT).show();
        }

        if(!CodeArray.contains(BarcodeValue)){
            CodeArray.add(BarcodeValue);
            if(CodeArray.size() > 8){
                CodeArray.remove(0);
            }
        }
    }
    
    public void hideSoftKeyboard() {
        if(getCurrentFocus()!=null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("debug","onResume()");

        if(!MySingleton.getInstance().ScanText.equals("")){
            mEditText.setText(MySingleton.getInstance().ScanText);
            getCode();
            MySingleton.getInstance().ScanText="";
        }
    }
}