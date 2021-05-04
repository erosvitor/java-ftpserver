package com.ctseducare.ftpserver.server;

public class FTPServerProcess implements Runnable {

  private Object fileData = null;
  
  public FTPServerProcess(FileData fileData) {
    this.fileData = fileData;
  }

  private void processFileData(Object fileData) {
    //TODO Process the file (store in filesystem or send to another process)
  }

  @Override
  public void run() {
    processFileData(fileData);
  }

}
