package com.ctseducare.ftpserver.server;

public class FileData {
  
  private String username;
  private String directory;
  private String filename;
  
  public FileData() {
    
  }

  public FileData(String username, String directory, String filename) {
    this.username = username;
    this.directory = directory;
    this.filename = filename;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }
  
}
