package com.ctseducare.ftpserver.server;

import java.io.IOException;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.ftpserver.ftplet.UserManager;

public class FtpletCustom extends DefaultFtplet {

  private FTPServerModule module = null;
  private UserManager userManager = null;

  public FtpletCustom(FTPServerModule module, UserManager userManager) {
    this.module = module;
    this.userManager = userManager;
  }
  
  @Override
  public FtpletResult onLogin(FtpSession session, FtpRequest request) throws FtpException, IOException {
    // ATTEMPTION: The object 'session.getUserArgument()' is null in this event.
    String ftpUsername = session.getUser().getName();
    System.out.println(String.format("User %s logged.", ftpUsername));
    return super.onLogin(session, request);
  }

  @Override
  public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
    String ftpUsername = session.getUser().getName();
    if (!userManager.doesExist(ftpUsername)) {
      return FtpletResult.DISCONNECT;
    }
    return super.onUploadStart(session, request);
  }

  @Override
  public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
    String username = session.getUser().getName();
    String directory = session.getUser().getHomeDirectory();
    String filename = request.getArgument();
    
    System.out.println(String.format("File received: %s", filename));
    
    FileData fileData = new FileData(username, directory, filename);
    module.processFileData(fileData);

    return super.onUploadEnd(session, request);
  }

  @Override
  public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException {
    // The object 'User' will be null when connection to FTP Server failed by password wrong, e.g
    if (session.getUser() != null) {
      String ftpUsername = session.getUser().getName();
      System.out.println(String.format("User %s disconnected", ftpUsername));
    }
    return super.onDisconnect(session);
  }

}
