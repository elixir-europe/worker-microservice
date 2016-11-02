package eu.crg.ega.workerservice.utils.openssl;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/*http://stackoverflow.com/a/15735332 */
public class OpenSSLPBEOutputStream extends OutputStream {

  private static final int BUFFER_SIZE = 5 * 1024 * 1024;

  private final Cipher cipher;
  private final OutputStream outStream;
  private final byte[] buffer = new byte[BUFFER_SIZE];
  private int bufferIndex = 0;

  public OpenSSLPBEOutputStream(final OutputStream outputStream, String algIn, int iterationCount,
                                char[] password) throws IOException {
    outStream = outputStream;
    try {
        /* Create and use a random SALT for each instance of this output stream. */
      byte[] salt = new byte[OpenSSLPBECommon.SALT_SIZE_BYTES];
      new SecureRandom().nextBytes(salt);
      cipher = OpenSSLPBECommon.initializeCipher(password, salt, Cipher.ENCRYPT_MODE, algIn, iterationCount);
        /* Write header */
      writeHeader(salt);
    } catch (InvalidKeySpecException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void write(int b) throws IOException {
    buffer[bufferIndex] = (byte) b;
    bufferIndex++;
    if (bufferIndex == BUFFER_SIZE) {
      byte[] result = cipher.update(buffer, 0, bufferIndex);
      outStream.write(result);
      bufferIndex = 0;
    }
  }

  @Override
  public void flush() throws IOException {
    if (bufferIndex > 0) {
      byte[] result;
      try {
        result = cipher.doFinal(buffer, 0, bufferIndex);
        outStream.write(result);
      } catch (IllegalBlockSizeException | BadPaddingException e) {
        throw new IOException(e);
      }
      bufferIndex = 0;
    }
  }

  @Override
  public void close() throws IOException {
    flush();
    outStream.close();
  }

  private void writeHeader(byte[] salt) throws IOException {
    outStream.write(OpenSSLPBECommon.OPENSSL_HEADER_STRING.getBytes(OpenSSLPBECommon.OPENSSL_HEADER_ENCODE));
    outStream.write(salt);
  }

}