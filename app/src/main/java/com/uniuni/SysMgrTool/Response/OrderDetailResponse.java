package com.uniuni.SysMgrTool.Response;

import static com.uniuni.SysMgrTool.Event.Event.EVENT_ORDER_DETAIL;

import android.os.Message;

import androidx.fragment.app.Fragment;

import com.uniuni.SysMgrTool.Event.Event;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.Task.TaskBase;

public class OrderDetailResponse extends ResponseBase implements TaskBase {
    private OrderDetailData data;

    public void setData(OrderDetailData data) {
        this.data = data;
    }

    public OrderDetailData getData() {
        return data;
    }

    @Override
    public void doIt(Message msg) {
        OrderDetailData d = this.getData();

        Event<OrderDetailData> eOrderDetailData = new Event<>(d);
        MySingleton.getInstance().getPublisher().notify(EVENT_ORDER_DETAIL, eOrderDetailData);

    }
}
