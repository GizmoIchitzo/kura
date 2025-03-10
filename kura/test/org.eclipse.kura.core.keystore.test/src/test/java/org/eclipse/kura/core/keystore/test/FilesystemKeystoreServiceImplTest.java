/*******************************************************************************
 * Copyright (c) 2021 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.core.keystore.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.security.auth.x500.X500Principal;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.core.keystore.FilesystemKeystoreServiceImpl;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.system.SystemService;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.EventAdmin;

public class FilesystemKeystoreServiceImplTest {

    private static final String DEFAULT_KEY_ALIAS = "alias";
    private static final String CERT_FILE_PATH = "target/test-classes/cert";
    private static final String KEY_KEYSTORE_PATH = "keystore.path";
    private static final String KEY_KEYSTORE_PASSWORD = "keystore.password";
    private static final String KEY_RANDOMIZE_PASSWORD = "randomize.password";

    private static final String STORE_PATH = "target/key.store";
    private static final String STORE_PASS = "pass";

    private KeyStore store;

    @Before
    public void setupDefaultKeystore()
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        // create a new keystore each time the tests should run

        this.store = KeyStore.getInstance("jks");

        this.store.load(null, null);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        gen.initialize(1024);
        KeyPair pair = gen.generateKeyPair();
        Key key = pair.getPrivate();

        InputStream is = new FileInputStream(CERT_FILE_PATH);
        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(is);
        is.close();

        Certificate[] chain = { certificate };

        this.store.setKeyEntry(DEFAULT_KEY_ALIAS, key, STORE_PASS.toCharArray(), chain);

        try (OutputStream os = new FileOutputStream(STORE_PATH)) {
            this.store.store(os, STORE_PASS.toCharArray());
        }
    }

    @Test
    public void testGetKeyStore() throws KuraException, KeyStoreException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        KeyStore keystore = keystoreService.getKeyStore();

        assertNotNull(keystore);
        assertEquals(Collections.list(this.store.aliases()), Collections.list(keystore.aliases()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEntryNullAlias() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.getEntry(null);
    }

    @Test
    public void testGetEntryEmptyAlias() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        Entry entry = keystoreService.getEntry("");
        assertNull(entry);
    }

    @Test
    public void testGetEntry() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());
        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        Entry entry = keystoreService.getEntry(DEFAULT_KEY_ALIAS);
        assertNotNull(entry);
        assertTrue(entry instanceof PrivateKeyEntry);
        PrivateKeyEntry privateKeyEntry = (PrivateKeyEntry) entry;
        assertNotNull(privateKeyEntry.getCertificateChain());
        assertNotNull(privateKeyEntry.getPrivateKey());
    }

    @Test
    public void testGetEntries() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());
        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        Map<String, Entry> entries = keystoreService.getEntries();
        assertNotNull(entries);
        assertFalse(entries.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteEntryNullAlias() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.deleteEntry(null);
    }

    @Test
    public void testDeleteEntryEmptyAlias() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());
        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.deleteEntry("");

        Map<String, Entry> entries = keystoreService.getEntries();
        assertNotNull(entries);
        assertFalse(entries.isEmpty());
    }

    @Test
    public void testDeleteEntry() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.deleteEntry(DEFAULT_KEY_ALIAS);

        Map<String, Entry> entries = keystoreService.getEntries();
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testGetAliases() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        List<String> aliases = keystoreService.getAliases();
        assertNotNull(aliases);
        assertFalse(aliases.isEmpty());
        assertEquals(DEFAULT_KEY_ALIAS, aliases.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetEntryNullAliasEntry() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.setEntry(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetEntryNullEntry() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.setEntry(DEFAULT_KEY_ALIAS + "1", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetEntryNullAlias() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.setEntry(DEFAULT_KEY_ALIAS + "1", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetEntryEmptyAlias() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.setEntry("", null);
    }

    @Test
    public void testSetEntry() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        PrivateKey key = pair.getPrivate();

        InputStream is = new FileInputStream(CERT_FILE_PATH);
        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(is);
        is.close();

        Certificate[] chain = { certificate };

        PrivateKeyEntry privateKeyEntry = new PrivateKeyEntry(key, chain);

        keystoreService.setEntry(DEFAULT_KEY_ALIAS + "1", privateKeyEntry);

        List<String> aliases = keystoreService.getAliases();
        assertNotNull(aliases);
        assertFalse(aliases.isEmpty());
        assertEquals(2, aliases.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetKeyManagersNullAlg() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.getKeyManagers(null);
    }

    @Test
    public void testGetKeyManagersEmptyAlg() throws GeneralSecurityException, IOException, KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        List<KeyManager> keyManagers = keystoreService.getKeyManagers(KeyManagerFactory.getDefaultAlgorithm());
        assertNotNull(keyManagers);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateKeyPairNullAlg() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", null, 1024, "SHA256WithDSA", "CN=Kura, OU=IoT, O=Eclipse, C=US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateKeyPairEmptyAlg() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", "", 1024, "SHA256WithDSA", "CN=Kura, OU=IoT, O=Eclipse, C=US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateKeyPairZeroKeyLength() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", "DSA", 0, "SHA256WithDSA", "CN=Kura, OU=IoT, O=Eclipse, C=US");
    }

    @Test(expected = KuraException.class)
    public void testCreateKeyPairWrongAlg() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", "KSA", 1024, "SHA256WithDSA", "CN=Kura, OU=IoT, O=Eclipse, C=US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateKeyPairEmptyAlias() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("", "DSA", 1024, "SHA256WithDSA", "CN=Kura, OU=IoT, O=Eclipse, C=US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateKeyPairEmptyAttributes() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", "DSA", 1024, "SHA256WithDSA", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateKeyPairEmptySigAlg() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", "DSA", 0, "", "CN=Kura, OU=IoT, O=Eclipse, C=US");
    }

    @Test
    public void testCreateKeyPair()
            throws KuraException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] password = "some password".toCharArray();
        ks.load(null, password);
        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl() {

            @Override
            public KeyStore getKeyStore() throws KuraException {
                return ks;
            }
        };
        keystoreService.setCryptoService(cryptoService);
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", "DSA", 1024, "SHA256WithDSA", "CN=Kura, OU=IoT, O=Eclipse, C=US");

        Entry entry = keystoreService.getEntry("alias");
        assertNotNull(entry);
        assertTrue(entry instanceof PrivateKeyEntry);
        assertNotNull(((PrivateKeyEntry) entry).getPrivateKey());
        assertNotNull(((PrivateKeyEntry) entry).getCertificate().getPublicKey());
        assertEquals("DSA", ((PrivateKeyEntry) entry).getPrivateKey().getAlgorithm());
        assertEquals("DSA", ((PrivateKeyEntry) entry).getCertificate().getPublicKey().getAlgorithm());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCSRNullPrincipal() throws KuraException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.getCSR("alias", null, "alg");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCSRNullSignerAlg() throws KuraException, NoSuchAlgorithmException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        X500Principal principal = new X500Principal("CN=Kura, OU=IoT, O=Eclipse, C=US");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
        keyGen.initialize(1024, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        keystoreService.getCSR(keyPair, principal, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCSREmptyAlg() throws KuraException, NoSuchAlgorithmException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        X500Principal principal = new X500Principal("CN=Kura, OU=IoT, O=Eclipse, C=US");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
        keyGen.initialize(1024, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        keystoreService.getCSR(keyPair, principal, "");
    }

    @Test
    public void testCreateCSRWithKeyPair() throws KuraException, NoSuchAlgorithmException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        X500Principal principal = new X500Principal("CN=Kura, OU=IoT, O=Eclipse, C=US");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        String csr = keystoreService.getCSR(keyPair, principal, "SHA256withRSA");
        assertNotNull(csr);
        assertTrue(csr.startsWith("-----BEGIN CERTIFICATE REQUEST-----"));
    }

    @Test
    public void testCreateCSRWithAlias()
            throws KuraException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] password = "some password".toCharArray();
        ks.load(null, password);
        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl() {

            @Override
            public KeyStore getKeyStore() throws KuraException {
                return ks;
            }
        };
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.activate(componentContext, properties);

        keystoreService.createKeyPair("alias", "RSA", 2048, "SHA256withRSA", "CN=Kura, OU=IoT, O=Eclipse, C=US");
        X500Principal principal = new X500Principal("CN=Kura, OU=IoT, O=Eclipse, C=US");

        String csr = keystoreService.getCSR("alias", principal, "SHA256withRSA");
        assertNotNull(csr);
        assertTrue(csr.startsWith("-----BEGIN CERTIFICATE REQUEST-----"));
    }

    @Test(expected = IOException.class)
    public void testPasswordChange() throws KuraException, GeneralSecurityException, IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(KEY_KEYSTORE_PATH, STORE_PATH);
        properties.put(KEY_KEYSTORE_PASSWORD, STORE_PASS);
        properties.put(KEY_RANDOMIZE_PASSWORD, true);

        Map<String, Object> systemServiceMap = new HashMap<>();
        systemServiceMap.put("kura.https.keyStorePassword", STORE_PASS);
        Properties systemServiceProperties = new Properties();
        systemServiceProperties.putAll(systemServiceMap);

        SystemService systemService = mock(SystemService.class);
        when(systemService.getProperties()).thenReturn(systemServiceProperties);

        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.decryptAes(STORE_PASS.toCharArray())).thenReturn(STORE_PASS.toCharArray());

        ComponentContext componentContext = mock(ComponentContext.class);

        FilesystemKeystoreServiceImpl keystoreService = new FilesystemKeystoreServiceImpl();
        keystoreService.setEventAdmin(mock(EventAdmin.class));
        keystoreService.setCryptoService(cryptoService);
        keystoreService.setSystemService(systemService);
        keystoreService.activate(componentContext, properties);

        try (InputStream tsReadStream = new FileInputStream(STORE_PATH);) {
            this.store.load(tsReadStream, STORE_PASS.toCharArray());
        }

    }

}
