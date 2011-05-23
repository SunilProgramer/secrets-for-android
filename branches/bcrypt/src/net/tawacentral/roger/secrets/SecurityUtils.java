// Copyright (c) 2009, Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.tawacentral.roger.secrets;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.mindrot.jbcrypt.BCrypt;

import android.util.Log;

/**
 * Helper class to manage cipher keys and encrypting and decrypting data.
 *
 * @author rogerta
 */
public class SecurityUtils {
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets";

  // The following three constants were used with the initial implementation of
  // secrets.  Secrets now uses a more secure algorithm, but for backwards
  // compatibility, the program needs to be able to load the secrets with the
  // old algorithm.  However, secrets will only be saved with the new algorithm
  // moving forward.
  //
  // All members that end with V1 or v1 have to do with the old algorithm. 
  private static final String KEY_FACTORY_V1 =
	  "PBEWITHSHA-256AND256BITAES-CBC-BC";

  // TODO(rogerta): for now, I'm using an iteration count of 100.  I had
  // initially set it to 5000, but that took *ages* to create the cipher.  I
  // need to figure out if this is a safe value to use.
  private static final int KEY_ITERATION_COUNT_V1 = 100;

  // The salt can be hardcoded, because the secrets file is never transmitted
  // off the phone.  Generating a ramdom salt would not provide any real extra
  // protection, because if an attacker can get to the secrets file, then he
  // has broken into the phone, and therefore would be able to get to the
  // random salt too.
  private static final byte[] salt_v1 = {
    (byte)0xA4, (byte)0x0B, (byte)0xC8, (byte)0x34,
    (byte)0xD6, (byte)0x95, (byte)0xF3, (byte)0x13
  };

  private static final String KEY_FACTORY = "AES";
  
  /** Class used to time the execution of functions */
  static public class ExecutionTimer {
    private long start = System.currentTimeMillis();

    /** Returns the time in millisecs since the object was created. */
    public long getElapsed() {
      return System.currentTimeMillis() - start;
    }

    /** Prints the time in millisecs since the object was created to the log. */
    public void logElapsed(String message) {
      Log.i(LOG_TAG, message + getElapsed());
    }
  }

  private static Cipher encryptCipher;
  private static Cipher decryptCipher;
  private static byte[] salt;

  /**
   * Get the cipher used to encrypt data using the password given to the
   * createCiphers() function.
   */
  public static Cipher getEncryptionCipher() {
    return encryptCipher;
  }

  /**
   * Get the cipher used to decrypt data using the password given to the
   * createCiphers() function.
   */
  public static Cipher getDecryptionCipher() {
    return decryptCipher;
  }

  /**
   * Gets the salt for this device.
   * @return A byte array represent the salt for this specific device.
   */
  public static byte[] getSalt() {
    return salt;
  }

  /**
   * Creates a new unique random salt.
   * @return A new salt value used to generate the secret key. 
   */
  private static byte[] createNewSalt() {
    byte[] bytes = new byte[BCrypt.BCRYPT_SALT_LEN];
    SecureRandom random = new SecureRandom();
    random.nextBytes(bytes);
    return bytes;
  }
  
  /**
   * Create a decryption cipher using the old algorithm based on the given
   * password string.  The string is not stored internally.
   *
   * This method is used for backward compatibility only.
   * 
   * @param password String to use for creating the ciphers.
   * @return True if the ciphers were successfully created.
   */
  public static Cipher createDecryptionCipherV1(String password) {
    Cipher cipher = null;

    ExecutionTimer timer = new ExecutionTimer();

    try {
      PBEKeySpec keyspec = new PBEKeySpec(password.toCharArray(),
                                          salt_v1,
                                          KEY_ITERATION_COUNT_V1,
                                          32);
      SecretKeyFactory skf = SecretKeyFactory.getInstance(KEY_FACTORY_V1);
      SecretKey key = skf.generateSecret(keyspec);
      AlgorithmParameterSpec aps = new PBEParameterSpec(salt_v1,
                                                        KEY_ITERATION_COUNT_V1);
      cipher = Cipher.getInstance(KEY_FACTORY_V1);
      cipher.init(Cipher.DECRYPT_MODE, key, aps);
    } catch (Exception ex) {
      Log.d(LOG_TAG, "createDecryptionCiphersV1", ex);
      cipher = null;
    }

    timer.logElapsed("Time to create V1 d-cipher: ");
    return cipher;
  }

  /**
   * Create a pair of encryption and decryption ciphers based on the given
   * password string.  The string is not stored internally.  This function
   * needs to be called before calling getEncryptionCipher() or
   * getDecryptionCipher().
   *
   * @param password String to use for creating the ciphers.
   * @param saltBytes The salt to use when creating the encryption key.
   * @return True if the ciphers were successfully created.
   */
  public static boolean createCiphers(String password, byte[] saltBytes) {
    boolean succeeded = false;

    ExecutionTimer timer = new ExecutionTimer();

    try {
      if (saltBytes == null)
        saltBytes = createNewSalt();
      
      int plaintext[] = {0x155cbf8e, 0x57f57513, 0x3da787b9, 0x71679d82,
                         0x7cf72e93, 0x1ae25274, 0x64b54adc, 0x335cbd0b};
      BCrypt bcrypt = new BCrypt();
      byte[] rawBytes = bcrypt.crypt_raw(password.getBytes("UTF-8"),
                                         saltBytes, 10, plaintext);
      SecretKeySpec spec = new SecretKeySpec(rawBytes, KEY_FACTORY);
      encryptCipher = Cipher.getInstance(KEY_FACTORY);
      encryptCipher.init(Cipher.ENCRYPT_MODE, spec);

      decryptCipher = Cipher.getInstance(KEY_FACTORY);
      decryptCipher.init(Cipher.DECRYPT_MODE, spec);

      salt = saltBytes;
      succeeded = true;
    } catch (Exception ex) {
      Log.d(LOG_TAG, "createCiphers", ex);
      encryptCipher = null;
      decryptCipher = null;
      salt = null;
    }

    timer.logElapsed("Time to create cihpers: ");
    return succeeded;
  }

  /** Clear the ciphers from memory. */
  public static void clearCiphers() {
    decryptCipher = null;
    encryptCipher = null;
    salt = null;
  }

  public static void timeCreateBcryptRawBytes(int count) {
    ExecutionTimer timer = new ExecutionTimer();
    try {
      byte[] saltBytes = createNewSalt();
      
      int plaintext[] = {0x155cbf8e, 0x57f57513, 0x3da787b9, 0x71679d82,
                         0x7cf72e93, 0x1ae25274, 0x64b54adc, 0x335cbd0b};
      BCrypt bcrypt = new BCrypt();
      bcrypt.crypt_raw("12345678".getBytes("UTF-8"), saltBytes, count,
                       plaintext);
    } catch (UnsupportedEncodingException ex) {
      Log.e(LOG_TAG, "testCreateBcryptRawBytes", ex);
      ex.printStackTrace();
    }

    timer.logElapsed("Time to create ciphers count=" + count + ": ");
  }

  /** This method returns all available services types. */
  /*public static String[] getServiceTypes() {
      java.util.HashSet result = new HashSet();
      // All all providers.
      java.security.Provider[] providers =
          java.security.Security.getProviders();
      for (int i = 0; i < providers.length; ++i) {
          // Get services provided by each provider.
          java.util.Set keys = providers[i].keySet();
          for (Iterator it=keys.iterator(); it.hasNext(); ) {
              String key = (String)it.next();
              key = key.split(" ")[0];
              if (key.startsWith("Alg.Alias.")) {
                  // Strip the alias.
                  key = key.substring(10);
              }
              int ix = key.indexOf('.');
              result.add(key.substring(0, ix));
          }
      }
      return (String[])result.toArray(new String[result.size()]);
  }*/

  /** This method returns the available implementations for a service type. */
  /*public static String[] getCryptoImpls(String serviceType) {
      java.util.HashSet<String> result = new java.util.HashSet<String>();
      // All all providers.
      java.security.Provider[] providers =
          java.security.Security.getProviders();
      for (int i = 0; i < providers.length; ++i) {
          // Get services provided by each provider.
          java.util.Set<Object> keys = providers[i].keySet();
          for (Object o : keys) {
            String key = ((String)o).split(" ")[0];
            if (key.startsWith(serviceType+".")) {
                result.add(key.substring(serviceType.length()+1));
            } else if (key.startsWith("Alg.Alias."+serviceType+".")) {
                // This is an alias
                result.add(key.substring(serviceType.length()+11));
            }
          }
      }
      return (String[])result.toArray(new String[result.size()]);
  }*/
}