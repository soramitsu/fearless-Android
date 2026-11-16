package com.neovisionaries.ws.client;

import javax.security.auth.x500.X500Principal;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DistinguishedNameParserTest {
    @Test
    public void selectsMostSpecificRepeatedAttributeAndResetsBetweenLookups() {
        DistinguishedNameParser parser = parser("CN=leaf.invalid,OU=Wallet,O=Example,CN=parent.invalid,C=JP");
        assertEquals("leaf.invalid", parser.findMostSpecific("cn"));
        assertEquals("Example", parser.findMostSpecific("O"));
        assertNull(parser.findMostSpecific("DC"));
        assertEquals("leaf.invalid", parser.findMostSpecific("CN"));
        assertEquals("JP", parser.findMostSpecific("c"));
    }

    @Test
    public void decodesEscapedPunctuationQuotesSpacesAndUnicode() {
        assertEquals("a,b+c=word", parser("CN=a\\,b\\+c\\=word,O=Org").findMostSpecific("CN"));
        assertEquals("quoted, name", parser("CN=\"quoted, name\",O=Org").findMostSpecific("CN"));
        assertEquals(" leading ", parser("CN=\\ leading\\ ,O=Org").findMostSpecific("CN"));
        assertEquals("J\u00f6rg", parser("CN=J\\C3\\B6rg,O=Org").findMostSpecific("CN"));
        assertEquals("\u96ea", parser("CN=\\E9\\9B\\AA,O=Org").findMostSpecific("CN"));
        assertEquals("back\\slash", parser("CN=back\\\\slash,O=Org").findMostSpecific("CN"));
    }

    @Test
    public void handlesMultiValuedNamesOpaqueOidsAndEmptySubjects() {
        DistinguishedNameParser multiple = parser("CN=leaf+UID=123,O=Example");
        assertEquals("leaf", multiple.findMostSpecific("CN"));
        assertEquals("123", multiple.findMostSpecific("UID"));
        assertEquals("#0403616263", parser("1.2.3.4=#0403616263,CN=leaf").findMostSpecific("1.2.3.4"));
        assertEquals("", parser("CN=,O=Example").findMostSpecific("CN"));
        assertNull(parser("").findMostSpecific("CN"));
    }

    private static DistinguishedNameParser parser(String name) {
        return new DistinguishedNameParser(new X500Principal(name));
    }
}
