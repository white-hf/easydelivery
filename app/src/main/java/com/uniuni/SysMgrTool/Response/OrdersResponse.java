package com.uniuni.SysMgrTool.Response;

public class OrdersResponse extends ResponseBase{
        private Data data;
        public void setData(Data data) {
            this.data = data;
        }
        public Data getData() {
            return data;
        }

    }
