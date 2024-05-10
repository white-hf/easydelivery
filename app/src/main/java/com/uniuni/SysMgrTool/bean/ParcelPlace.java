package com.uniuni.SysMgrTool.bean;

public class ParcelPlace {

        private int code;
        private String streetOrApartment;
        private String address;

        public ParcelPlace(int code, String streetOrApartment, String address) {
            this.code = code;
            this.streetOrApartment = streetOrApartment;
            this.address = address;
        }

        public int getCode() {
            return code;
        }

        public String getStreetOrApartment() {
            return streetOrApartment;
        }

        public String getAddress() {
            return address;
        }

}
