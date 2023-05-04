package com.uniuni.SysMgrTool.Response;

public class OrderDetailResponse  extends ResponseBase{
        private OrderDetailData data;
        public void setData(OrderDetailData data) {
            this.data = data;
        }
        public OrderDetailData getData() {
            return data;
        }

}
