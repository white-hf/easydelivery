package com.uniuni.SysMgrTool.Response;

public class ZipCodeInfo {
        private boolean isZipCovered;
        private boolean isBlocked;
        private String blockDate;
        public void setIsZipCovered(boolean isZipCovered) {
            this.isZipCovered = isZipCovered;
        }
        public boolean getIsZipCovered() {
            return isZipCovered;
        }

        public void setIsBlocked(boolean isBlocked) {
            this.isBlocked = isBlocked;
        }
        public boolean getIsBlocked() {
            return isBlocked;
        }

        public void setBlockDate(String blockDate) {
            this.blockDate = blockDate;
        }
        public String getBlockDate() {
            return blockDate;
        }

    }