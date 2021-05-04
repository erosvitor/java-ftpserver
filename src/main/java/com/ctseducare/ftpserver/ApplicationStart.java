package com.ctseducare.ftpserver;

import com.ctseducare.ftpserver.server.FTPServerModule;

public class ApplicationStart {

  public static void main(String[] args) {
    FTPServerModule.instance().startModule();
  }

}
