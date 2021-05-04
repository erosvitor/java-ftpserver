package com.ctseducare.ftpserver.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.ftpserver.usermanager.PasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

public class FTPServerModule {

  private static final String FTPSERVER_USER_FOLDER = "/opt/ftpserver/users";
  private static final int FTPSERVER_PORT = 4466;
  private static final String FTPSERVER_PROTOCOL = "ftp"; // or 'ftps'
  private static final String FTPSERVER_MODE = "passive"; // or 'active'

  private static FTPServerModule instance = null;
  private FtpServer ftpServer = null;
  private UserManager userManager = null;

  private int ftpPort;
  private String ftpProtocol;
  private String ftpMode;

  private ThreadPoolExecutor threadPoolExecutor;
  private int numberOfCoreProcess;
  private int maxNumberOfProcess;
  private long maxIdelTime = 60;
  private static int threadID = 0;

  private FTPServerModule() {
    int processorsNumber = Runtime.getRuntime().availableProcessors();
    if (processorsNumber < 1) {
      processorsNumber = 1;
    }
    numberOfCoreProcess = processorsNumber;
    maxNumberOfProcess = processorsNumber;
  }

  public static FTPServerModule instance() {
    if (instance == null) {
      instance = new FTPServerModule();
    }
    return instance;
  }

  public boolean startModule() {
    if (this.threadPoolExecutor != null && !this.threadPoolExecutor.isShutdown()) {
      stopModule();
    }
    this.threadPoolExecutor = new ThreadPoolExecutor(
       numberOfCoreProcess,
       maxNumberOfProcess,
       maxIdelTime,
       TimeUnit.SECONDS,
       new LinkedBlockingQueue<Runnable>(),
       new ThreadFactory() {
         private synchronized int getid() {
           threadID++;
           return threadID % maxNumberOfProcess;
         }
         @Override
         public Thread newThread(Runnable runnable) {
           return new Thread(runnable, String.format("FTPServerProcess(%d)", getid()));
         }
       }
    );
    this.threadPoolExecutor.allowCoreThreadTimeOut(true);

    this.ftpPort = FTPSERVER_PORT;
    this.ftpProtocol = FTPSERVER_PROTOCOL;
    this.ftpMode = FTPSERVER_MODE;
    return startFtpServer();
  }

  private boolean startFtpServer() {
    FtpServerFactory serverFactory = new FtpServerFactory();

    // Set FTP server port
    ListenerFactory listenerFactory = new ListenerFactory();
    listenerFactory.setPort(this.ftpPort);

    // Set the interface range of passive mode data upload, and the ECS needs to open the corresponding port to the client
    if (this.ftpMode.equals("passive")) {
      DataConnectionConfigurationFactory dataConnectionConfFactory = new DataConnectionConfigurationFactory();
      dataConnectionConfFactory.setPassivePorts("10000-10500");
      listenerFactory.setDataConnectionConfiguration(dataConnectionConfFactory.createDataConnectionConfiguration());
    }

    // Add SSL security configuration
    if (this.ftpProtocol.equals("ftps")) {
      File keyStoreFile = new File("/opt/ftpserver/ftpserver.jks");
      if (keyStoreFile.exists()) {
        SslConfigurationFactory ssl = new SslConfigurationFactory();
        ssl.setKeystoreFile(keyStoreFile);
        ssl.setKeystorePassword("ftpserver");
        ssl.setSslProtocol("SSL");
        listenerFactory.setSslConfiguration(ssl.createSslConfiguration());
        listenerFactory.setImplicitSsl(true);
      } else {
        System.out.println("The KeyStore file does not exists in /opt/ftpserver");
        return false;
      }
    }

    // Replace the default listener
    Listener listener = listenerFactory.createListener();
    serverFactory.addListener("default", listener);

    // Define user's directory
    File dir = new File(FTPSERVER_USER_FOLDER);
    if (!dir.exists() && !dir.mkdirs()) {
      System.out.println(String.format("Can't create users directory (%s).", FTPSERVER_USER_FOLDER));
      return false;
    }

    // Read user's configuration
    PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
    userManagerFactory.setAdminName("ctseducare");
    userManagerFactory.setPasswordEncryptor(new PasswordEncryptor() {
      @Override
      public String encrypt(String password) {
        return password;
      }
      @Override
      public boolean matches(String passwordToCheck, String storedPassword) {
        //String passwordInMD5 = PxMD5.generate(passwordToCheck);
        String passwordInMD5 = passwordToCheck;
        return passwordInMD5.equals(storedPassword);
      }
    });
    userManager = userManagerFactory.createUserManager();
    loadUsers(userManager);
    serverFactory.setUserManager(userManager);

    // Configure custom user events
    Map<String, Ftplet> ftpLets = new HashMap<>();
    ftpLets.put("ftpService", new FtpletCustom(FTPServerModule.instance, userManager));
    serverFactory.setFtplets(ftpLets);

    // Instantiate FTP Server
    this.ftpServer = serverFactory.createServer();
    try {
      this.ftpServer.start();
      return true;
    } catch (FtpException e) {
      e.printStackTrace();
      return false;
    }
  }

  private void loadUsers(UserManager userManager) {
    try {
      User u = createUser("ftpuser1", "ftpuser1");
      if (u != null) {
        userManager.save(u);
      }
    } catch (FtpException e) {
      e.printStackTrace();
    }
  }

  private User createUser(String username, String password) {
    String dirUser = FTPSERVER_USER_FOLDER + "/" + username;
    File dir = new File(dirUser);
    if (dir.exists()) {
      try {
        FileUtils.cleanDirectory(dir);
      } catch (IOException e) {
        System.out.println(String.format("Can't empty the directory " + dirUser));
      }
    } else {
      if (!dir.mkdirs()) {
        System.out.println(String.format("Can't create user directory (%s). The user (%s) can't send files to FTP Server.", dirUser, username));
        return null;
      }
    }

    List<Authority> authorities = new ArrayList<>();
    authorities.add(new WritePermission());

    BaseUser baseUser = new BaseUser();
    baseUser.setName(username);
    baseUser.setPassword(password);
    baseUser.setHomeDirectory(dirUser);
    baseUser.setAuthorities(authorities);

    return baseUser;
  }

  public boolean stopModule() {
    this.ftpServer.stop();

    if (this.threadPoolExecutor != null) {
      this.threadPoolExecutor.shutdown();
    }
    this.threadPoolExecutor = null;

    return true;
  }
  
  public void processFileData(FileData fileData) {
    this.threadPoolExecutor.execute(new FTPServerProcess(fileData));
  }

}
