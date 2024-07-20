package com.uniuni.SysMgrTool.View.Adapter;

import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.uniuni.SysMgrTool.R;

import java.util.ArrayList;

public class MyAdapter extends BaseAdapter {

    private final ArrayList<String> localDataSet;
    private Context mContext;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public ViewHolder(View view) {
            super(view);
            textView = view.findViewById(R.id.mtext);
        }

        public TextView getTextView(){
            return textView;
        }
    }

    @Override
    public int getCount() {
        return localDataSet.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    /**
     * Initialize the dataset of the Adapter.
     *
     * @param dataSet String[] containing the data to populate views to be used
     * by RecyclerView.
     */
    public MyAdapter(ArrayList<String> dataSet , Context c) {
        localDataSet = dataSet;
        mContext = c;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.my_recycler_view,parent,false);

        TextView txt_searchOrder = (TextView) view.findViewById(R.id.mtext);
        String r = localDataSet.get(position);
        txt_searchOrder.setText(r);

        return view;
    }

}