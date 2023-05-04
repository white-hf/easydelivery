package com.uniuni.SysMgrTool.View;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.dao.ScannedRecord;

import java.util.LinkedList;

public class ScannedDataAdapter extends BaseAdapter {
    private LinkedList<ScannedRecord> mData;
    private Context mContext;

    public ScannedDataAdapter(LinkedList<ScannedRecord> mData, Context mContext) {
        this.mData = mData;
        this.mContext = mContext;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public MyAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.my_recycler_view, viewGroup, false);

        return new MyAdapter.ViewHolder(view);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.my_recycler_view,parent,false);

        TextView txt_scanned_detail = (TextView) view.findViewById(R.id.mtext);
        ScannedRecord r = mData.get(position);
        txt_scanned_detail.setText(r.pickId + " " + r.tid + " " + r.driverId + " " + MySingleton.getInstance().formatDate(r.scannedTime));

        return view;
    }
}
