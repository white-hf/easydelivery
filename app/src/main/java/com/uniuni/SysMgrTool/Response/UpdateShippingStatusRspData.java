package com.uniuni.SysMgrTool.Response;

public class UpdateShippingStatusRspData {
        private String status;
        private String ret_msg;
        private String err_code;

        private Data data;
        public void setStatus(String status) {
            this.status = status;
        }
        public String getStatus() {
            return status;
        }

        public void setRet_msg(String ret_msg) {
            this.ret_msg = ret_msg;
        }
        public String getRet_msg() {
            return ret_msg;
        }

        public void setErr_code(String err_code) {
            this.err_code = err_code;
        }
        public String getErr_code() {
            return err_code;
        }

        public void setData(Data data) {
            this.data = data;
        }
        public Data getData() {
            return data;
        }
}
