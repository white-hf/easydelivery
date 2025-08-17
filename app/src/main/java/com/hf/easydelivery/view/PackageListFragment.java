package com.hf.easydelivery.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.widget.SearchView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.map.MapHostFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import com.hf.easydelivery.view.model.MapViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * iOS 风格的“包裹列表”实现：
 * - 顶部 SearchView：支持按“包裹号/运单号后缀”过滤
 * - 中部 RecyclerView：展示过滤后的结果
 * - 右下角 FAB：返回地图
 *
 * 注意：Android 的数据源与 iOS 不同。本实现定义了一个最小的数据适配模型（ParcelItem），
 * 以及 DataSource 接口。请在 DefaultDataSource 中接入你的真实数据源并转换为 ParcelItem 列表。
 */
public class PackageListFragment extends Fragment {

    public enum Status {
        IN_TRANSIT(202),           // 派送中
        GATEWAY_TRANSIT(199),      // 分拣/在途中（如需放开拍照，也算可操作）
        OTHER(0);
        public final int code;
        Status(int c) { this.code = c; }
        public static Status fromState(@Nullable Integer state) {
            if (state == null) return OTHER;
            if (state == 202) return IN_TRANSIT;
            if (state == 199) return GATEWAY_TRANSIT;
            return OTHER;
        }
        public boolean isDeliverable() {
            return this == IN_TRANSIT ; // 可根据业务只保留 IN_TRANSIT
        }
    }

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SearchView searchView;
    private FloatingActionButton fabBackToMap;

    private final List<DeliveryInfo> allParcels = new ArrayList<>();
    private final List<DeliveryInfo> filteredParcels = new ArrayList<>();
    private ParcelListAdapter adapter;

    private MapViewModel mapViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_packages_list, container, false);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        recyclerView = view.findViewById(R.id.recycler_view);
        progressBar  = view.findViewById(R.id.progress_bar);
        emptyView    = view.findViewById(R.id.empty_view);
        searchView   = view.findViewById(R.id.search_view);
        fabBackToMap = view.findViewById(R.id.fab_back_to_map);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ParcelListAdapter(requireContext(), new ParcelListAdapter.OnItemClickListener() {
            @Override public void onItemClick(@NonNull DeliveryInfo item) { onItemClicked(item); }
        });
        recyclerView.setAdapter(adapter);

        // 搜索：后缀匹配（包裹号/运单号）
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    applyFilter(query);
                    return true;
                }
                @Override
                public boolean onQueryTextChange(String newText) {
                    applyFilter(newText);
                    return true;
                }
            });
        }

        // 右下角返回地图
        if (fabBackToMap != null) {
            fabBackToMap.setOnClickListener(v -> {
                // 直接通知宿主 Activity 或 MapHostFragment 切换到地图视图
               if (getParentFragment() instanceof MapHostFragment) {
                    ((MapHostFragment) getParentFragment()).switchToMap();
                }
            });
        }

        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        mapViewModel.getPackages().observe(getViewLifecycleOwner(), new Observer<List<DeliveryInfo>>() {
            @Override
            public void onChanged(List<DeliveryInfo> items) {
                progressBar.setVisibility(View.GONE);
                allParcels.clear();
                allParcels.addAll(items);
                applyFilter("");
            }
        });

        return view;
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        filteredParcels.clear();
        if (TextUtils.isEmpty(q)) {
            filteredParcels.addAll(allParcels);
        } else {
            for (DeliveryInfo it : allParcels) {
                String rn = safeLower(it.getRouteNumber());
                String sn = safeLower(it.getOrderSn());
                if ((rn != null && rn.endsWith(q)) || (sn != null && sn.endsWith(q))) {
                    filteredParcels.add(it);
                }
            }
        }
        adapter.submit(filteredParcels);
        boolean empty = filteredParcels.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private String safeLower(String s) { return s == null ? null : s.toLowerCase(); }

    private void onItemClicked(@NonNull DeliveryInfo item) {
        // 仅允许派送中/在途进入拍照
        Status status = Status.fromState(item.getState());
        if (!status.isDeliverable()) {
            Toast.makeText(requireContext(), "该包裹不在派送中状态，无法操作。", Toast.LENGTH_SHORT).show();
            return;
        }

        // 从资源管理中查找 DeliveryInfo（按运单号优先，找不到再按包裹号）
        DeliveryInfo di = findDeliveryInfo(item);
        if (di == null) {
            Toast.makeText(requireContext(), "未找到包裹详情，无法进入拍照", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), CameraActivity.class);
        intent.putExtra("order_id", di.getOrderId() == null ? -1L : di.getOrderId());
        try { intent.putExtra("latitude",  di.getLatitude()); } catch (Throwable ignore) { intent.putExtra("latitude",  -1); }
        try { intent.putExtra("longitude", di.getLongitude()); } catch (Throwable ignore) { intent.putExtra("longitude", -1); }
        startActivity(intent);
    }

    @Nullable
    private DeliveryInfo findDeliveryInfo(@NonNull DeliveryInfo item) {
        List<DeliveryInfo> list = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        if (list == null) return null;
        // 1) 先按运单号精确匹配
        if (item.getOrderSn() != null) {
            for (DeliveryInfo e : list) {
                if (item.getOrderSn().equals(e.getOrderSn())) return e;
            }
        }
        // 2) 再按包裹号匹配
        if (item.getRouteNumber() != null) {
            for (DeliveryInfo e : list) {
                if (item.getRouteNumber().equals(String.valueOf(e.getRouteNumber()))) return e;
            }
        }
        return null;
    }

    // ============================
    // 数据模型与适配器（与 iOS 对齐）
    // ============================

    /** RecyclerView 适配器（两行：包裹号/运单号） */
    public static final class ParcelListAdapter extends RecyclerView.Adapter<ParcelListAdapter.VH> {
        public interface OnItemClickListener { void onItemClick(@NonNull DeliveryInfo item); }
        private final Context ctx;
        private final OnItemClickListener listener;
        private final List<DeliveryInfo> items = new ArrayList<>();
        public ParcelListAdapter(Context ctx, OnItemClickListener l) {
            this.ctx = ctx; this.listener = l;
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            DeliveryInfo it = items.get(pos);
            h.title.setText("包裹号: " + (it.getRouteNumber() == null ? "—" : it.getRouteNumber()));
            h.sub.setText("运单号: " + (it.getOrderSn() == null ? "" : it.getOrderSn()));
            h.itemView.setOnClickListener(v -> listener.onItemClick(it));
        }
        @Override public int getItemCount() { return items.size(); }
        public void submit(@NonNull List<DeliveryInfo> data) {
            items.clear(); items.addAll(data); notifyDataSetChanged();
        }
        static final class VH extends RecyclerView.ViewHolder {
            final TextView title; final TextView sub;
            VH(@NonNull View itemView) { super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                sub   = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
