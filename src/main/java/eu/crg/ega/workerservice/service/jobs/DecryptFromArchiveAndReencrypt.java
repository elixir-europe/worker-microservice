package eu.crg.ega.workerservice.service.jobs;

import com.rabbitmq.client.Channel;

import eu.crg.ega.microservice.dto.message.WorkFlowCommandMessage;
import eu.crg.ega.microservice.dto.message.WorkFlowEventMessage;
import eu.crg.ega.microservice.enums.WorkflowType;
import eu.crg.ega.microservice.exception.WorkflowException;
import eu.crg.ega.workerservice.service.steps.helper.WorkflowFunctions;
import eu.crg.ega.workerservice.utils.openssl.OpenSSLPBEOutputStream;
import eu.crg.ega.workerservice.utils.pgp.PGPUtils;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.zip.GZIPOutputStream;

import javax.annotation.PostConstruct;
import javax.xml.bind.DatatypeConverter;

import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.log4j.Log4j;

@Setter
@Log4j
@Component
@Scope("prototype")
public class DecryptFromArchiveAndReencrypt implements Callable {

  private static final String ALGORITHM = "PBEWITHMD5AND256BITAES-CBC-OPENSSL";

  private static String STAGING_FILESYSTEM_PATH;

  private static String group = "distribGRP";

  //For queuing information
  private Channel channel;
  private long deliveryTag;

  //For having all the info of what to do
  private WorkFlowCommandMessage workFlowCommandMessage;

  private String secRingPath;
  private String keyPath;

  //Permissions
  @Setter(AccessLevel.NONE)
  private Set<PosixFilePermission> permissions =
      EnumSet.of(PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_WRITE,
          PosixFilePermission.GROUP_EXECUTE);

  @Setter(AccessLevel.NONE)
  @Autowired
  WorkflowFunctions workflowFunctions;

  @Setter(AccessLevel.NONE)
  @Autowired
  Environment env;

  @PostConstruct
  public void init() throws Exception {
    STAGING_FILESYSTEM_PATH = env.getProperty("service.worker.staging.location");
    group = env.getProperty("service.worker.group", "distribGRP");
  }

  @Override
  public Object call() throws Exception {
    String workflowid = workFlowCommandMessage.getWorkflowId();
    log.debug("Starting decrypt and reencrypt for " + workFlowCommandMessage.getSourceFile());
    WorkFlowEventMessage wfMessage = workflowFunctions.createWfEventMessage(workFlowCommandMessage);
    wfMessage.setPreviousState(WorkflowType.REENCRYPT);

    try {
      String unencryptedArchiveMd5 = workFlowCommandMessage.getResult();
      wfMessage.setResult(null);

      if (StringUtils.isBlank(unencryptedArchiveMd5)) {
        throw new WorkflowException(1, "Missing unencrypted md5 from archive file");
      }

      WorkFlowEventMessage processingMessage = workflowFunctions.createWfEventMessage(workFlowCommandMessage);
      processingMessage.setPreviousState(workFlowCommandMessage.getWorkflowSubtype());
      processingMessage.setWorkflowSubtype(WorkflowType.REENCRYPT_STARTED);
      workflowFunctions.sendMessage(WorkflowType.REENCRYPT_STARTED.getValue(), processingMessage);

      Integer keyId = workFlowCommandMessage.getDecryptKeyId();

      if (StringUtils.isBlank(workFlowCommandMessage.getReEncryptkey())) {
        throw new WorkflowException(6, "Missing reencryption key");
      }

      //Group
      UserPrincipalLookupService lookupService = FileSystems.getDefault()
          .getUserPrincipalLookupService();
      GroupPrincipal groupPrincipal = lookupService.lookupPrincipalByGroupName(group);

      //Create directory with group permissions
      Path directoryPath = Paths.get(STAGING_FILESYSTEM_PATH + workFlowCommandMessage.getSystemUser());
      try {
        Files.createDirectory(directoryPath, PosixFilePermissions.asFileAttribute(permissions));
        Files.getFileAttributeView(directoryPath, PosixFileAttributeView.class,
            LinkOption.NOFOLLOW_LINKS).setGroup(groupPrincipal);
        Files.setPosixFilePermissions(directoryPath, permissions);
      } catch (FileAlreadyExistsException fae) {
        //Silently ignore this exception
        //log.debug("File already exists", fae);
      } catch (UnsupportedOperationException uoe) {
        log.debug("Unsupported operation", uoe);
      }

      OutputStream output = null;
      InputStream sourceFile = null;
      InputStream secRingFile = null;
      String unencryptedCalculatedMd5 = null;
      try {
        if (keyId == 1) {
          //Read key to string
          String key = null;
          //key = new String(Files.readAllBytes(Paths.get(keyPath)), Charset.forName("UTF-8"));
          BufferedReader bufferedReader = null;
          try {
            bufferedReader = new BufferedReader(new FileReader(keyPath));
            key = bufferedReader.readLine();
            bufferedReader.close();
          } catch (IOException ioexc) {
            log.error("Error: ", ioexc);
            throw new WorkflowException(4, "Error getting passphrase");
          } finally {
            if (bufferedReader != null) {
              bufferedReader.close();
            }
          }
          if (key == null || key.length() == 0) {
            throw new WorkflowException(5, "Empty key!");
          }

          //Create md5 instance
          MessageDigest md = MessageDigest.getInstance("MD5");
          log.debug("key id is one!! -> for now it means GPG. Going to process " + workFlowCommandMessage.getTargetFile());
          //Create output stream, we are going to write in AES256
          BufferedOutputStream bufferoutput = new BufferedOutputStream(Files.newOutputStream(Paths.get(workFlowCommandMessage.getTargetFile())));
          CountingOutputStream countendCounter = new CountingOutputStream(bufferoutput);
          DigestOutputStream digestOutput = new DigestOutputStream(countendCounter, md);
          OpenSSLPBEOutputStream encrypted = new OpenSSLPBEOutputStream(digestOutput, ALGORITHM, 1, workFlowCommandMessage.getReEncryptkey().toCharArray());
          if (workFlowCommandMessage.getCompress()) {
            log.trace("File is going to be compressed");
            output = new GZIPOutputStream(encrypted, 4096);
          } else {
            output = encrypted;
          }
          Path sourceFilePath = Paths.get(workFlowCommandMessage.getSourceFile());
          //Check that sourceFile exists
          if (!Files.exists(sourceFilePath)) {
            throw new WorkflowException(7, "File does not exist: " + sourceFilePath.toString());
          }
          //Check that the source file size is not zero
          if (Files.size(sourceFilePath) == 0) {
            throw new WorkflowException(8, "File has zero length: " + sourceFilePath.toString());
          }
          sourceFile = Files.newInputStream(sourceFilePath);
          secRingFile = Files.newInputStream(Paths.get(secRingPath));

          unencryptedCalculatedMd5 = PGPUtils.decryptFileCalcMd5(sourceFile,
              output,
              secRingFile,
              key.toCharArray());
          // In case of gzip outputstream, we have to call finish in order to avoid generating
          // corrupt gzip files, if not just using flush() doesn't ensure all the data is written
          if (output instanceof GZIPOutputStream) {
            ((GZIPOutputStream) output).finish();
          }
          output.flush();
          long sizeEncrypted = countendCounter.getByteCount();
          String md5Encrypted = DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
          log.debug("Counted Bytes = " + sizeEncrypted);
          log.debug("Encrypted file md5 = " + md5Encrypted);
          wfMessage.setResult(sizeEncrypted + "," + md5Encrypted);
        } else { //if (keyId == 2) {
          throw new WorkflowException(3, "decrypt key number != 1, I don't know how to decrypt this");
        }

        log.debug("Archive unencrypted md5= " + unencryptedArchiveMd5 + " Calculated Md5 for Downloadbox Staging = " + unencryptedCalculatedMd5);

        //Check that internal md5 are equals
        if (!unencryptedArchiveMd5.equals(unencryptedCalculatedMd5)) {
          wfMessage.setResult(unencryptedCalculatedMd5);
          throw new WorkflowException(2, "Archive unencrypted md5= " + unencryptedArchiveMd5 + " Calculated Md5 for Downloadbox Staging = " + unencryptedCalculatedMd5);
        }

      } catch (Exception ex) {
        log.error("Error: ", ex);
        if (ex instanceof WorkflowException) {
          throw new WorkflowException(((WorkflowException) ex).getErrorCode(), ex.getMessage());
        } else {
          throw new WorkflowException(-1, ex.getMessage());
        }
      } finally {
        //In nested streams only the outer is necessary to close.
        if (output != null) {
          output.close();
        }
        if (sourceFile != null) {
          sourceFile.close();
        }
        if (secRingFile != null) {
          secRingFile.close();
        }
      }

      //Change the group of the file
      Path pathToFile = Paths.get(workFlowCommandMessage.getTargetFile());
      try {
        PosixFileAttributeView fileAttributeView = Files.getFileAttributeView(pathToFile,
            PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (fileAttributeView != null) {
          fileAttributeView.setGroup(groupPrincipal);
        } else {
          log.error("Problems changing file group");
        }
        Files.setPosixFilePermissions(pathToFile, permissions);
      } catch (UnsupportedOperationException uoe) {
        //Silently ignore this exception for windows
        log.debug("unsupported operation", uoe);
      }

      //send correct workflow event
      wfMessage.setWorkflowSubtype(WorkflowType.REENCRYPT_OK);
      log.debug("Going to send wf mesage: " + wfMessage);
      workflowFunctions.sendMessage(WorkflowType.REENCRYPT_OK.getValue(), wfMessage);

      log.debug("Going to ack message for workflowid: " + workflowid + " with deliveryTag " + deliveryTag);
      channel.basicAck(deliveryTag, true);
    } catch (Exception e) {
      log.error("Error: ", e);
      wfMessage.setErrorType(e.getMessage());
      wfMessage.setWorkflowSubtype(WorkflowType.REENCRYPT_FAIL);
      if (e instanceof WorkflowException) {
        wfMessage.setErrorCode(((WorkflowException) e).getErrorCode());
      } else {
        wfMessage.setErrorCode(-2);
      }
      try {
        workflowFunctions.sendMessage(WorkflowType.REENCRYPT_FAIL.getValue(), wfMessage);
      } catch (Exception ex) {
        log.fatal("Fatal: ", ex);
      } finally {
        //Reject the message
        channel.basicReject(deliveryTag, false);
      }
    }
    return null;
  }
}
