package com.uniuni.SysMgrTool.Response;

public class ResponseBase {
       private String status;
       private String ret_msg;
       private Integer err_code;

       public String getStatus() {
              return status;
       }

       public void setStatus(String status) {
              this.status = status;
       }

       public String getRet_msg() {
              return ret_msg;
       }

       public void setRet_msg(String ret_msg) {
              this.ret_msg = ret_msg;
       }

       public Integer getErr_code() {
              return err_code;
       }

       public void setErr_code(Integer err_code) {
              this.err_code = err_code;
       }

       public boolean isSuccess()
       {
              if (status.equalsIgnoreCase("SUCCESS"))
                     return true;
              else
                     return false;
       }
}
