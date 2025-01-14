/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.nsd;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.InetAddresses;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.testutils.ConnectivityModuleTest;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
@ConnectivityModuleTest
public class NsdServiceInfoTest {

    private static final InetAddress IPV4_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.1");
    private static final InetAddress IPV6_ADDRESS = InetAddresses.parseNumericAddress("2001:db8::");
    private static final byte[] PUBLIC_KEY_RDATA = new byte[] {
            (byte) 0x02, (byte)0x01,  // flag
            (byte) 0x03, // protocol
            (byte) 0x0d, // algorithm
            // 64-byte public key below
            (byte) 0xC1, (byte) 0x41, (byte) 0xD0, (byte) 0x63, (byte) 0x79, (byte) 0x60,
            (byte) 0xB9, (byte) 0x8C, (byte) 0xBC, (byte) 0x12, (byte) 0xCF, (byte) 0xCA,
            (byte) 0x22, (byte) 0x1D, (byte) 0x28, (byte) 0x79, (byte) 0xDA, (byte) 0xC2,
            (byte) 0x6E, (byte) 0xE5, (byte) 0xB4, (byte) 0x60, (byte) 0xE9, (byte) 0x00,
            (byte) 0x7C, (byte) 0x99, (byte) 0x2E, (byte) 0x19, (byte) 0x02, (byte) 0xD8,
            (byte) 0x97, (byte) 0xC3, (byte) 0x91, (byte) 0xB0, (byte) 0x37, (byte) 0x64,
            (byte) 0xD4, (byte) 0x48, (byte) 0xF7, (byte) 0xD0, (byte) 0xC7, (byte) 0x72,
            (byte) 0xFD, (byte) 0xB0, (byte) 0x3B, (byte) 0x1D, (byte) 0x9D, (byte) 0x6D,
            (byte) 0x52, (byte) 0xFF, (byte) 0x88, (byte) 0x86, (byte) 0x76, (byte) 0x9E,
            (byte) 0x8E, (byte) 0x23, (byte) 0x62, (byte) 0x51, (byte) 0x35, (byte) 0x65,
            (byte) 0x27, (byte) 0x09, (byte) 0x62, (byte) 0xD3
    };

    @Test
    public void testLimits() throws Exception {
        NsdServiceInfo info = new NsdServiceInfo();

        // Non-ASCII keys.
        boolean exceptionThrown = false;
        try {
            info.setAttribute("猫", "meow");
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        assertEmptyServiceInfo(info);

        // ASCII keys with '=' character.
        exceptionThrown = false;
        try {
            info.setAttribute("kitten=", "meow");
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        assertEmptyServiceInfo(info);

        // Single key + value length too long.
        exceptionThrown = false;
        try {
            String longValue = "loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo"
                    + "oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo"
                    + "oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo"
                    + "ooooooooooooooooooooooooooooong";  // 248 characters.
            info.setAttribute("longcat", longValue);  // Key + value == 255 characters.
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        assertEmptyServiceInfo(info);

        // Total TXT record length too long.
        exceptionThrown = false;
        int recordsAdded = 0;
        try {
            for (int i = 100; i < 300; ++i) {
                // 6 char key + 5 char value + 2 bytes overhead = 13 byte record length.
                String key = String.format("key%d", i);
                info.setAttribute(key, "12345");
                recordsAdded++;
            }
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        assertTrue(100 == recordsAdded);
        assertTrue(info.getTxtRecord().length == 1300);
    }

    @Test
    public void testParcel() throws Exception {
        NsdServiceInfo emptyInfo = new NsdServiceInfo();
        checkParcelable(emptyInfo);

        NsdServiceInfo fullInfo = new NsdServiceInfo();
        fullInfo.setServiceName("kitten");
        fullInfo.setServiceType("_kitten._tcp");
        fullInfo.setSubtypes(Set.of("_thread", "_matter"));
        fullInfo.setPort(4242);
        fullInfo.setHostAddresses(List.of(IPV4_ADDRESS));
        fullInfo.setHostname("home");
        fullInfo.setPublicKey(PUBLIC_KEY_RDATA);
        fullInfo.setNetwork(new Network(123));
        fullInfo.setInterfaceIndex(456);
        checkParcelable(fullInfo);

        NsdServiceInfo noHostInfo = new NsdServiceInfo();
        noHostInfo.setServiceName("kitten");
        noHostInfo.setServiceType("_kitten._tcp");
        noHostInfo.setPort(4242);
        checkParcelable(noHostInfo);

        NsdServiceInfo attributedInfo = new NsdServiceInfo();
        attributedInfo.setServiceName("kitten");
        attributedInfo.setServiceType("_kitten._tcp");
        attributedInfo.setPort(4242);
        attributedInfo.setHostAddresses(List.of(IPV6_ADDRESS, IPV4_ADDRESS));
        attributedInfo.setHostname("home");
        attributedInfo.setPublicKey(PUBLIC_KEY_RDATA);
        attributedInfo.setAttribute("color", "pink");
        attributedInfo.setAttribute("sound", (new String("にゃあ")).getBytes("UTF-8"));
        attributedInfo.setAttribute("adorable", (String) null);
        attributedInfo.setAttribute("sticky", "yes");
        attributedInfo.setAttribute("siblings", new byte[] {});
        attributedInfo.setAttribute("edge cases", new byte[] {0, -1, 127, -128});
        attributedInfo.removeAttribute("sticky");
        checkParcelable(attributedInfo);

        // Sanity check that we actually wrote attributes to attributedInfo.
        assertTrue(attributedInfo.getAttributes().keySet().contains("adorable"));
        String sound = new String(attributedInfo.getAttributes().get("sound"), "UTF-8");
        assertTrue(sound.equals("にゃあ"));
        byte[] edgeCases = attributedInfo.getAttributes().get("edge cases");
        assertTrue(Arrays.equals(edgeCases, new byte[] {0, -1, 127, -128}));
        assertFalse(attributedInfo.getAttributes().keySet().contains("sticky"));
    }

    private static void checkParcelable(NsdServiceInfo original) {
        // Write to parcel.
        Parcel p = Parcel.obtain();
        Bundle writer = new Bundle();
        writer.putParcelable("test_info", original);
        writer.writeToParcel(p, 0);

        // Extract from parcel.
        p.setDataPosition(0);
        Bundle reader = p.readBundle();
        reader.setClassLoader(NsdServiceInfo.class.getClassLoader());
        NsdServiceInfo result = reader.getParcelable("test_info");

        // Assert equality of base fields.
        assertEquals(original.getServiceName(), result.getServiceName());
        assertEquals(original.getServiceType(), result.getServiceType());
        assertEquals(original.getHost(), result.getHost());
        assertEquals(original.getHostname(), result.getHostname());
        assertArrayEquals(original.getPublicKey(), result.getPublicKey());
        assertTrue(original.getPort() == result.getPort());
        assertEquals(original.getNetwork(), result.getNetwork());
        assertEquals(original.getInterfaceIndex(), result.getInterfaceIndex());

        // Assert equality of attribute map.
        Map<String, byte[]> originalMap = original.getAttributes();
        Map<String, byte[]> resultMap = result.getAttributes();
        assertEquals(originalMap.keySet(), resultMap.keySet());
        for (String key : originalMap.keySet()) {
            assertTrue(Arrays.equals(originalMap.get(key), resultMap.get(key)));
        }
    }

    private static void assertEmptyServiceInfo(NsdServiceInfo shouldBeEmpty) {
        byte[] txtRecord = shouldBeEmpty.getTxtRecord();
        if (txtRecord == null || txtRecord.length == 0) {
            return;
        }
        fail("NsdServiceInfo.getTxtRecord did not return null but " + Arrays.toString(txtRecord));
    }

    @Test
    public void testSubtypesValidSubtypesSuccess() {
        NsdServiceInfo info = new NsdServiceInfo();

        info.setSubtypes(Set.of("_thread", "_matter"));

        assertEquals(Set.of("_thread", "_matter"), info.getSubtypes());
    }
}
