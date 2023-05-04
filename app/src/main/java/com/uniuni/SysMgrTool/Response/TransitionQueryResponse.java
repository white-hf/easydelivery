package com.uniuni.SysMgrTool.Response;

import java.util.List;

public class TransitionQueryResponse extends ResponseBase{
    public List<TransitionData> getData() {
        return data;
    }

    public void setData(List<TransitionData> data) {
        this.data = data;
    }

    private List<TransitionData> data;
}
