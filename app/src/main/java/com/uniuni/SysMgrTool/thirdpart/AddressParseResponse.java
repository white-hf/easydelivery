package com.uniuni.SysMgrTool.thirdpart;

public class AddressParseResponse {

    private String address1;
    private String address2;
    private Components components;
    private Metadata metadata;
    private Analysis analysis;
    public void setAddress1(String address1) {
        this.address1 = address1;
    }
    public String getAddress1() {
        return address1;
    }

    public void setAddress2(String address2) {
        this.address2 = address2;
    }
    public String getAddress2() {
        return address2;
    }

    public void setComponents(Components components) {
        this.components = components;
    }
    public Components getComponents() {
        return components;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
    public Metadata getMetadata() {
        return metadata;
    }

    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }
    public Analysis getAnalysis() {
        return analysis;
    }


}
