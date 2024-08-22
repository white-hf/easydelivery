package com.hf.courierservice.bean;

/**
 * This class is detailed description of a package, and it is complicated, many fields
 * is completely optional. It might change according to the specific courier's needs.
 * @author jvtang
 * @since 2024-08-21
 */
public class DeliveringListData {
    private long order_id;
    private String order_sn;
    private String tracking_no;
    private int goods_type;
    private int express_type;
    private int route_no;
    private String assign_time;
    private String delivery_by;
    private int state;
    private String name;
    private String mobile;
    private String phone_extension;
    private String address;
    private String zipcode;
    private String lat;
    private String lng;
    private String unit_number;
    private String buzz_code;
    private String postscript;
    private int warehouse_id;
    private int need_retry;
    private Dispatch_type dispatch_type;
    private int scan_status;
    private int is_detained;
    private int time_range;
    private int since_last_updated;
    private String building_id;
    public void setOrder_id(long order_id) {
        this.order_id = order_id;
    }
    public long getOrder_id() {
        return order_id;
    }

    public void setOrder_sn(String order_sn) {
        this.order_sn = order_sn;
    }
    public String getOrder_sn() {
        return order_sn;
    }

    public void setTracking_no(String tracking_no) {
        this.tracking_no = tracking_no;
    }
    public String getTracking_no() {
        return tracking_no;
    }

    public void setGoods_type(int goods_type) {
        this.goods_type = goods_type;
    }
    public int getGoods_type() {
        return goods_type;
    }

    public void setExpress_type(int express_type) {
        this.express_type = express_type;
    }
    public int getExpress_type() {
        return express_type;
    }

    public void setRoute_no(int route_no) {
        this.route_no = route_no;
    }
    public int getRoute_no() {
        return route_no;
    }

    public void setAssign_time(String assign_time) {
        this.assign_time = assign_time;
    }
    public String getAssign_time() {
        return assign_time;
    }

    public void setDelivery_by(String delivery_by) {
        this.delivery_by = delivery_by;
    }
    public String getDelivery_by() {
        return delivery_by;
    }

    public void setState(int state) {
        this.state = state;
    }
    public int getState() {
        return state;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }
    public String getMobile() {
        return mobile;
    }

    public void setPhone_extension(String phone_extension) {
        this.phone_extension = phone_extension;
    }
    public String getPhone_extension() {
        return phone_extension;
    }

    public void setAddress(String address) {
        this.address = address;
    }
    public String getAddress() {
        return address;
    }

    public void setZipcode(String zipcode) {
        this.zipcode = zipcode;
    }
    public String getZipcode() {
        return zipcode;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }
    public String getLat() {
        return lat;
    }

    public void setLng(String lng) {
        this.lng = lng;
    }
    public String getLng() {
        return lng;
    }

    public void setUnit_number(String unit_number) {
        this.unit_number = unit_number;
    }
    public String getUnit_number() {
        return unit_number;
    }

    public void setBuzz_code(String buzz_code) {
        this.buzz_code = buzz_code;
    }
    public String getBuzz_code() {
        return buzz_code;
    }

    public void setPostscript(String postscript) {
        this.postscript = postscript;
    }
    public String getPostscript() {
        return postscript;
    }

    public void setWarehouse_id(int warehouse_id) {
        this.warehouse_id = warehouse_id;
    }
    public int getWarehouse_id() {
        return warehouse_id;
    }

    public void setNeed_retry(int need_retry) {
        this.need_retry = need_retry;
    }
    public int getNeed_retry() {
        return need_retry;
    }

    public void setDispatch_type(Dispatch_type dispatch_type) {
        this.dispatch_type = dispatch_type;
    }
    public Dispatch_type getDispatch_type() {
        return dispatch_type;
    }

    public void setScan_status(int scan_status) {
        this.scan_status = scan_status;
    }
    public int getScan_status() {
        return scan_status;
    }

    public void setIs_detained(int is_detained) {
        this.is_detained = is_detained;
    }
    public int getIs_detained() {
        return is_detained;
    }

    public void setTime_range(int time_range) {
        this.time_range = time_range;
    }
    public int getTime_range() {
        return time_range;
    }

    public void setSince_last_updated(int since_last_updated) {
        this.since_last_updated = since_last_updated;
    }
    public int getSince_last_updated() {
        return since_last_updated;
    }

    public void setBuilding_id(String building_id) {
        this.building_id = building_id;
    }
    public String getBuilding_id() {
        return building_id;
    }

}
