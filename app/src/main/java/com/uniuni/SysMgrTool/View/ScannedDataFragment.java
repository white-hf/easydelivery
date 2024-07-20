package com.uniuni.SysMgrTool.View;

import static java.lang.Thread.sleep;

import android.app.ProgressDialog;
import android.os.Bundle;

import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.uniuni.SysMgrTool.MyDb;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.ServerInterface;
import com.uniuni.SysMgrTool.View.Adapter.ScannedDataAdapter;
import com.uniuni.SysMgrTool.dao.ScannedRecord;
import com.uniuni.SysMgrTool.dao.ScannedRecordDao;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ScannedDataFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ScannedDataFragment extends DialogFragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private List<ScannedRecord> mData = null;
    private DialogFragment mContext;
    private ScannedDataAdapter mAdapter = null;
    private ListView list_animal;
    private TextView mItemDetail;

    public ScannedDataFragment() {
        // Required empty public constructor
    }

    public List<ScannedRecord> getmData() {
        return mData;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ScannedDataFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ScannedDataFragment newInstance(String param1, String param2) {
        ScannedDataFragment fragment = new ScannedDataFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View scannedView =  inflater.inflate(R.layout.fragment_scanned_data, container, false);
        mItemDetail = (TextView)scannedView.findViewById(R.id.txtview_itemdetail);
        list_animal = (ListView) scannedView.findViewById(R.id.lstv_scanneddata);
        mData = new LinkedList<ScannedRecord>();

        list_animal.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ScannedRecord r = mData.get(position);
                if (r != null)
                {
                    String info = MySingleton.getInstance().formatScannedOrderInfo(r);
                    mItemDetail.setText(info);

                    ServerInterface svr = MySingleton.getInstance().getServerInterface();
                    MyDb db = MySingleton.getInstance().getmMydb();

                    //update committed status in DB
                    r.isCommitted = 1;
                    r.committedDate = new Date();
                    svr.getMyHandler().addReqContext(r.orderId , r);

                    svr.handleScanParcel(r.orderId , r.orderSn , false);

                    //db.updateScannedData(r);

                    mData.remove(position);
                    mAdapter.notifyDataSetChanged();
                }
            }
        });

        mAdapter = new ScannedDataAdapter((LinkedList<ScannedRecord>) mData, getActivity());
        list_animal.setAdapter(mAdapter);

        AppCompatButton btn_reload = (AppCompatButton)scannedView.findViewById(R.id.btn_reload);
        btn_reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadScannedDataFromDb();
            }
        });


        AppCompatButton btn_save = (AppCompatButton)scannedView.findViewById(R.id.btn_save);
        btn_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                ProgressDialog mProgressDialog;

                mProgressDialog = new ProgressDialog(getActivity());
                mProgressDialog.setMessage(getString(R.string.str_waiting_svr));
                mProgressDialog.setTitle(getString(R.string.str_operation));
                mProgressDialog.setCancelable(true);
                mProgressDialog.show();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ServerInterface svr = MySingleton.getInstance().getServerInterface();
                        MyDb db = MySingleton.getInstance().getmMydb();

                        int c = 0;
                        Date currentDate = new Date();
                        for(ScannedRecord r : mData)
                        {
                            //update committed status in DB
                            r.isCommitted = 1;
                            r.committedDate = currentDate;
                            svr.getMyHandler().addReqContext(r.orderId , r);
                            //db.updateScannedData(r);

                            svr.handleScanParcel(r.orderId , r.orderSn , false);

                            c++;

                            try {
                                //avoid too many requests to server in a while
                                sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mProgressDialog.cancel();
                                mProgressDialog.dismiss();

                                Toast.makeText(MySingleton.getInstance().getCtx(),  String.format("%d data committed.",mData.size()) , Toast.LENGTH_LONG).show();

                                mData.clear();
                                mAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }).start();
            }
        });

        mContext = this;

        loadScannedDataFromDb();

        return scannedView;
    }

    private boolean loadScannedDataFromDb()
    {
        MyDb db = MySingleton.getInstance().getmMydb();
        ScannedRecordDao dao = db.getScannedRecordDao();

        String batchId = MySingleton.getInstance().getProperty(MySingleton.ITEM_CURRENT_BATCH_ID);
        Integer driverId = MySingleton.getInstance().getIntProperty(MySingleton.ITEM_DRIVER_ID);

        if (batchId == null || batchId.isEmpty() || driverId == null || driverId < 1) {
            return false;
        }

        mData.clear();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ScannedRecord[] records = dao.loadUnCommittedRecords(batchId, driverId);

                /*
                Calendar   cal   =   Calendar.getInstance();
                cal.add(Calendar.DATE,   -1);
                Date d = cal.getTime();

                final OrderIdRecord[] ds = db.getOrderIdRecordDao().findOrderIdByDate(d);
                for (OrderIdRecord dd : ds)
                {
                    FileLog.getInstance().writeLog(dd.tid);
                }

                 */

                if (records == null || records.length == 0)
                    return;

                for (ScannedRecord r : records) {
                    mData.add(r);
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                        mItemDetail.setText(String.format("Load data:%d" , mData.size()));
                    }
                });

            }
        }).start();


        /**
        ScanOrder o = new ScanOrder();
        Random r = new Random();
        long l = r.nextLong();
        o.setId(String.valueOf(l));
        o.setPackId((short) 1);
        o.setOrderId(345L);
        o.setOrderSn("uuuu");
        o.setDriverId(MySingleton.getInstance().getIntProperty(MySingleton.ITEM_DRIVER_ID));

        db.saveScannedData(o);
         **/

        /**
        db.loadScannedData(this);
        try {
            sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mAdapter.notifyDataSetChanged();
         **/

        return true;
    }
}