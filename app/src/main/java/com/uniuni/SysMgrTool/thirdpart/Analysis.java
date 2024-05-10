package com.uniuni.SysMgrTool.thirdpart;

public class Analysis {
        private String verification_status;
        private String address_precision;
        private String max_address_precision;
        private Changes changes;
        public void setVerification_status(String verification_status) {
            this.verification_status = verification_status;
        }
        public String getVerification_status() {
            return verification_status;
        }

        public void setAddress_precision(String address_precision) {
            this.address_precision = address_precision;
        }
        public String getAddress_precision() {
            return address_precision;
        }

        public void setMax_address_precision(String max_address_precision) {
            this.max_address_precision = max_address_precision;
        }
        public String getMax_address_precision() {
            return max_address_precision;
        }

        public void setChanges(Changes changes) {
            this.changes = changes;
        }
        public Changes getChanges() {
            return changes;
        }
}
