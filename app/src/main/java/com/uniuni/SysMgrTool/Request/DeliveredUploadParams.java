package com.uniuni.SysMgrTool.Request;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * This class represents the parameters for the delivered packages upload request.
 */
public class DeliveredUploadParams {
       private String url;
        private String authorization;
        private Map<String, String> formFields;
        private List<File> imageFiles;

        // Getters and Setters
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getAuthorization() {
            return authorization;
        }

        public void setAuthorization(String authorization) {
            this.authorization = authorization;
        }

        public Map<String, String> getFormFields() {
            return formFields;
        }

        public void setFormFields(Map<String, String> formFields) {
            this.formFields = formFields;
        }

        public List<File> getImageFiles() {
            return imageFiles;
        }

        public void setImageFiles(List<File> imageFiles) {
            this.imageFiles = imageFiles;
        }
    }
