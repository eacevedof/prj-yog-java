package modules.shared.infrastructure.components.secrets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.sun.jna.platform.win32.Crypt32Util;

import core.boot.BootPathsEnum;

/**
 * Los secretos guardados en la BD local (feature 47): passwords de BD y SSH y passphrases. Nunca en claro:
 *   - Windows: DPAPI (CryptProtectData), atado al usuario de Windows: otro usuario u otro PC no lo descifra.
 *   - Resto: AES-256-GCM con una clave aleatoria en <JAVAYOG_HOME>/secret.key (permisos 600). Mas debil que un
 *     llavero del sistema (quien lea la carpeta tiene la clave): ver tech-debt.
 * El valor guardado lleva el metodo delante ("dpapi:", "aes:"); sin prefijo es un valor antiguo en claro y se devuelve
 * tal cual (el arranque los cifra, EncryptStoredSecretsService).
 */
public final class SecretCipherComponent {

    public static final String MODE_DPAPI = "dpapi";
    public static final String MODE_AES = "aes";

    private static final String KEY_FILE_NAME = "secret.key";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String mode;

    private SecretCipherComponent(String mode) {
        this.mode = mode;
    }

    /**
     * DPAPI en Windows, AES en el resto.
     */
    public static SecretCipherComponent getInstance() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        return new SecretCipherComponent(isWindows ? MODE_DPAPI : MODE_AES);
    }

    /**
     * Un modo concreto: los tests prueban los dos en cualquier sistema (DPAPI solo en Windows).
     */
    public static SecretCipherComponent getInstance(String mode) {
        return new SecretCipherComponent(mode);
    }

    /**
     * "" o null se guardan tal cual (no hay secreto).
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
        if (MODE_DPAPI.equals(mode)) {
            return MODE_DPAPI + ":" + Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(plainBytes));
        }
        return MODE_AES + ":" + Base64.getEncoder().encodeToString(getAesEncrypted(plainBytes));
    }

    /**
     * Descifra segun el prefijo (no segun el modo actual: una BD cifrada con AES se sigue leyendo en Windows).
     * @throws IllegalStateException si no se puede descifrar (otro usuario de Windows, secret.key cambiada)
     */
    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) return storedValue;
        try {
            if (storedValue.startsWith(MODE_DPAPI + ":")) {
                byte[] encrypted = Base64.getDecoder().decode(storedValue.substring(MODE_DPAPI.length() + 1));
                return new String(Crypt32Util.cryptUnprotectData(encrypted), StandardCharsets.UTF_8);
            }
            if (storedValue.startsWith(MODE_AES + ":")) {
                byte[] encrypted = Base64.getDecoder().decode(storedValue.substring(MODE_AES.length() + 1));
                return new String(getAesDecrypted(encrypted), StandardCharsets.UTF_8);
            }
        }
        catch (RuntimeException e) {
            throw new IllegalStateException("Could not decrypt a stored secret: " + e.getMessage(), e);
        }
        return storedValue;
    }

    public boolean isEncrypted(String storedValue) {
        return storedValue != null && (storedValue.startsWith(MODE_DPAPI + ":") || storedValue.startsWith(MODE_AES + ":"));
    }

    /**
     * iv (12 bytes) + texto cifrado con su etiqueta GCM.
     */
    private byte[] getAesEncrypted(byte[] plainBytes) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getAesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainBytes);
            byte[] ivAndCipher = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, ivAndCipher, 0, iv.length);
            System.arraycopy(cipherBytes, 0, ivAndCipher, iv.length, cipherBytes.length);
            return ivAndCipher;
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private byte[] getAesDecrypted(byte[] ivAndCipher) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getAesKey(), new GCMParameterSpec(GCM_TAG_BITS, ivAndCipher, 0, GCM_IV_BYTES));
            return cipher.doFinal(ivAndCipher, GCM_IV_BYTES, ivAndCipher.length - GCM_IV_BYTES);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * La clave AES; la primera vez se crea aleatoria con permisos 600 (donde el sistema de ficheros los entiende).
     */
    private SecretKeySpec getAesKey() {
        Path keyPath = Path.of(BootPathsEnum.getPathAppHome(), KEY_FILE_NAME);
        try {
            if (Files.notExists(keyPath)) {
                Files.createDirectories(keyPath.getParent());
                byte[] keyBytes = new byte[AES_KEY_BYTES];
                new SecureRandom().nextBytes(keyBytes);
                Files.writeString(keyPath, Base64.getEncoder().encodeToString(keyBytes));
                try {
                    Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
                }
                catch (UnsupportedOperationException e) {
                    // Windows: sin permisos POSIX (alli se usa DPAPI)
                }
            }
            return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(keyPath).trim()), "AES");
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
