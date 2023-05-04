package com.uniuni.SysMgrTool.Response;


import java.util.List;

public class OrderDetailData {
        private OrdersOfDetail orders;
        private Tracking tracking;
        private List<Path> path;
        private List<Incidents> incidents;
        private List<Operation> operation;
        private Calllog calllog;
        private List<Dispatches> dispatches;
        private String last_return_month;
        private ZipCodeInfo zipCodeInfo;
        public void setOrders(OrdersOfDetail orders) {
            this.orders = orders;
        }
        public OrdersOfDetail getOrders() {
            return orders;
        }

        public void setTracking(Tracking tracking) {
            this.tracking = tracking;
        }
        public Tracking getTracking() {
            return tracking;
        }

        public void setPath(List<Path> path) {
            this.path = path;
        }
        public List<Path> getPath() {
            return path;
        }

        public void setIncidents(List<Incidents> incidents) {
            this.incidents = incidents;
        }
        public List<Incidents> getIncidents() {
            return incidents;
        }

        public void setOperation(List<Operation> operation) {
            this.operation = operation;
        }
        public List<Operation> getOperation() {
            return operation;
        }

        public void setCalllog(Calllog calllog) {
            this.calllog = calllog;
        }
        public Calllog getCalllog() {
            return calllog;
        }

        public void setDispatches(List<Dispatches> dispatches) {
            this.dispatches = dispatches;
        }
        public List<Dispatches> getDispatches() {
            return dispatches;
        }

        public void setLast_return_month(String last_return_month) {
            this.last_return_month = last_return_month;
        }
        public String getLast_return_month() {
            return last_return_month;
        }

        public void setZipCodeInfo(ZipCodeInfo zipCodeInfo) {
            this.zipCodeInfo = zipCodeInfo;
        }
        public ZipCodeInfo getZipCodeInfo() {
            return zipCodeInfo;
        }

    }

