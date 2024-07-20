package com.uniuni.SysMgrTool.View;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.View.Adapter.PlaceAdapter;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;

import java.util.ArrayList;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ListViewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ListViewFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public ListViewFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ListViewFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ListViewFragment newInstance(String param1, String param2) {
        ListViewFragment fragment = new ListViewFragment();
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
        View v =  inflater.inflate(R.layout.fragment_list_view, container, false);

        // Create and set the adapter
        ArrayList<DeliveryInfo> lst = MySingleton.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        lst.sort((o1, o2) -> {
            try {
                if (o1.getRouteNumber() == null || o2.getRouteNumber() == null)
                    return 0;

                int r1 = Integer.parseInt(o1.getRouteNumber());
                int r2 = Integer.parseInt(o2.getRouteNumber());
                return Integer.compare(r1, r2);
            } catch (Exception e) {
                return 0;
            }
        });

        PlaceAdapter placeAdapter = new PlaceAdapter(this.getContext(), lst);
        ListView listView = v.findViewById(R.id.listView); // Replace with your ListView's ID
        listView.setAdapter(placeAdapter);

        //v.setBackgroundColor(Color.WHITE);

        return v;
    }
}