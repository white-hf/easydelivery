package com.uniuni.SysMgrTool.Response;

public class Tracking {

    private long order_id;
    private String order_sn;
    private String enpathInfo;
    private String pathAddr;
    private String pathInfo;
    private String rcountry;
    private int state;
    private int grid_code;
    private int exception;
    private long update_time;
    private int te_origin;
    private int process_time;
    private int arrival_scan;
    private int attempt_time;
    private int delivered_time;
    private int warehouse;
    private String tno;
    private String segment;
    private String owner;
    private int is_resend;
    private int is_returned;
    private String is_rejected;
    private int is_addon;
    private int is_remote;
    private int is_pn_enabled;
    private int is_custom_examed;
    private int is_transshipment;
    private int storage_rotation;
    private String storage_info;
    private int storage_code;
    private String is_cleared;
    private String tracking_number;
    private String reference;
    private String sub_reference;
    private String internal_account_number;
    private String bag_no;
    private String service_point_id;
    private String sp_storage_code;
    private String shipper;
    private String shipper_address_1;
    private String shipper_address_2;
    private String shipper_address_3;
    private String shipper_city;
    private String shipper_county_state;
    private String shipper_zip;
    private String shipper_country_code;
    private String consignee;
    private String address1;
    private String address2;
    private String address3;
    private String city;
    private String province;
    private String province_code;
    private String zip;
    private String country_code;
    private String email;
    private String phone;
    private int pieces;
    private String total_weight;
    private String weight_uom;
    private String length;
    private String width;
    private String height;
    private String dimension_uom;
    private double total_value;
    private String currency;
    private String incoterms;
    private String item_description;
    private String item_hs_code;
    private int item_quantity;
    private double item_value;
    private String country_of_origin;
    private String second_delivery_sn;
    private String resend_req_no;
    private int failed_reason_type;
    private String estimate_deliver_time;
    private int storaged_warehouse;
    private int pod_qualified;
    private int express_type;
    private int goods_type;
    private int danger_type;
    private int weight_type;
    private int size_type;
    private int require_signature;
    private String dispatch_type;
    private int enable_retry;
    private long incident_occurence_time;
    private int last_operation_warehouse;
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

    public void setEnpathInfo(String enpathInfo) {
        this.enpathInfo = enpathInfo;
    }
    public String getEnpathInfo() {
        return enpathInfo;
    }

    public void setPathAddr(String pathAddr) {
        this.pathAddr = pathAddr;
    }
    public String getPathAddr() {
        return pathAddr;
    }

    public void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }
    public String getPathInfo() {
        return pathInfo;
    }

    public void setRcountry(String rcountry) {
        this.rcountry = rcountry;
    }
    public String getRcountry() {
        return rcountry;
    }

    public void setState(int state) {
        this.state = state;
    }
    public int getState() {
        return state;
    }

    public void setGrid_code(int grid_code) {
        this.grid_code = grid_code;
    }
    public int getGrid_code() {
        return grid_code;
    }

    public void setException(int exception) {
        this.exception = exception;
    }
    public int getException() {
        return exception;
    }

    public void setUpdate_time(long update_time) {
        this.update_time = update_time;
    }
    public long getUpdate_time() {
        return update_time;
    }

    public void setTe_origin(int te_origin) {
        this.te_origin = te_origin;
    }
    public int getTe_origin() {
        return te_origin;
    }

    public void setProcess_time(int process_time) {
        this.process_time = process_time;
    }
    public int getProcess_time() {
        return process_time;
    }

    public void setArrival_scan(int arrival_scan) {
        this.arrival_scan = arrival_scan;
    }
    public int getArrival_scan() {
        return arrival_scan;
    }

    public void setAttempt_time(int attempt_time) {
        this.attempt_time = attempt_time;
    }
    public int getAttempt_time() {
        return attempt_time;
    }

    public void setDelivered_time(int delivered_time) {
        this.delivered_time = delivered_time;
    }
    public int getDelivered_time() {
        return delivered_time;
    }

    public void setWarehouse(int warehouse) {
        this.warehouse = warehouse;
    }
    public int getWarehouse() {
        return warehouse;
    }

    public void setTno(String tno) {
        this.tno = tno;
    }
    public String getTno() {
        return tno;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }
    public String getSegment() {
        return segment;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
    public String getOwner() {
        return owner;
    }

    public void setIs_resend(int is_resend) {
        this.is_resend = is_resend;
    }
    public int getIs_resend() {
        return is_resend;
    }

    public void setIs_returned(int is_returned) {
        this.is_returned = is_returned;
    }
    public int getIs_returned() {
        return is_returned;
    }

    public void setIs_rejected(String is_rejected) {
        this.is_rejected = is_rejected;
    }
    public String getIs_rejected() {
        return is_rejected;
    }

    public void setIs_addon(int is_addon) {
        this.is_addon = is_addon;
    }
    public int getIs_addon() {
        return is_addon;
    }

    public void setIs_remote(int is_remote) {
        this.is_remote = is_remote;
    }
    public int getIs_remote() {
        return is_remote;
    }

    public void setIs_pn_enabled(int is_pn_enabled) {
        this.is_pn_enabled = is_pn_enabled;
    }
    public int getIs_pn_enabled() {
        return is_pn_enabled;
    }

    public void setIs_custom_examed(int is_custom_examed) {
        this.is_custom_examed = is_custom_examed;
    }
    public int getIs_custom_examed() {
        return is_custom_examed;
    }

    public void setIs_transshipment(int is_transshipment) {
        this.is_transshipment = is_transshipment;
    }
    public int getIs_transshipment() {
        return is_transshipment;
    }

    public void setStorage_rotation(int storage_rotation) {
        this.storage_rotation = storage_rotation;
    }
    public int getStorage_rotation() {
        return storage_rotation;
    }

    public void setStorage_info(String storage_info) {
        this.storage_info = storage_info;
    }
    public String getStorage_info() {
        return storage_info;
    }

    public void setStorage_code(int storage_code) {
        this.storage_code = storage_code;
    }
    public int getStorage_code() {
        return storage_code;
    }

    public void setIs_cleared(String is_cleared) {
        this.is_cleared = is_cleared;
    }
    public String getIs_cleared() {
        return is_cleared;
    }

    public void setTracking_number(String tracking_number) {
        this.tracking_number = tracking_number;
    }
    public String getTracking_number() {
        return tracking_number;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
    public String getReference() {
        return reference;
    }

    public void setSub_reference(String sub_reference) {
        this.sub_reference = sub_reference;
    }
    public String getSub_reference() {
        return sub_reference;
    }

    public void setInternal_account_number(String internal_account_number) {
        this.internal_account_number = internal_account_number;
    }
    public String getInternal_account_number() {
        return internal_account_number;
    }

    public void setBag_no(String bag_no) {
        this.bag_no = bag_no;
    }
    public String getBag_no() {
        return bag_no;
    }

    public void setService_point_id(String service_point_id) {
        this.service_point_id = service_point_id;
    }
    public String getService_point_id() {
        return service_point_id;
    }

    public void setSp_storage_code(String sp_storage_code) {
        this.sp_storage_code = sp_storage_code;
    }
    public String getSp_storage_code() {
        return sp_storage_code;
    }

    public void setShipper(String shipper) {
        this.shipper = shipper;
    }
    public String getShipper() {
        return shipper;
    }

    public void setShipper_address_1(String shipper_address_1) {
        this.shipper_address_1 = shipper_address_1;
    }
    public String getShipper_address_1() {
        return shipper_address_1;
    }

    public void setShipper_address_2(String shipper_address_2) {
        this.shipper_address_2 = shipper_address_2;
    }
    public String getShipper_address_2() {
        return shipper_address_2;
    }

    public void setShipper_address_3(String shipper_address_3) {
        this.shipper_address_3 = shipper_address_3;
    }
    public String getShipper_address_3() {
        return shipper_address_3;
    }

    public void setShipper_city(String shipper_city) {
        this.shipper_city = shipper_city;
    }
    public String getShipper_city() {
        return shipper_city;
    }

    public void setShipper_county_state(String shipper_county_state) {
        this.shipper_county_state = shipper_county_state;
    }
    public String getShipper_county_state() {
        return shipper_county_state;
    }

    public void setShipper_zip(String shipper_zip) {
        this.shipper_zip = shipper_zip;
    }
    public String getShipper_zip() {
        return shipper_zip;
    }

    public void setShipper_country_code(String shipper_country_code) {
        this.shipper_country_code = shipper_country_code;
    }
    public String getShipper_country_code() {
        return shipper_country_code;
    }

    public void setConsignee(String consignee) {
        this.consignee = consignee;
    }
    public String getConsignee() {
        return consignee;
    }

    public void setAddress1(String address1) {
        this.address1 = address1;
    }
    public String getAddress1() {
        return address1;
    }

    public void setAddress2(String address2) {
        this.address2 = address2;
    }
    public String getAddress2() {
        return address2;
    }

    public void setAddress3(String address3) {
        this.address3 = address3;
    }
    public String getAddress3() {
        return address3;
    }

    public void setCity(String city) {
        this.city = city;
    }
    public String getCity() {
        return city;
    }

    public void setProvince(String province) {
        this.province = province;
    }
    public String getProvince() {
        return province;
    }

    public void setProvince_code(String province_code) {
        this.province_code = province_code;
    }
    public String getProvince_code() {
        return province_code;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }
    public String getZip() {
        return zip;
    }

    public void setCountry_code(String country_code) {
        this.country_code = country_code;
    }
    public String getCountry_code() {
        return country_code;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    public String getEmail() {
        return email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
    public String getPhone() {
        return phone;
    }

    public void setPieces(int pieces) {
        this.pieces = pieces;
    }
    public int getPieces() {
        return pieces;
    }

    public void setTotal_weight(String total_weight) {
        this.total_weight = total_weight;
    }
    public String getTotal_weight() {
        return total_weight;
    }

    public void setWeight_uom(String weight_uom) {
        this.weight_uom = weight_uom;
    }
    public String getWeight_uom() {
        return weight_uom;
    }

    public void setLength(String length) {
        this.length = length;
    }
    public String getLength() {
        return length;
    }

    public void setWidth(String width) {
        this.width = width;
    }
    public String getWidth() {
        return width;
    }

    public void setHeight(String height) {
        this.height = height;
    }
    public String getHeight() {
        return height;
    }

    public void setDimension_uom(String dimension_uom) {
        this.dimension_uom = dimension_uom;
    }
    public String getDimension_uom() {
        return dimension_uom;
    }

    public void setTotal_value(double total_value) {
        this.total_value = total_value;
    }
    public double getTotal_value() {
        return total_value;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
    public String getCurrency() {
        return currency;
    }

    public void setIncoterms(String incoterms) {
        this.incoterms = incoterms;
    }
    public String getIncoterms() {
        return incoterms;
    }

    public void setItem_description(String item_description) {
        this.item_description = item_description;
    }
    public String getItem_description() {
        return item_description;
    }

    public void setItem_hs_code(String item_hs_code) {
        this.item_hs_code = item_hs_code;
    }
    public String getItem_hs_code() {
        return item_hs_code;
    }

    public void setItem_quantity(int item_quantity) {
        this.item_quantity = item_quantity;
    }
    public int getItem_quantity() {
        return item_quantity;
    }

    public void setItem_value(double item_value) {
        this.item_value = item_value;
    }
    public double getItem_value() {
        return item_value;
    }

    public void setCountry_of_origin(String country_of_origin) {
        this.country_of_origin = country_of_origin;
    }
    public String getCountry_of_origin() {
        return country_of_origin;
    }

    public void setSecond_delivery_sn(String second_delivery_sn) {
        this.second_delivery_sn = second_delivery_sn;
    }
    public String getSecond_delivery_sn() {
        return second_delivery_sn;
    }

    public void setResend_req_no(String resend_req_no) {
        this.resend_req_no = resend_req_no;
    }
    public String getResend_req_no() {
        return resend_req_no;
    }

    public void setFailed_reason_type(int failed_reason_type) {
        this.failed_reason_type = failed_reason_type;
    }
    public int getFailed_reason_type() {
        return failed_reason_type;
    }

    public void setEstimate_deliver_time(String estimate_deliver_time) {
        this.estimate_deliver_time = estimate_deliver_time;
    }
    public String getEstimate_deliver_time() {
        return estimate_deliver_time;
    }

    public void setStoraged_warehouse(int storaged_warehouse) {
        this.storaged_warehouse = storaged_warehouse;
    }
    public int getStoraged_warehouse() {
        return storaged_warehouse;
    }

    public void setPod_qualified(int pod_qualified) {
        this.pod_qualified = pod_qualified;
    }
    public int getPod_qualified() {
        return pod_qualified;
    }

    public void setExpress_type(int express_type) {
        this.express_type = express_type;
    }
    public int getExpress_type() {
        return express_type;
    }

    public void setGoods_type(int goods_type) {
        this.goods_type = goods_type;
    }
    public int getGoods_type() {
        return goods_type;
    }

    public void setDanger_type(int danger_type) {
        this.danger_type = danger_type;
    }
    public int getDanger_type() {
        return danger_type;
    }

    public void setWeight_type(int weight_type) {
        this.weight_type = weight_type;
    }
    public int getWeight_type() {
        return weight_type;
    }

    public void setSize_type(int size_type) {
        this.size_type = size_type;
    }
    public int getSize_type() {
        return size_type;
    }

    public void setRequire_signature(int require_signature) {
        this.require_signature = require_signature;
    }
    public int getRequire_signature() {
        return require_signature;
    }

    public void setDispatch_type(String dispatch_type) {
        this.dispatch_type = dispatch_type;
    }
    public String getDispatch_type() {
        return dispatch_type;
    }

    public void setEnable_retry(int enable_retry) {
        this.enable_retry = enable_retry;
    }
    public int getEnable_retry() {
        return enable_retry;
    }

    public void setIncident_occurence_time(long incident_occurence_time) {
        this.incident_occurence_time = incident_occurence_time;
    }
    public long getIncident_occurence_time() {
        return incident_occurence_time;
    }

    public void setLast_operation_warehouse(int last_operation_warehouse) {
        this.last_operation_warehouse = last_operation_warehouse;
    }
    public int getLast_operation_warehouse() {
        return last_operation_warehouse;
    }

}