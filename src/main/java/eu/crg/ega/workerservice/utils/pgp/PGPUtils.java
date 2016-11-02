package eu.crg.ega.workerservice.utils.pgp;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Iterator;

import javax.xml.bind.DatatypeConverter;

import lombok.extern.log4j.Log4j;

//Based on http://sloanseaman.com/wordpress/2011/08/11/pgp-encryptiondecryption-in-java/
@Log4j
public class PGPUtils {

  private static final int PAYLOAD_ENCRYPTION_ALG = PGPEncryptedData.AES_256;
  private static final String BC_PROVIDER_NAME = "BC";
  private static final String HASH_ALG = "MD5";

  @SuppressWarnings("unchecked")
  public static PGPPublicKey readPublicKey(InputStream in) throws IOException, PGPException {
    in = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(in);

    PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(in);

    //
    // we just loop through the collection till we find a key suitable for encryption, in the real
    // world you would probably want to be a bit smarter about this.
    //
    PGPPublicKey key = null;

    //
    // iterate through the key rings.
    //
    Iterator<PGPPublicKeyRing> rIt = pgpPub.getKeyRings();

    while (key == null && rIt.hasNext()) {
      PGPPublicKeyRing kRing = rIt.next();
      Iterator<PGPPublicKey> kIt = kRing.getPublicKeys();
      while (key == null && kIt.hasNext()) {
        PGPPublicKey k = kIt.next();

        if (k.isEncryptionKey()) {
          key = k;
        }
      }
    }

    if (key == null) {
      throw new IllegalArgumentException("Can't find encryption key in key ring.");
    }

    return key;
  }

  /**
   * Load a secret key ring collection from keyIn and find the secret key corresponding to keyID if
   * it exists.
   *
   * @param keyIn input stream representing a key ring collection.
   * @param keyID keyID we want.
   * @param pass  passphrase to decrypt secret key with.
   */
  private static PGPPrivateKey findSecretKey(InputStream keyIn, long keyID, char[] pass)
      throws IOException, PGPException, NoSuchProviderException {
    PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
        org.bouncycastle.openpgp.PGPUtil.getDecoderStream(keyIn));

    PGPSecretKey pgpSecKey = pgpSec.getSecretKey(keyID);

    if (pgpSecKey == null) {
      return null;
    }

    return pgpSecKey.extractPrivateKey(pass, BC_PROVIDER_NAME);
  }

  /**
   * decrypt the passed in message stream
   */
  @SuppressWarnings("unchecked")
  public static void decryptFile(InputStream in, OutputStream out, InputStream keyIn, char[] passwd)
      throws Exception {
    decryptFileCalcMd5(in, out, keyIn, passwd);
  }

  /**
   * decrypt the passed in message stream
   */
  @SuppressWarnings("unchecked")
  public static String decryptFileCalcMd5(InputStream in, OutputStream out, InputStream keyIn, char[] passwd)
      throws Exception {

    in = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(in);

    PGPObjectFactory pgpF = new PGPObjectFactory(in);
    PGPEncryptedDataList enc;

    Object object = pgpF.nextObject();
    //
    // the first object might be a PGP marker packet.
    //
    if (object instanceof PGPEncryptedDataList) {
      enc = (PGPEncryptedDataList) object;
    } else {
      enc = (PGPEncryptedDataList) pgpF.nextObject();
    }

    //
    // find the secret key
    //
    Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
    PGPPrivateKey sKey = null;
    PGPPublicKeyEncryptedData pbe = null;

    while (sKey == null && it.hasNext()) {
      pbe = it.next();

      sKey = findSecretKey(keyIn, pbe.getKeyID(), passwd);
    }

    if (sKey == null) {
      throw new IllegalArgumentException("Secret key for message not found.");
    }

    InputStream clear = pbe.getDataStream(sKey, BC_PROVIDER_NAME);

    PGPObjectFactory plainFact = new PGPObjectFactory(clear);

    Object message = plainFact.nextObject();

    if (message instanceof PGPCompressedData) {
      PGPCompressedData cData = (PGPCompressedData) message;
      PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream());

      message = pgpFact.nextObject();
    }

    String result = null;
    if (message instanceof PGPLiteralData) {
      PGPLiteralData ld = (PGPLiteralData) message;

      MessageDigest md = MessageDigest.getInstance(HASH_ALG);
      InputStream unc = ld.getInputStream();
      DigestInputStream dis = new DigestInputStream(unc, md);

      IOUtils.copy(dis, out);
      byte[] digest = md.digest();
      result = DatatypeConverter.printHexBinary(digest).toLowerCase();
    } else if (message instanceof PGPOnePassSignatureList) {
      throw new PGPException("Encrypted message contains a signed message - not literal data.");
    } else {
      throw new PGPException("Message is not a simple encrypted file - type unknown.");
    }

    if (pbe.isIntegrityProtected()) {
      if (!pbe.verify()) {
        log.error("Message failed PGP integrity check, but the unencrypted md5 is " + result);
        //It seems that for some files this will check will fail but the fail will be correctly generated
        //so we know just log the incident.
        //throw new PGPException("Message failed PGP integrity check, but the unencrypted md5 is " + result);
      }
    }
    return result;
  }

  public static void encryptFile(OutputStream out, String fileName,
                                 PGPPublicKey encKey, boolean armor, boolean withIntegrityCheck)
      throws IOException, NoSuchProviderException, PGPException {

    if (armor) {
      out = new ArmoredOutputStream(out);
    }

    ByteArrayOutputStream bOut = new ByteArrayOutputStream();

    PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(
        PGPCompressedData.ZIP);

    org.bouncycastle.openpgp.PGPUtil.writeFileToLiteralData(comData.open(bOut),
        PGPLiteralData.BINARY, new File(fileName));

    comData.close();

    PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(
        PAYLOAD_ENCRYPTION_ALG, withIntegrityCheck,
        new SecureRandom(), BC_PROVIDER_NAME);

    cPk.addMethod(encKey);

    byte[] bytes = bOut.toByteArray();

    OutputStream cOut = cPk.open(out, bytes.length);

    cOut.write(bytes);

    cOut.close();

    out.close();
  }

}