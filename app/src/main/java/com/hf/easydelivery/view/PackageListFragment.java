package com.hf.easydelivery.view;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SearchView searchView;
    private FloatingActionButton fabBackToMap;

    private final List<ParcelItem> allParcels = new ArrayList<>();
    private final List<ParcelItem> filteredParcels = new ArrayList<>();
    private ParcelListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_packages_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        progressBar  = view.findViewById(R.id.progress_bar);
        emptyView    = view.findViewById(R.id.empty_view);
        searchView   = view.findViewById(R.id.search_view);
        fabBackToMap = view.findViewById(R.id.fab_back_to_map);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ParcelListAdapter(requireContext(), new ParcelListAdapter.OnItemClickListener() {
            @Override public void onItemClick(@NonNull ParcelItem item) { onItemClicked(item); }
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
                // Java 调用需用 getOnBackPressedDispatcher()
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            });
        }

        // 加载数据（接入你们 Android 的数据源 → 转为 ParcelItem 列表）
        loadAllParcels();

        return view;
    }

    private void loadAllParcels() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        // 将你们的真实数据源接在这里（见 DefaultDataSource）
        DataSource ds = DefaultDataSource.getInstance();
        ds.fetch(requireContext(), new DataSource.Callback() {
            @Override public void onResult(@NonNull List<ParcelItem> items) {
                progressBar.setVisibility(View.GONE);
                allParcels.clear();
                allParcels.addAll(items);
                applyFilter(""); // 初始不过滤
            }
        });
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        filteredParcels.clear();
        if (TextUtils.isEmpty(q)) {
            filteredParcels.addAll(allParcels);
        } else {
            for (ParcelItem it : allParcels) {
                String rn = safeLower(it.routeNumber);
                String sn = safeLower(it.orderSn);
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

    private void onItemClicked(@NonNull ParcelItem item) {
        // 仅允许派送中/在途进入拍照
        if (!item.status.isDeliverable()) {
            Toast.makeText(requireContext(), "该包裹不在派送中状态，无法操作。", Toast.LENGTH_SHORT).show();
            return;
        }

        // 从资源管理中查找 DeliveryInfo（按运单号优先，找不到再按包裹号）
        DeliveryInfo di = findDeliveryInfo(item);
        if (di == null) {
            Toast.makeText(requireContext(), "未找到包裹详情，无法进入拍照", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle args = new Bundle();
        args.putLong("order_id", di.getOrderId() == null ? -1L : di.getOrderId());
        try { args.putDouble("latitude",  di.getLatitude()); } catch (Throwable ignore) { args.putDouble("latitude",  -1); }
        try { args.putDouble("longitude", di.getLongitude()); } catch (Throwable ignore) { args.putDouble("longitude", -1); }

        CameraFragment camera = new CameraFragment();
        camera.setArguments(args);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, camera, "camera")
                .addToBackStack("camera")
                .commit();
    }

    @Nullable
    private DeliveryInfo findDeliveryInfo(@NonNull ParcelItem item) {
        List<DeliveryInfo> list = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        if (list == null) return null;
        // 1) 先按运单号精确匹配
        if (item.orderSn != null) {
            for (DeliveryInfo e : list) {
                if (item.orderSn.equals(e.getOrderSn())) return e;
            }
        }
        // 2) 再按包裹号匹配
        if (item.routeNumber != null) {
            for (DeliveryInfo e : list) {
                if (item.routeNumber.equals(String.valueOf(e.getRouteNumber()))) return e;
            }
        }
        return null;
    }

    // ============================
    // 数据模型与适配器（与 iOS 对齐）
    // ============================

    /** 将你们的数据源实体映射到这个轻量模型 */
    public static final class ParcelItem {
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
        public final String routeNumber; // 包裹号（路由号）
        public final String orderSn;     // 运单号
        public final Status status;      // 状态
        public ParcelItem(String routeNumber, String orderSn, Status status) {
            this.routeNumber = routeNumber;
            this.orderSn = orderSn;
            this.status = status;
        }
    }

    /** RecyclerView 适配器（两行：包裹号/运单号） */
    public static final class ParcelListAdapter extends RecyclerView.Adapter<ParcelListAdapter.VH> {
        public interface OnItemClickListener { void onItemClick(@NonNull ParcelItem item); }
        private final Context ctx;
        private final OnItemClickListener listener;
        private final List<ParcelItem> items = new ArrayList<>();
        public ParcelListAdapter(Context ctx, OnItemClickListener l) {
            this.ctx = ctx; this.listener = l;
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            ParcelItem it = items.get(pos);
            h.title.setText("包裹号: " + (it.routeNumber == null ? "—" : it.routeNumber));
            h.sub.setText("运单号: " + (it.orderSn == null ? "" : it.orderSn));
            h.itemView.setOnClickListener(v -> listener.onItemClick(it));
        }
        @Override public int getItemCount() { return items.size(); }
        public void submit(@NonNull List<ParcelItem> data) {
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

    /**
     * 数据源接口：请在 DefaultDataSource 中接入你们的真实数据。
     */
    public interface DataSource {
        interface Callback { void onResult(@NonNull List<ParcelItem> items); }
        void fetch(@NonNull Context context, @NonNull Callback cb);
    }

    /**
     * 默认数据源占位：
     * 1) 你可以在这里调用你们现有的 Repository/Manager，组装成 ParcelItem 列表；
     * 2) 下面演示了一个空数据返回（不会编译报错），请替换为真实实现。
     */
    public static final class DefaultDataSource implements DataSource {
        private static final DefaultDataSource INSTANCE = new DefaultDataSource();
        public static DefaultDataSource getInstance() { return INSTANCE; }
        @Override
        public void fetch(@NonNull Context context, @NonNull Callback cb) {
            List<ParcelItem> mapped = new ArrayList<>();
            List<DeliveryInfo> raw = ResourceMgr.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
            if (raw != null) {
                for (DeliveryInfo e : raw) {
                    String rn = e.getRouteNumber() == null ? null : String.valueOf(e.getRouteNumber());
                    String sn = e.getOrderSn();
                    Integer stCode = e.getState();
                    ParcelItem.Status st = ParcelItem.Status.fromState(stCode);
                    mapped.add(new ParcelItem(rn, sn, st));
                }
            }
            cb.onResult(mapped);
        }
    }
}
