package com.uniuni.SysMgrTool.Response;

public class Calllog {
        private long id;
        private int type;
        private String order_sn;
        private String phone;
        private long call_time;
        private String call_id;
        private int retries;
        private String duration;
        private long msg_time;
        public void setId(long id) {
            this.id = id;
        }
        public long getId() {
            return id;
        }

        public void setType(int type) {
            this.type = type;
        }
        public int getType() {
            return type;
        }

        public void setOrder_sn(String order_sn) {
            this.order_sn = order_sn;
        }
        public String getOrder_sn() {
            return order_sn;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
        public String getPhone() {
            return phone;
        }

        public void setCall_time(long call_time) {
            this.call_time = call_time;
        }
        public long getCall_time() {
            return call_time;
        }

        public void setCall_id(String call_id) {
            this.call_id = call_id;
        }
        public String getCall_id() {
            return call_id;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
        public int getRetries() {
            return retries;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }
        public String getDuration() {
            return duration;
        }

        public void setMsg_time(long msg_time) {
            this.msg_time = msg_time;
        }
        public long getMsg_time() {
            return msg_time;
        }

}
