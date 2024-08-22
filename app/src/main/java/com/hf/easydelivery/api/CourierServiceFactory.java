package com.hf.easydelivery.api;

import com.hf.courierservice.ICourierService;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.common.ConfigurationManager;

public class CourierServiceFactory {
    public static ICourierService createCourierService() {
        String courier = "";
        try {
            ConfigurationManager configManager = ResourceMgr.getInstance().getConfigurationManager();
            courier = configManager.getString("courier" , "");

            if (courier.isEmpty()) {
                return null;
            }

            Class<?> clazz = Class.forName("com.hf." + courier.toLowerCase() + "."  + "CourierService");
            return (ICourierService) clazz.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported courier service: " + courier, e);
        }
    }
}
