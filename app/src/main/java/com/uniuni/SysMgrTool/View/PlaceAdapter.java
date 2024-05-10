package com.uniuni.SysMgrTool.View;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.bean.ParcelPlace;

import java.util.List;

    public class PlaceAdapter extends BaseAdapter {

        private Context context;
        private List<ParcelPlace> placeList;

        public PlaceAdapter(Context context, List<ParcelPlace> placeList) {
            this.context = context;
            this.placeList = placeList;
        }

        @Override
        public int getCount() {
            return placeList.size();
        }

        @Override
        public Object getItem(int position) {
            return placeList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.list_item_place, parent, false);
                holder = new ViewHolder();
                holder.codeTextView = convertView.findViewById(R.id.codeTextView);
                holder.streetOrApartmentTextView = convertView.findViewById(R.id.streetOrApartmentTextView);
                holder.addressTextView = convertView.findViewById(R.id.addressTextView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ParcelPlace place = placeList.get(position);
            holder.codeTextView.setText(String.valueOf(place.getCode()));
            holder.streetOrApartmentTextView.setText(place.getStreetOrApartment());
            holder.addressTextView.setText(place.getAddress());

            return convertView;
        }

        private static class ViewHolder {
            TextView codeTextView;
            TextView streetOrApartmentTextView;
            TextView addressTextView;
        }
    }

