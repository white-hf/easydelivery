package com.hf.easydelivery.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;
import com.hf.easydelivery.map.MapHostFragment;
import com.hf.easydelivery.view.model.MapViewModel;
import com.hf.easydelivery.view.model.ScanViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MVVM 优化版:
 * - 不使用独立的 ListViewModel，直接使用 MapViewModel 或 ScanViewModel 作为数据源。
 * - 过滤逻辑由 Fragment 管理。
 */
public class PackageListFragment extends Fragment {

    public enum Status {
        IN_TRANSIT(202),
        GATEWAY_TRANSIT(199),
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
            return this == IN_TRANSIT;
        }
    }

    /** 列表模式：由哪个 ViewModel 提供数据 */
    public enum ListMode { IN_TRANSIT_FROM_MAP, UNSCANNED_FROM_SCAN }
    private static final String ARG_LIST_MODE = "arg_list_mode";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SearchView searchView;
    private FloatingActionButton fabBackToMap;

    private ParcelListAdapter adapter;
    private MapViewModel mapViewModel;
    private ScanViewModel scanViewModel;
    private ListMode currentMode = ListMode.IN_TRANSIT_FROM_MAP;

    /** 当前监听的数据源（随模式切换） */
    private LiveData<List<DeliveryInfo>> currentDataSource;
    private List<DeliveryInfo> currentFullList = new ArrayList<>();
    private final Observer<List<DeliveryInfo>> dataObserver = this::updateList;

    // 新增：用于观察 MapViewModel 的加载状态
    private LiveData<Boolean> mapLoadingState;
    // 新增：用于观察 ScanViewModel 的加载状态
    private LiveData<Boolean> scanLoadingState;

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
        adapter = new ParcelListAdapter(requireContext(), this::onItemClicked);
        recyclerView.setAdapter(adapter);

        // 初始化 ViewModel
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        scanViewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        // 读取外部传入的列表模式（如果有）
        Bundle args = getArguments();
        if (args != null) {
            String m = args.getString(ARG_LIST_MODE, ListMode.IN_TRANSIT_FROM_MAP.name());
            try { currentMode = ListMode.valueOf(m); } catch (Throwable ignored) { currentMode = ListMode.IN_TRANSIT_FROM_MAP; }
        }

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
                if (getParentFragment() instanceof MapHostFragment) {
                    ((MapHostFragment) getParentFragment()).switchToMap();
                }
            });
        }

        // 根据当前模式加载对应数据
        if (currentMode == ListMode.UNSCANNED_FROM_SCAN) {
            loadUnscannedParcels();
        } else {
            loadInDeliveryParcels();
        }

        // ... (在您的 onViewCreated 方法中)

        recyclerView.setClipToPadding(false);

// 动态设置内边距
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (v, insets) -> {
            // 获取系统导航栏的高度
            int sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            // 获取您应用底部 FAB 的总高度（高度 + 底部外边距）
            int navExtra = 0;
            View fab = requireActivity().findViewById(R.id.fab_back_to_map);
            if (fab != null) {
                navExtra = fab.getHeight() + ((ViewGroup.MarginLayoutParams) fab.getLayoutParams()).bottomMargin;
            }

            // 最终的底部内边距取系统导航栏和 FAB 区域的最大值
            int desiredBottom = Math.max(sysBottom, navExtra);

            // 设置所有方向的内边距，这里为列表内容左右和顶部留出 8dp 间距
            v.setPadding(dp2px(8), dp2px(8), dp2px(8), desiredBottom);

            return insets;
        });

        return view;
    }

    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * 切换监听的数据源，避免重复注册导致内存泄漏
     * @param newSource 要开始观察的新 LiveData
     */
    private void switchDataSource(@NonNull LiveData<List<DeliveryInfo>> newSource) {
        if (currentDataSource != null) {
            currentDataSource.removeObserver(dataObserver);
        }
        currentDataSource = newSource;
        currentDataSource.observe(getViewLifecycleOwner(), dataObserver);
    }

    /**
     * 加载派送中包裹列表
     */
    public void loadInDeliveryParcels() {
        currentMode = ListMode.IN_TRANSIT_FROM_MAP;
        switchDataSource(mapViewModel.getMapItemsLive());
        // 通知 MapViewModel 主动刷新
        mapViewModel.requestAndRefreshMarkers(true);
        if (searchView != null) {
            searchView.setQuery("", false);
            searchView.clearFocus();
        }
    }

    /**
     * 加载未扫描包裹列表
     */
    public void loadUnscannedParcels() {
        currentMode = ListMode.UNSCANNED_FROM_SCAN;
        switchDataSource(scanViewModel.getUnscannedFilteredLive());
        // 优化点：明确调用 ScanViewModel 的查询方法，而不是被动等待事件
        scanViewModel.queryUnscanned();
        if (searchView != null) {
            searchView.setQuery("", false);
            searchView.clearFocus();
        }
    }

    /**
     * 更新列表数据，此方法由 LiveData 的观察者自动调用
     * @param fullList 完整的、未过滤的包裹列表
     */
    private void updateList(List<DeliveryInfo> fullList) {
        currentFullList = (fullList != null) ? new ArrayList<>(fullList) : new ArrayList<>();
        sortByRouteNumber(currentFullList);
        applyFilter(searchView != null ? searchView.getQuery().toString() : "");
    }

    /**
     * 过滤当前列表数据
     * @param query 搜索关键字
     */
    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<DeliveryInfo> filteredList;

        if (q.isEmpty()) {
            filteredList = new ArrayList<>(currentFullList);
        } else {
            filteredList = currentFullList.stream()
                    .filter(it -> {
                        String rn = safeLower(it.getRouteNumber());
                        String sn = safeLower(it.getOrderSn());
                        return (rn != null && rn.endsWith(q)) || (sn != null && sn.endsWith(q));
                    })
                    .collect(Collectors.toList());
        }

        sortByRouteNumber(filteredList);
        adapter.submit(filteredList);
        boolean empty = filteredList.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (empty) {
            if (currentMode == ListMode.UNSCANNED_FROM_SCAN) {
                emptyView.setText("暂无【未扫描】包裹");
            } else {
                emptyView.setText("暂无【派送中】包裹");
            }
        }
    }

    private String safeLower(String s) {
        return s == null ? null : s.toLowerCase();
    }

    private void sortByRouteNumber(List<DeliveryInfo> list) {
        list.sort((a, b) -> safeString(a.getRouteNumber()).compareTo(safeString(b.getRouteNumber())));
    }

    private String safeString(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private void onItemClicked(@NonNull DeliveryInfo item) {
        if (currentMode == ListMode.UNSCANNED_FROM_SCAN) {
            Toast.makeText(requireContext(), "该列表为【未扫描】包裹，请先在扫描页完成扫描。", Toast.LENGTH_SHORT).show();
            return;
        }

        Status status = Status.fromState(item.getState());
        if (!status.isDeliverable()) {
            Toast.makeText(requireContext(), "该包裹不在派送中状态，无法操作。", Toast.LENGTH_SHORT).show();
            return;
        }

        if (item.getOrderId() == null) {
            Toast.makeText(requireContext(), "包裹详情不完整，无法进入拍照", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), CameraActivity.class);
        intent.putExtra("order_id", item.getOrderId());
        try { intent.putExtra("latitude",  item.getLatitude()); } catch (Throwable ignore) { intent.putExtra("latitude",  -1); }
        try { intent.putExtra("longitude", item.getLongitude()); } catch (Throwable ignore) { intent.putExtra("longitude", -1); }
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 移除所有观察者以避免内存泄漏
        if (currentDataSource != null) {
            currentDataSource.removeObserver(dataObserver);
        }
    }

    // 移除这两个冗余的公共方法，因为 loadInDeliveryParcels/loadUnscannedParcels 已经足够
    // public void showInTransitList() { loadInDeliveryParcels(); }
    // public void showUnscannedList() { loadUnscannedParcels(); }

    public static final class ParcelListAdapter extends RecyclerView.Adapter<ParcelListAdapter.VH> {
        public interface OnItemClickListener { void onItemClick(@NonNull DeliveryInfo item); }
        private final Context ctx;
        private final OnItemClickListener listener;
        private final List<DeliveryInfo> items = new ArrayList<>();
        private final int minRowHeightPx;
        public ParcelListAdapter(Context ctx, OnItemClickListener l) {
            this.ctx = ctx; this.listener = l;
            float density = ctx.getResources().getDisplayMetrics().density;
            this.minRowHeightPx = (int) (64f * density);
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_package_list, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            DeliveryInfo it = items.get(pos);
            h.itemView.setMinimumHeight(minRowHeightPx);
            String routeText = "包裹号: " + valueOrDash(it.getRouteNumber());
            String orderText = "运单号: " + valueOrDash(it.getOrderSn());
            String customerText = "客户: " + valueOrDash(it.getName());
            h.routeNumber.setText(routeText);
            h.orderSn.setText(orderText);
            h.customer.setText(customerText);

            String unit = valueOrDash(it.getUnitNumber());
            String streetNo = it.getCivilNumber() != null && it.getCivilNumber() > 0
                    ? String.valueOf(it.getCivilNumber())
                    : "—";
            String addressLine = valueOrDash(it.getAddress());
            StringBuilder addressBuilder = new StringBuilder("地址: ");
            if (!"—".equals(streetNo)) {
                addressBuilder.append(streetNo).append("号 ");
            }
            if (!"—".equals(unit)) {
                addressBuilder.append(unit).append("单元 ");
            }
            addressBuilder.append(addressLine);
            String addressText = addressBuilder.toString();
            h.address.setText(addressText);
            View.OnClickListener clickListener = v -> {
                int adapterPos = h.getBindingAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return;
                DeliveryInfo target = items.get(adapterPos);
                listener.onItemClick(target);
            };
            h.itemView.setOnClickListener(clickListener);
            h.routeNumber.setOnClickListener(clickListener);
            h.orderSn.setOnClickListener(clickListener);
            h.customer.setOnClickListener(clickListener);
            h.address.setOnClickListener(clickListener);

            attachCopySupport(h.routeNumber, routeText);
            attachCopySupport(h.orderSn, orderText);
            attachCopySupport(h.customer, customerText);
            attachCopySupport(h.address, addressText);
        }
        @Override public int getItemCount() { return items.size(); }
        public void submit(@NonNull List<DeliveryInfo> data) {
            items.clear(); items.addAll(data); notifyDataSetChanged();
        }
        static final class VH extends RecyclerView.ViewHolder {
            final TextView routeNumber;
            final TextView orderSn;
            final TextView customer;
            final TextView address;
            VH(@NonNull View itemView) { super(itemView);
                routeNumber = itemView.findViewById(R.id.tv_route_number);
                orderSn = itemView.findViewById(R.id.tv_order_sn);
                customer = itemView.findViewById(R.id.tv_customer);
                address = itemView.findViewById(R.id.tv_address);
            }
        }

        private String valueOrDash(String value) {
            return value == null || value.trim().isEmpty() ? "—" : value;
        }

        private void attachCopySupport(@NonNull TextView view, @NonNull String text) {
            view.setOnLongClickListener(v -> {
                copyText(text);
                return true;
            });
        }

        private void copyText(@NonNull String text) {
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            ClipData clip = ClipData.newPlainText("parcel_info", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(ctx, ctx.getString(R.string.copy_success), Toast.LENGTH_SHORT).show();
        }
    }
}
