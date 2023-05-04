package com.uniuni.SysMgrTool.Response;

import java.util.List;
public class Data {
        private int count;
        private List<Orders> orders;
        public void setCount(int count) {
            this.count = count;
        }
        public int getCount() {
            return count;
        }

        public void setOrders(List<Orders> orders) {
            this.orders = orders;
        }
        public List<Orders> getOrders() {
            return orders;
        }

}
